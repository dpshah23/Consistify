package com.example.exercisedetector;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

import java.util.Arrays;

public final class StepDetectionEngine implements SensorEventListener {

    private static final long MIN_INTERVAL_MS = 280L;
    private static final long MAX_INTERVAL_MS = 3_000L;
    private static final long VIBRATION_BAND_MS = 25L;
    private static final long DEFAULT_INTERVAL_MS = 520L;

    private static final float MIN_CADENCE_SPM = 30f;
    private static final float MIN_HARDWARE_CADENCE_SPM = 30f;
    private static final float MAX_CADENCE_SPM = 240f;
    private static final float WALKING_SPM = 60f;
    private static final float RUNNING_SPM = 140f;

    private static final float WALK_STEP_M = 0.72f;
    private static final float RUN_STEP_M = 0.95f;
    private static final float CAL_WALK = 0.040f;
    private static final float CAL_RUN = 0.095f;

    private static final int WINDOW = 12;
    private static final float ACCEL_THRESHOLD = 1.8f;
    private static final float ACCEL_ALPHA = 0.18f;
    private static final float MOTION_ALPHA = 0.22f;
    private static final float HARDWARE_MOTION_THRESHOLD = 0.16f;

    private final long[] timestamps = new long[WINDOW];
    private int head;
    private int filled;

    private boolean usingHardware;
    private float smoothedAcceleration;
    private float smoothedMotion;
    private boolean aboveThreshold;
    private boolean hasLinearAcceleration;
    private long lastLinearStepMs;
    private float lastStepCounterValue = Float.NaN;
    private long lastStepCounterEventMs;

    private int steps;
    private float distanceM;
    private float calories;
    private float heartRateBpm;
    private String activityType = "STILL";

    public interface Listener {
        void onUpdate(StepSnapshot snapshot);
    }

    private final Listener listener;

    public StepDetectionEngine(Listener listener) {
        this.listener = listener;
    }

