package com.example.consistify_app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class StepTrackerService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "step_tracker_channel";
    private static final int NOTIFICATION_ID = 5510;

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private StepDetectionEngine stepEngine;
    private GamificationManager gamificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        gamificationManager = new GamificationManager(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if (stepSensor == null) {
                stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
                stepEngine = new StepDetectionEngine(snapshot -> {
                    if (snapshot.steps > 0) {
                        gamificationManager.addSteps(snapshot.steps);
                        stepEngine.reset();
                        updateNotification();
                        sendBroadcastUpdate(snapshot.steps, snapshot.distanceM, snapshot.calories, snapshot.speedKmh);
                    }
                });
            }
        }
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        
        if (sensorManager != null && stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
        
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            gamificationManager.addSteps(1);
            updateNotification();
            // Approximations for basic detector hardware fallback since we lack full StepEngine data
            float approxDistanceM = gamificationManager.getDailySteps() * 0.72f;
            float approxCalories = gamificationManager.getDailySteps() * 0.040f; 
            sendBroadcastUpdate(gamificationManager.getDailySteps(), approxDistanceM, approxCalories, 4.0f);
        } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && stepEngine != null) {
            stepEngine.onSensorChanged(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private void sendBroadcastUpdate(int newSteps, float distance, float cals, float speed) {
        Intent intent = new Intent("STEPS_UPDATED");
        intent.putExtra("TOTAL_STEPS", gamificationManager.getDailySteps());
        intent.putExtra("DISTANCE", distance);
        intent.putExtra("CALORIES", cals);
        intent.putExtra("SPEED", speed);
        sendBroadcast(intent);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 10, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int currentSteps = gamificationManager.getDailySteps();

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Consistify Steps Tracking")
                .setContentText("Steps walked today: " + currentSteps)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Step Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
