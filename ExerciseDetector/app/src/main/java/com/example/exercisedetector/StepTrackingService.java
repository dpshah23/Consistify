package com.example.exercisedetector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public final class StepTrackingService extends Service implements StepDetectionEngine.Listener {

    public static final String ACTION_START = "com.example.exercisedetector.action.START_TRACKING";
    public static final String ACTION_STOP = "com.example.exercisedetector.action.STOP_TRACKING";
    public static final String ACTION_SNAPSHOT = "com.example.exercisedetector.action.SNAPSHOT";

    public static final String EXTRA_TRACKING = "extra_tracking";
    public static final String EXTRA_STEPS = "extra_steps";
    public static final String EXTRA_DISTANCE = "extra_distance";
    public static final String EXTRA_SPEED = "extra_speed";
    public static final String EXTRA_CADENCE = "extra_cadence";
    public static final String EXTRA_HEART_RATE = "extra_heart_rate";
    public static final String EXTRA_CALORIES = "extra_calories";
    public static final String EXTRA_ACTIVITY_TYPE = "extra_activity_type";
    public static final String EXTRA_SYNC_LABEL = "extra_sync_label";

    private static final String CHANNEL_ID = "step_tracking_channel";
    private static final int NOTIFICATION_ID = 4107;
    private static final int SYNC_STEP_INTERVAL = 12;
    private static final long SYNC_TIME_INTERVAL_MS = 30_000L;

    private SensorManager sensorManager;
    private FitnessDatabaseHelper databaseHelper;
    private StepDetectionEngine stepDetectionEngine;

    private long activeLocalSessionId = -1L;
    private long sessionStartWallMs = 0L;
    private int lastSyncSteps = 0;
    private long lastSyncAtMs = 0L;
    private boolean engineRegistered;

    private StepDetectionEngine.StepSnapshot latestSnapshot =
            new StepDetectionEngine.StepSnapshot(0, 0f, 0f, 0f, 0f, 0f, "STILL");

    public static Intent createStartIntent(Context context) {
        return new Intent(context, StepTrackingService.class).setAction(ACTION_START);
    }

    public static Intent createStopIntent(Context context) {
        return new Intent(context, StepTrackingService.class).setAction(ACTION_STOP);
    }

    public static void start(Context context) {
        ContextCompat.startForegroundService(context, createStartIntent(context));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        databaseHelper = new FitnessDatabaseHelper(this);
        stepDetectionEngine = new StepDetectionEngine(this);
        FitnessSyncWorker.schedulePeriodic(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTrackingInternal();
            stopSelf();
            return START_NOT_STICKY;
        }

        startTrackingInternal();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (engineRegistered) {
            stepDetectionEngine.unregister(sensorManager);
            engineRegistered = false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onUpdate(StepDetectionEngine.StepSnapshot snapshot) {
        latestSnapshot = snapshot;
        long nowMs = System.currentTimeMillis();
        int durationSeconds = sessionStartWallMs == 0L
                ? 0
                : (int) ((nowMs - sessionStartWallMs) / 1000L);

        if (activeLocalSessionId <= 0L) {
            sessionStartWallMs = nowMs;
            activeLocalSessionId = databaseHelper.createOrGetActiveSession(nowMs);
        }

        databaseHelper.updateSession(activeLocalSessionId, snapshot, durationSeconds, nowMs);
        updateNotification(buildNotificationText(snapshot));
        broadcastSnapshot(true);

        if (snapshot.steps - lastSyncSteps >= SYNC_STEP_INTERVAL
                || nowMs - lastSyncAtMs >= SYNC_TIME_INTERVAL_MS) {
            lastSyncSteps = snapshot.steps;
            lastSyncAtMs = nowMs;
            FitnessSyncWorker.enqueue(this);
        }
    }

    private void startTrackingInternal() {
        TrackingPreferences.setTrackingActive(this, true);
        FitnessSessionLocalRecord activeSession = databaseHelper.getActiveSession();

        if (activeSession != null) {
            activeLocalSessionId = activeSession.localId;
            sessionStartWallMs = activeSession.startedAtMs;
            latestSnapshot = activeSession.toSnapshot();
            stepDetectionEngine.seedFromSnapshot(latestSnapshot);
        } else {
            long nowMs = System.currentTimeMillis();
            activeLocalSessionId = databaseHelper.createOrGetActiveSession(nowMs);
            sessionStartWallMs = nowMs;
            latestSnapshot = new StepDetectionEngine.StepSnapshot(0, 0f, 0f, 0f, 0f, 0f, "STILL");
            stepDetectionEngine.reset();
        }

        startForeground(NOTIFICATION_ID, buildNotification(buildNotificationText(latestSnapshot)));

        if (!engineRegistered) {
            stepDetectionEngine.register(sensorManager);
            engineRegistered = true;
        }

        broadcastSnapshot(true);
        FitnessSyncWorker.enqueue(this);
    }

    private void stopTrackingInternal() {
        TrackingPreferences.setTrackingActive(this, false);

        if (engineRegistered) {
            stepDetectionEngine.unregister(sensorManager);
            engineRegistered = false;
        }

        long nowMs = System.currentTimeMillis();
        int durationSeconds = sessionStartWallMs == 0L
                ? 0
                : (int) ((nowMs - sessionStartWallMs) / 1000L);

        if (activeLocalSessionId > 0L) {
            databaseHelper.finishSession(activeLocalSessionId, latestSnapshot, durationSeconds, nowMs);
            FitnessSyncWorker.enqueue(this);
        }

        broadcastSnapshot(false);
        stopForeground(STOP_FOREGROUND_REMOVE);
        activeLocalSessionId = -1L;
        sessionStartWallMs = 0L;
        lastSyncSteps = 0;
        lastSyncAtMs = 0L;
    }

    private void broadcastSnapshot(boolean tracking) {
        Intent intent = new Intent(ACTION_SNAPSHOT).setPackage(getPackageName());
        intent.putExtra(EXTRA_TRACKING, tracking);
        intent.putExtra(EXTRA_STEPS, latestSnapshot.steps);
        intent.putExtra(EXTRA_DISTANCE, latestSnapshot.distanceM);
        intent.putExtra(EXTRA_SPEED, latestSnapshot.speedKmh);
        intent.putExtra(EXTRA_CADENCE, latestSnapshot.cadenceSpm);
        intent.putExtra(EXTRA_HEART_RATE, latestSnapshot.heartRateBpm);
        intent.putExtra(EXTRA_CALORIES, latestSnapshot.calories);
        intent.putExtra(EXTRA_ACTIVITY_TYPE, latestSnapshot.activityType);
        intent.putExtra(EXTRA_SYNC_LABEL, currentSyncLabel());
        sendBroadcast(intent);
    }

    private String currentSyncLabel() {
        return NetworkUtils.isInternetAvailable(this)
                ? getString(R.string.sync_online)
                : getString(R.string.sync_offline_queue);
    }

    private Notification buildNotification(String contentText) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                11,
                createStopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notification_tracking_title))
                .setContentText(contentText)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(android.R.drawable.ic_media_pause,
                        getString(R.string.steps_stop), stopPendingIntent)
                .build();
    }

    private void updateNotification(String contentText) {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText));
        }
    }

    private String buildNotificationText(StepDetectionEngine.StepSnapshot snapshot) {
        return snapshot.steps + " " + getString(R.string.notification_steps_suffix)
                + " • " + snapshot.activityType
                + " • " + currentSyncLabel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.notification_channel_description));

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
}