    public void register(SensorManager sensorManager) {
        Sensor stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            usingHardware = true;
        } else {
            Sensor stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if (stepDetector != null) {
                sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_FASTEST);
                usingHardware = true;
            } else {
                usingHardware = false;
            }
        }

        Sensor linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        hasLinearAcceleration = linearAcceleration != null;
        if (linearAcceleration != null) {
            sensorManager.registerListener(this, linearAcceleration, SensorManager.SENSOR_DELAY_GAME);
        }

        Sensor heartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRate != null) {
            sensorManager.registerListener(this, heartRate, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    public void unregister(SensorManager sensorManager) {
        sensorManager.unregisterListener(this);
    }

    public void seedFromSnapshot(StepSnapshot snapshot) {
        steps = snapshot.steps;
        distanceM = snapshot.distanceM;
        calories = snapshot.calories;
        heartRateBpm = snapshot.heartRateBpm;
        activityType = snapshot.activityType;
        clearCadenceWindow();
        lastStepCounterValue = Float.NaN;
        lastStepCounterEventMs = 0L;
        smoothedAcceleration = 0f;
        smoothedMotion = 0f;
        aboveThreshold = false;
        lastLinearStepMs = 0L;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_STEP_COUNTER:
                handleStepCounter(event.values[0], SystemClock.elapsedRealtime());
                break;
            case Sensor.TYPE_STEP_DETECTOR:
                acceptStep(SystemClock.elapsedRealtime(), true);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                handleLinearAcceleration(event);
                break;
            case Sensor.TYPE_HEART_RATE:
                if (event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_LOW
                        && event.values[0] > 0f) {
                    if (heartRateBpm == 0f) {
                        heartRateBpm = event.values[0];
                    } else {
                        heartRateBpm = heartRateBpm + (event.values[0] - heartRateBpm) * 0.18f;
                    }
                    notifyListener();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void handleStepCounter(float sensorValue, long eventTimeMs) {
        if (Float.isNaN(lastStepCounterValue)) {
            lastStepCounterValue = sensorValue;
            lastStepCounterEventMs = eventTimeMs;
            return;
        }

        int delta = Math.round(sensorValue - lastStepCounterValue);
        if (delta <= 0) {
            lastStepCounterValue = sensorValue;
            lastStepCounterEventMs = eventTimeMs;
            return;
        }

        long intervalMs = estimateIntervalMs();
        if (lastStepCounterEventMs > 0L) {
            long observedGapMs = eventTimeMs - lastStepCounterEventMs;
            if (observedGapMs > 0L) {
                intervalMs = clampInterval(observedGapMs / Math.max(1, delta));
            }
        }
        long syntheticStart = eventTimeMs - intervalMs * (delta - 1L);

        for (int index = 0; index < delta; index++) {
            acceptStep(syntheticStart + intervalMs * index, true);
        }

        lastStepCounterValue = sensorValue;
        lastStepCounterEventMs = eventTimeMs;
    }

    private void handleLinearAcceleration(SensorEvent event) {
        float magnitude = (float) Math.sqrt(
                (double) event.values[0] * event.values[0]
                        + (double) event.values[1] * event.values[1]
                        + (double) event.values[2] * event.values[2]
        );

        smoothedMotion = smoothedMotion + (magnitude - smoothedMotion) * MOTION_ALPHA;

        if (usingHardware) {
            return;
        }

        smoothedAcceleration = smoothedAcceleration
                + (magnitude - smoothedAcceleration) * ACCEL_ALPHA;

        if (!aboveThreshold && smoothedAcceleration > ACCEL_THRESHOLD) {
            aboveThreshold = true;
            return;
        }

        if (aboveThreshold && smoothedAcceleration < ACCEL_THRESHOLD * 0.55f) {
            aboveThreshold = false;
            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs - lastLinearStepMs > MIN_INTERVAL_MS) {
                lastLinearStepMs = nowMs;
                acceptStep(nowMs, false);
            }
        }
    }

    private void acceptStep(long nowMs, boolean trustHardware) {
        if (filled > 0) {
            long previous = timestamps[(head - 1 + WINDOW) % WINDOW];
            long intervalMs = nowMs - previous;

            if (intervalMs > MAX_INTERVAL_MS) {
                clearCadenceWindow();
            } else if (intervalMs < MIN_INTERVAL_MS) {
                return;
            }
        }

        appendTimestamp(nowMs);
        float cadence = cadenceSpm();

        if (trustHardware) {
            if (filled < 2) {
                return;
            }

            if (cadence < MIN_HARDWARE_CADENCE_SPM || cadence > MAX_CADENCE_SPM + 10f) {
                return;
            }

            if (hasLinearAcceleration
                    && filled >= 4
                    && smoothedMotion < HARDWARE_MOTION_THRESHOLD
                    && cadence < 50f) {
                return;
            }

            accumulateStep(cadence > 0f ? cadence : WALKING_SPM);
            return;
        }

        if (filled <= 2) {
            return;
        }
        if (cadence < MIN_CADENCE_SPM || cadence > MAX_CADENCE_SPM) {
            return;
        }
        if (filled >= 5 && isVibrationPattern()) {
            return;
        }
        accumulateStep(cadence);
    }

    private void accumulateStep(float cadence) {
        float stepLength;
        float caloriesPerStep;

        if (cadence >= RUNNING_SPM) {
            activityType = "RUNNING";
            stepLength = RUN_STEP_M + Math.min(0.16f, (cadence - RUNNING_SPM) * 0.002f);
            caloriesPerStep = CAL_RUN;
        } else if (cadence >= WALKING_SPM) {
            activityType = "WALKING";
            stepLength = WALK_STEP_M + Math.min(0.08f, (cadence - WALKING_SPM) * 0.001f);
            caloriesPerStep = CAL_WALK;
        } else {
            activityType = "STILL";
            stepLength = 0.35f;
            caloriesPerStep = 0.02f;
        }

        steps += 1;
        distanceM += stepLength;
        calories += caloriesPerStep;
        notifyListener();
    }

    private long estimateIntervalMs() {
        float cadence = cadenceSpm();
        if (cadence <= 0f) {
            return DEFAULT_INTERVAL_MS;
        }
        return clampInterval(Math.round(60_000f / cadence));
    }

    private static long clampInterval(long intervalMs) {
        return Math.max(MIN_INTERVAL_MS, Math.min(MAX_INTERVAL_MS, intervalMs));
    }

    private void appendTimestamp(long nowMs) {
        timestamps[head] = nowMs;
        head = (head + 1) % WINDOW;
        if (filled < WINDOW) {
            filled += 1;
        }
    }

    private void clearCadenceWindow() {
        Arrays.fill(timestamps, 0L);
        head = 0;
        filled = 0;
    }

    private float cadenceSpm() {
        if (filled < 2) {
            return 0f;
        }

        int count = Math.min(filled, WINDOW);
        int oldest = (head - count + WINDOW) % WINDOW;
        long span = timestamps[(head - 1 + WINDOW) % WINDOW] - timestamps[oldest];
        return span > 0L ? (count - 1) * 60_000f / span : 0f;
    }

    private boolean isVibrationPattern() {
        int count = Math.min(filled, WINDOW);
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (int index = 0; index < count - 1; index++) {
            int current = (head - count + index + WINDOW) % WINDOW;
            int next = (head - count + index + 1 + WINDOW) % WINDOW;
            long interval = timestamps[next] - timestamps[current];
            min = Math.min(min, interval);
            max = Math.max(max, interval);
        }

        return (max - min) < VIBRATION_BAND_MS;
    }

    private void notifyListener() {
        float cadence = cadenceSpm();
        float stepLength = "RUNNING".equals(activityType) ? RUN_STEP_M : WALK_STEP_M;
        float speedKmh = cadence > 0f ? (cadence / 60f) * stepLength * 3.6f : 0f;
        listener.onUpdate(new StepSnapshot(
                steps,
                distanceM,
                speedKmh,
                cadence,
                heartRateBpm,
                calories,
                activityType
        ));
    }

    public void reset() {
        clearCadenceWindow();
        steps = 0;
        distanceM = 0f;
        calories = 0f;
        heartRateBpm = 0f;
        activityType = "STILL";
        smoothedAcceleration = 0f;
        smoothedMotion = 0f;
        aboveThreshold = false;
        lastLinearStepMs = 0L;
        lastStepCounterValue = Float.NaN;
        lastStepCounterEventMs = 0L;
    }

    public static final class StepSnapshot {
        public final int steps;
        public final float distanceM;
        public final float speedKmh;
        public final float cadenceSpm;
        public final float heartRateBpm;
        public final float calories;
        public final String activityType;

        StepSnapshot(int steps,
                     float distanceM,
                     float speedKmh,
                     float cadenceSpm,
                     float heartRateBpm,
                     float calories,
                     String activityType) {
            this.steps = steps;
            this.distanceM = distanceM;
            this.speedKmh = speedKmh;
            this.cadenceSpm = cadenceSpm;
            this.heartRateBpm = heartRateBpm;
            this.calories = calories;
            this.activityType = activityType;
        }
    }
}