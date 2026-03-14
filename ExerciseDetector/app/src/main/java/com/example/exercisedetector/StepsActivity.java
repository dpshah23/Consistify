package com.example.exercisedetector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StepsActivity extends AppCompatActivity implements StepDetectionEngine.Listener {

    private static final String TAG              = "StepsActivity";
    private static final int    SYNC_INTERVAL_MS = 30_000;  // periodic backend sync period
    private static final int    SYNC_STEP_EVERY  = 15;      // also sync every N valid steps

    // ── UI references ──────────────────────────────────────────────────────
    private TextView activityBadgeText;
    private TextView stepCountText;
    private TextView distanceText;
    private TextView speedText;
    private TextView paceText;
    private TextView heartRateText;
    private TextView caloriesText;
    private TextView statusText;
    private Button   startStopButton;

    // ── Sensors ────────────────────────────────────────────────────────────
    private SensorManager       sensorManager;
    private StepDetectionEngine engine;

    // ── Session state ──────────────────────────────────────────────────────
    private boolean                        tracking         = false;
    private Long                           fitnessSessionId = null;
    private long                           sessionStartMs;
    private int                            lastSyncSteps    = 0;
    private StepDetectionEngine.StepSnapshot latestSnapshot = null;

    // ── Periodic backend sync ──────────────────────────────────────────────
    private final Handler  syncHandler  = new Handler(Looper.getMainLooper());
    private final Runnable syncRunnable = new Runnable() {
        @Override public void run() {
            syncToBackend();
            syncHandler.postDelayed(this, SYNC_INTERVAL_MS);
        }
    };

    // ── Permission launcher ────────────────────────────────────────────────
    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult);

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_steps);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.stepsRoot), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        activityBadgeText = findViewById(R.id.activityBadgeText);
        stepCountText     = findViewById(R.id.stepCountText);
        distanceText      = findViewById(R.id.distanceText);
        speedText         = findViewById(R.id.speedText);
        paceText          = findViewById(R.id.paceText);
        heartRateText     = findViewById(R.id.heartRateText);
        caloriesText      = findViewById(R.id.caloriesText);
        statusText        = findViewById(R.id.statusText);
        startStopButton   = findViewById(R.id.startStopButton);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        engine = new StepDetectionEngine(this);

        startStopButton.setOnClickListener(v -> {
            if (tracking) stopTracking();
            else          checkPermissionsAndStart();
        });

        renderIdleState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tracking) {
            engine.unregister(sensorManager);
            syncHandler.removeCallbacks(syncRunnable);
            finalizeSession();
        }
    }

    // ── Permission flow ────────────────────────────────────────────────────

    private void checkPermissionsAndStart() {
        List<String> needed = new ArrayList<>();

        // ACTIVITY_RECOGNITION is required on Android 10+ for step sensor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                        != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        // BODY_SENSORS is required for heart rate (gracefully degraded if denied)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BODY_SENSORS);
        }

        if (needed.isEmpty()) {
            startTracking();
        } else {
            permissionsLauncher.launch(needed.toArray(new String[0]));
        }
    }

    private void onPermissionsResult(Map<String, Boolean> results) {
        Boolean actRec = results.get(Manifest.permission.ACTIVITY_RECOGNITION);
        // On API 29+ the step detector requires ACTIVITY_RECOGNITION; deny = can't track
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Boolean.FALSE.equals(actRec)) {
            Toast.makeText(this,
                    "Activity recognition permission is required for step counting.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        // BODY_SENSORS denial just means heart rate stays at "--"; tracking still works
        startTracking();
    }

    // ── Tracking lifecycle ─────────────────────────────────────────────────

    private void startTracking() {
        tracking      = true;
        sessionStartMs = SystemClock.elapsedRealtime();
        lastSyncSteps  = 0;
        latestSnapshot = null;

        engine.reset();
        engine.register(sensorManager);

        startStopButton.setText(R.string.steps_stop);
        statusText.setText(R.string.steps_status_tracking);
        activityBadgeText.setText("STILL");

        startFitnessSession();
    }

    private void stopTracking() {
        tracking = false;
        engine.unregister(sensorManager);
        syncHandler.removeCallbacks(syncRunnable);
        finalizeSession();

        startStopButton.setText(R.string.steps_start);
        statusText.setText(R.string.steps_status_finished);
    }

    // ── StepDetectionEngine.Listener ──────────────────────────────────────

    @Override
    public void onUpdate(StepDetectionEngine.StepSnapshot snapshot) {
        latestSnapshot = snapshot;
        runOnUiThread(() -> renderSnapshot(snapshot));

        // Milestone sync: every SYNC_STEP_EVERY valid steps
        if (snapshot.steps - lastSyncSteps >= SYNC_STEP_EVERY) {
            lastSyncSteps = snapshot.steps;
            syncToBackend();
        }
    }

    // ── UI rendering ───────────────────────────────────────────────────────

    private void renderSnapshot(StepDetectionEngine.StepSnapshot s) {
        activityBadgeText.setText(s.activityType);
        stepCountText.setText(formatSteps(s.steps));

        if (s.distanceM >= 1000f) {
            distanceText.setText(String.format(Locale.US, "%.2f km", s.distanceM / 1000f));
        } else {
            distanceText.setText(String.format(Locale.US, "%.0f m", s.distanceM));
        }

        speedText.setText(String.format(Locale.US, "%.1f km/h", s.speedKmh));
        paceText.setText(String.format(Locale.US, "%.0f /min", s.cadenceSpm));

        if (s.heartRateBpm > 0f) {
            heartRateText.setText(String.format(Locale.US, "%.0f BPM", s.heartRateBpm));
        } else {
            heartRateText.setText("-- BPM");
        }

        caloriesText.setText(String.format(Locale.US, "%.0f kcal", s.calories));

        switch (s.activityType) {
            case "RUNNING": statusText.setText(R.string.steps_tip_running); break;
            case "WALKING": statusText.setText(R.string.steps_tip_walking); break;
            default:        statusText.setText(R.string.steps_tip_still);   break;
        }
    }

    private void renderIdleState() {
        activityBadgeText.setText("STILL");
        stepCountText.setText("0");
        distanceText.setText("0 m");
        speedText.setText("0.0 km/h");
        paceText.setText("0 /min");
        heartRateText.setText("-- BPM");
        caloriesText.setText("0 kcal");
        statusText.setText(R.string.steps_status_idle);
        startStopButton.setText(R.string.steps_start);
    }

    private static String formatSteps(int steps) {
        return String.format(Locale.US, "%,d", steps);
    }

    // ── Backend: session start ─────────────────────────────────────────────

    private void startFitnessSession() {
        ExerciseApiService api = ApiClient.getInstance().create(ExerciseApiService.class);
        api.startFitnessSession(new FitnessSessionStartRequest())
                .enqueue(new Callback<SessionResponse>() {
                    @Override
                    public void onResponse(Call<SessionResponse> call,
                                           Response<SessionResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            fitnessSessionId = (long) response.body().id;
                            // Begin periodic sync only after session is created
                            syncHandler.postDelayed(syncRunnable, SYNC_INTERVAL_MS);
                        }
                    }
                    @Override
                    public void onFailure(Call<SessionResponse> call, Throwable t) {
                        // Non-fatal: step tracking continues without backend
                    }
                });
    }

    // ── Backend: incremental sync ──────────────────────────────────────────

    private void syncToBackend() {
        if (fitnessSessionId == null || latestSnapshot == null) return;
        int duration = (int) ((SystemClock.elapsedRealtime() - sessionStartMs) / 1000);

        ExerciseApiService api = ApiClient.getInstance().create(ExerciseApiService.class);
        api.updateFitnessSession(fitnessSessionId, buildUpdateRequest(duration))
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                    @Override public void onFailure(Call<Void> c, Throwable t)      {}
                });
    }

    // ── Backend: finalize ──────────────────────────────────────────────────

    private void finalizeSession() {
        if (fitnessSessionId == null || latestSnapshot == null) return;
        long sessionId = fitnessSessionId;
        fitnessSessionId = null;
        int duration = (int) ((SystemClock.elapsedRealtime() - sessionStartMs) / 1000);

        ExerciseApiService api = ApiClient.getInstance().create(ExerciseApiService.class);
        api.updateFitnessSession(sessionId, buildUpdateRequest(duration))
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> c, Response<Void> r) {}
                    @Override public void onFailure(Call<Void> c, Throwable t)      {}
                });
    }

    private FitnessSessionUpdateRequest buildUpdateRequest(int durationSeconds) {
        StepDetectionEngine.StepSnapshot s = latestSnapshot;
        return new FitnessSessionUpdateRequest(
                s.steps,
                s.distanceM,
                s.speedKmh,
                (int) s.heartRateBpm,
                (int) s.calories,
                durationSeconds,
                s.activityType
        );
    }
}
