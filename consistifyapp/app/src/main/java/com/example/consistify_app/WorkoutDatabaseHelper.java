package com.example.consistify_app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class WorkoutDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "exercise_detector_workouts.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_SESSIONS = "workout_sessions";
    private static final String TABLE_REPS = "workout_reps";

    private static final String COL_ID = "_id";

    private static final String COL_REMOTE_ID = "remote_id";
    private static final String COL_STARTED_AT_MS = "started_at_ms";
    private static final String COL_ENDED_AT_MS = "ended_at_ms";
    private static final String COL_SQUAT_COUNT = "squat_count";
    private static final String COL_PUSHUP_COUNT = "pushup_count";
    private static final String COL_DURATION_SECONDS = "duration_seconds";
    private static final String COL_IS_ACTIVE = "is_active";
    private static final String COL_SYNC_STATE = "sync_state";
    private static final String COL_LAST_MODIFIED_MS = "last_modified_ms";

    private static final String COL_SESSION_LOCAL_ID = "session_local_id";
    private static final String COL_EXERCISE_TYPE = "exercise_type";
    private static final String COL_REP_NUMBER = "rep_number";
    private static final String COL_RECORDED_AT_MS = "recorded_at_ms";
    private static final String COL_REP_SYNC_STATE = "rep_sync_state";

    public WorkoutDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SESSIONS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_REMOTE_ID + " INTEGER, "
                + COL_STARTED_AT_MS + " INTEGER NOT NULL, "
                + COL_ENDED_AT_MS + " INTEGER, "
                + COL_SQUAT_COUNT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_PUSHUP_COUNT + " INTEGER NOT NULL DEFAULT 0, "
                + COL_DURATION_SECONDS + " INTEGER NOT NULL DEFAULT 0, "
                + COL_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, "
                + COL_SYNC_STATE + " TEXT NOT NULL DEFAULT 'PENDING', "
                + COL_LAST_MODIFIED_MS + " INTEGER NOT NULL"
                + ")");

        db.execSQL("CREATE TABLE " + TABLE_REPS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SESSION_LOCAL_ID + " INTEGER NOT NULL, "
                + COL_EXERCISE_TYPE + " TEXT NOT NULL, "
                + COL_REP_NUMBER + " INTEGER NOT NULL, "
                + COL_RECORDED_AT_MS + " INTEGER NOT NULL, "
                + COL_REP_SYNC_STATE + " TEXT NOT NULL DEFAULT 'PENDING', "
                + "FOREIGN KEY(" + COL_SESSION_LOCAL_ID + ") REFERENCES "
                + TABLE_SESSIONS + "(" + COL_ID + ") ON DELETE CASCADE"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_REPS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        onCreate(db);
    }

    public synchronized long createOrGetActiveSession(long startedAtMs) {
        WorkoutSessionLocalRecord existing = getActiveSession();
        if (existing != null) {
            return existing.localId;
        }

        ContentValues values = new ContentValues();
        values.put(COL_STARTED_AT_MS, startedAtMs);
        values.put(COL_IS_ACTIVE, 1);
        values.put(COL_SYNC_STATE, WorkoutSessionLocalRecord.SYNC_PENDING);
        values.put(COL_LAST_MODIFIED_MS, startedAtMs);
        return getWritableDatabase().insert(TABLE_SESSIONS, null, values);
    }

    public synchronized WorkoutSessionLocalRecord getActiveSession() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SESSIONS,
                null,
                COL_IS_ACTIVE + "=?",
                new String[]{"1"},
                null,
                null,
                COL_STARTED_AT_MS + " DESC",
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return readSession(cursor);
            }
        }
        return null;
    }

    public synchronized void updateSession(long localId,
                                           int squatCount,
                                           int pushupCount,
                                           int durationSeconds,
                                           long nowMs) {
        ContentValues values = new ContentValues();
        values.put(COL_SQUAT_COUNT, squatCount);
        values.put(COL_PUSHUP_COUNT, pushupCount);
        values.put(COL_DURATION_SECONDS, durationSeconds);
        values.put(COL_IS_ACTIVE, 1);
        values.put(COL_SYNC_STATE, WorkoutSessionLocalRecord.SYNC_PENDING);
        values.put(COL_LAST_MODIFIED_MS, nowMs);
        getWritableDatabase().update(
                TABLE_SESSIONS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localId)}
        );
    }

    public synchronized void finishSession(long localId,
                                           int squatCount,
                                           int pushupCount,
                                           int durationSeconds,
                                           long endedAtMs) {
        ContentValues values = new ContentValues();
        values.put(COL_SQUAT_COUNT, squatCount);
        values.put(COL_PUSHUP_COUNT, pushupCount);
        values.put(COL_DURATION_SECONDS, durationSeconds);
        values.put(COL_ENDED_AT_MS, endedAtMs);
        values.put(COL_IS_ACTIVE, 0);
        values.put(COL_SYNC_STATE, WorkoutSessionLocalRecord.SYNC_PENDING);
        values.put(COL_LAST_MODIFIED_MS, endedAtMs);
        getWritableDatabase().update(
                TABLE_SESSIONS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localId)}
        );
    }

    public synchronized void queueRep(long sessionLocalId,
                                      String exerciseType,
                                      int repNumber,
                                      long recordedAtMs) {
        ContentValues values = new ContentValues();
        values.put(COL_SESSION_LOCAL_ID, sessionLocalId);
        values.put(COL_EXERCISE_TYPE, exerciseType);
        values.put(COL_REP_NUMBER, repNumber);
        values.put(COL_RECORDED_AT_MS, recordedAtMs);
        values.put(COL_REP_SYNC_STATE, WorkoutRepLocalRecord.SYNC_PENDING);
        getWritableDatabase().insert(TABLE_REPS, null, values);
    }

    public synchronized List<WorkoutSessionLocalRecord> getPendingSessions() {
        List<WorkoutSessionLocalRecord> sessions = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_SESSIONS,
                null,
                COL_SYNC_STATE + "!=?",
                new String[]{WorkoutSessionLocalRecord.SYNCED},
                null,
                null,
                COL_LAST_MODIFIED_MS + " ASC"
        )) {
            while (cursor.moveToNext()) {
                sessions.add(readSession(cursor));
            }
        }
        return sessions;
    }

    public synchronized List<WorkoutRepLocalRecord> getPendingRepsForSession(long sessionLocalId) {
        List<WorkoutRepLocalRecord> reps = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_REPS,
                null,
                COL_SESSION_LOCAL_ID + "=? AND " + COL_REP_SYNC_STATE + "!=?",
                new String[]{
                        String.valueOf(sessionLocalId),
                        WorkoutRepLocalRecord.SYNCED
                },
                null,
                null,
                COL_RECORDED_AT_MS + " ASC"
        )) {
            while (cursor.moveToNext()) {
                reps.add(readRep(cursor));
            }
        }
        return reps;
    }

    public synchronized void updateRemoteId(long localId, long remoteId) {
        ContentValues values = new ContentValues();
        values.put(COL_REMOTE_ID, remoteId);
        getWritableDatabase().update(
                TABLE_SESSIONS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localId)}
        );
    }

    public synchronized void markRepSynced(long localRepId) {
        ContentValues values = new ContentValues();
        values.put(COL_REP_SYNC_STATE, WorkoutRepLocalRecord.SYNCED);
        getWritableDatabase().update(
                TABLE_REPS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localRepId)}
        );
    }

    public synchronized void markSessionSynced(long localId) {
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATE, WorkoutSessionLocalRecord.SYNCED);
        values.put(COL_LAST_MODIFIED_MS, System.currentTimeMillis());
        getWritableDatabase().update(
                TABLE_SESSIONS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localId)}
        );
    }

    private static WorkoutSessionLocalRecord readSession(Cursor cursor) {
        long remoteIdValue = cursor.isNull(cursor.getColumnIndexOrThrow(COL_REMOTE_ID))
                ? -1L
                : cursor.getLong(cursor.getColumnIndexOrThrow(COL_REMOTE_ID));
        long endedAtValue = cursor.isNull(cursor.getColumnIndexOrThrow(COL_ENDED_AT_MS))
                ? -1L
                : cursor.getLong(cursor.getColumnIndexOrThrow(COL_ENDED_AT_MS));

        return new WorkoutSessionLocalRecord(
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                remoteIdValue > 0 ? remoteIdValue : null,
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_STARTED_AT_MS)),
                endedAtValue > 0 ? endedAtValue : null,
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_SQUAT_COUNT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_PUSHUP_COUNT)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_DURATION_SECONDS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_ACTIVE)) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_STATE))
        );
    }

    private static WorkoutRepLocalRecord readRep(Cursor cursor) {
        return new WorkoutRepLocalRecord(
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_SESSION_LOCAL_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_EXERCISE_TYPE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_REP_NUMBER)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_RECORDED_AT_MS)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_REP_SYNC_STATE))
        );
    }
}