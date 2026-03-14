package com.example.exercisedetector;

public final class FitnessSessionLocalRecord {

    public static final String SYNC_PENDING = "PENDING";
    public static final String SYNCED = "SYNCED";

    public final long localId;
    public final Long remoteId;
    public final long startedAtMs;
    public final Long endedAtMs;
    public final int totalSteps;
    public final float distanceMeters;
    public final float avgSpeedKmh;
    public final int avgHeartRate;
    public final int caloriesBurned;
    public final int durationSeconds;
    public final String activityType;
    public final boolean active;
    public final String syncState;

    public FitnessSessionLocalRecord(
            long localId,
            Long remoteId,
            long startedAtMs,
            Long endedAtMs,
            int totalSteps,
            float distanceMeters,
            float avgSpeedKmh,
            int avgHeartRate,
            int caloriesBurned,
            int durationSeconds,
            String activityType,
            boolean active,
            String syncState
    ) {
        this.localId = localId;
        this.remoteId = remoteId;
        this.startedAtMs = startedAtMs;
        this.endedAtMs = endedAtMs;
        this.totalSteps = totalSteps;
        this.distanceMeters = distanceMeters;
        this.avgSpeedKmh = avgSpeedKmh;
        this.avgHeartRate = avgHeartRate;
        this.caloriesBurned = caloriesBurned;
        this.durationSeconds = durationSeconds;
        this.activityType = activityType;
        this.active = active;
        this.syncState = syncState;
    }

    public StepDetectionEngine.StepSnapshot toSnapshot() {
        return new StepDetectionEngine.StepSnapshot(
                totalSteps,
                distanceMeters,
                avgSpeedKmh,
                0f,
                avgHeartRate,
                caloriesBurned,
                activityType
        );
    }

    public boolean isSynced() {
        return SYNCED.equals(syncState);
    }
}