package com.example.consistify_app;

public final class WorkoutSessionLocalRecord {

    public static final String SYNC_PENDING = "PENDING";
    public static final String SYNCED = "SYNCED";

    public final long localId;
    public final Long remoteId;
    public final long startedAtMs;
    public final Long endedAtMs;
    public final int squatCount;
    public final int pushupCount;
    public final int durationSeconds;
    public final boolean active;
    public final String syncState;

    public WorkoutSessionLocalRecord(
            long localId,
            Long remoteId,
            long startedAtMs,
            Long endedAtMs,
            int squatCount,
            int pushupCount,
            int durationSeconds,
            boolean active,
            String syncState
    ) {
        this.localId = localId;
        this.remoteId = remoteId;
        this.startedAtMs = startedAtMs;
        this.endedAtMs = endedAtMs;
        this.squatCount = squatCount;
        this.pushupCount = pushupCount;
        this.durationSeconds = durationSeconds;
        this.active = active;
        this.syncState = syncState;
    }

    public boolean isSynced() {
        return SYNCED.equals(syncState);
    }
}