package com.example.consistify_app;

public final class WorkoutRepLocalRecord {

    public static final String SYNC_PENDING = "PENDING";
    public static final String SYNCED = "SYNCED";

    public final long localId;
    public final long sessionLocalId;
    public final String exerciseType;
    public final int repNumber;
    public final long recordedAtMs;
    public final String syncState;

    public WorkoutRepLocalRecord(
            long localId,
            long sessionLocalId,
            String exerciseType,
            int repNumber,
            long recordedAtMs,
            String syncState
    ) {
        this.localId = localId;
        this.sessionLocalId = sessionLocalId;
        this.exerciseType = exerciseType;
        this.repNumber = repNumber;
        this.recordedAtMs = recordedAtMs;
        this.syncState = syncState;
    }
}