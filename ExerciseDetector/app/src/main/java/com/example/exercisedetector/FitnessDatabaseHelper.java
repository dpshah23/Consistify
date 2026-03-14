package com.example.exercisedetector;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class FitnessDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "exercise_detector.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_FITNESS = "fitness_sessions";

    private static final String COL_ID = "_id";
    private static final String COL_REMOTE_ID = "remote_id";
    private static final String COL_STARTED_AT_MS = "started_at_ms";
    private static final String COL_ENDED_AT_MS = "ended_at_ms";
    private static final String COL_TOTAL_STEPS = "total_steps";
    private static final String COL_DISTANCE_METERS = "distance_meters";
    private static final String COL_AVG_SPEED_KMH = "avg_speed_kmh";
    private static final String COL_AVG_HEART_RATE = "avg_heart_rate";
    private static final String COL_CALORIES_BURNED = "calories_burned";
    private static final String COL_DURATION_SECONDS = "duration_seconds";
    private static final String COL_ACTIVITY_TYPE = "activity_type";
    private static final String COL_IS_ACTIVE = "is_active";
    private static final String COL_SYNC_STATE = "sync_state";
    private static final String COL_LAST_MODIFIED_MS = "last_modified_ms";

    public FitnessDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_FITNESS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_REMOTE_ID + " INTEGER, "
                + COL_STARTED_AT_MS + " INTEGER NOT NULL, "
                + COL_ENDED_AT_MS + " INTEGER, "
                + COL_TOTAL_STEPS + " INTEGER NOT NULL DEFAULT 0, "
                + COL_DISTANCE_METERS + " REAL NOT NULL DEFAULT 0, "
                + COL_AVG_SPEED_KMH + " REAL NOT NULL DEFAULT 0, "
                + COL_AVG_HEART_RATE + " INTEGER NOT NULL DEFAULT 0, "
                + COL_CALORIES_BURNED + " INTEGER NOT NULL DEFAULT 0, "
                + COL_DURATION_SECONDS + " INTEGER NOT NULL DEFAULT 0, "
                + COL_ACTIVITY_TYPE + " TEXT NOT NULL DEFAULT 'UNKNOWN', "
                + COL_IS_ACTIVE + " INTEGER NOT NULL DEFAULT 1, "
                + COL_SYNC_STATE + " TEXT NOT NULL DEFAULT 'PENDING', "
                + COL_LAST_MODIFIED_MS + " INTEGER NOT NULL"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FITNESS);
        onCreate(db);
    }

    public synchronized long createOrGetActiveSession(long startedAtMs) {
        FitnessSessionLocalRecord existing = getActiveSession();
        if (existing != null) {
            return existing.localId;
        }

        ContentValues values = new ContentValues();
        values.put(COL_STARTED_AT_MS, startedAtMs);
        values.put(COL_IS_ACTIVE, 1);
        values.put(COL_SYNC_STATE, FitnessSessionLocalRecord.SYNC_PENDING);
        values.put(COL_LAST_MODIFIED_MS, startedAtMs);
        return getWritableDatabase().insert(TABLE_FITNESS, null, values);
    }

    public synchronized FitnessSessionLocalRecord getActiveSession() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_FITNESS,
                null,
                COL_IS_ACTIVE + "=?",
                new String[]{"1"},
                null,
                null,
                COL_STARTED_AT_MS + " DESC",
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return readRecord(cursor);
            }
        }
        return null;
    }

    public synchronized void updateSession(long localId,
                                           StepDetectionEngine.StepSnapshot snapshot,
                                           int durationSeconds,
                                           long nowMs) {
        ContentValues values = snapshotValues(snapshot, durationSeconds, nowMs);
        values.put(COL_IS_ACTIVE, 1);
        values.put(COL_SYNC_STATE, FitnessSessionLocalRecord.SYNC_PENDING);
        getWritableDatabase().update(
                TABLE_FITNESS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localId)}
        );
    }

    public synchronized void finishSession(long localId,
                                           StepDetectionEngine.StepSnapshot snapshot,
                                           int durationSeconds,
                                           long endedAtMs) {
        ContentValues values = snapshotValues(snapshot, durationSeconds, endedAtMs);
        values.put(COL_ENDED_AT_MS, endedAtMs);
        values.put(COL_IS_ACTIVE, 0);
        values.put(COL_SYNC_STATE, FitnessSessionLocalRecord.SYNC_PENDING);
        getWritableDatabase().update(
                TABLE_FITNESS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localId)}
        );
    }

    public synchronized List<FitnessSessionLocalRecord> getPendingSessions() {
        return queryRecords(COL_SYNC_STATE + "!=?", new String[]{FitnessSessionLocalRecord.SYNCED});
    }

    public synchronized List<FitnessSessionLocalRecord> getRecentSessions(int limit) {
        List<FitnessSessionLocalRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_FITNESS,
                null,
                null,
                null,
                null,
                null,
                COL_STARTED_AT_MS + " DESC",
                String.valueOf(limit)
        )) {
            while (cursor.moveToNext()) {
                records.add(readRecord(cursor));
            }
        }
        return records;
    }

    public synchronized DashboardSummary getDashboardSummary() {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(" + COL_TOTAL_STEPS + "), 0), "
                        + "COALESCE(SUM(" + COL_DISTANCE_METERS + "), 0), "
                        + "COALESCE(SUM(" + COL_CALORIES_BURNED + "), 0), "
                        + "COALESCE(MAX(" + COL_TOTAL_STEPS + "), 0) "
                        + "FROM " + TABLE_FITNESS,
                null
        )) {
            if (cursor.moveToFirst()) {
                return new DashboardSummary(
                        cursor.getInt(0),
                        cursor.getInt(1),
                        cursor.getFloat(2),
                        cursor.getInt(3),
                        cursor.getInt(4)
                );
            }
        }
        return new DashboardSummary(0, 0, 0f, 0, 0);
    }

    public synchronized void updateRemoteId(long localId, long remoteId) {
        ContentValues values = new ContentValues();
        values.put(COL_REMOTE_ID, remoteId);
        getWritableDatabase().update(
                TABLE_FITNESS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localId)}
        );
    }

    public synchronized void markSynced(long localId) {
        ContentValues values = new ContentValues();
        values.put(COL_SYNC_STATE, FitnessSessionLocalRecord.SYNCED);
        values.put(COL_LAST_MODIFIED_MS, System.currentTimeMillis());
        getWritableDatabase().update(
                TABLE_FITNESS,
                values,
                COL_ID + "=?",
                new String[]{String.valueOf(localId)}
        );
    }

    private List<FitnessSessionLocalRecord> queryRecords(String selection, String[] selectionArgs) {
        List<FitnessSessionLocalRecord> records = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_FITNESS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                COL_LAST_MODIFIED_MS + " ASC"
        )) {
            while (cursor.moveToNext()) {
                records.add(readRecord(cursor));
            }
        }
        return records;
    }

    private static ContentValues snapshotValues(StepDetectionEngine.StepSnapshot snapshot,
                                                int durationSeconds,
                                                long nowMs) {
        ContentValues values = new ContentValues();
        values.put(COL_TOTAL_STEPS, snapshot.steps);
        values.put(COL_DISTANCE_METERS, snapshot.distanceM);
        values.put(COL_AVG_SPEED_KMH, snapshot.speedKmh);
        values.put(COL_AVG_HEART_RATE, Math.round(snapshot.heartRateBpm));
        values.put(COL_CALORIES_BURNED, Math.round(snapshot.calories));
        values.put(COL_DURATION_SECONDS, durationSeconds);
        values.put(COL_ACTIVITY_TYPE, snapshot.activityType);
        values.put(COL_LAST_MODIFIED_MS, nowMs);
        return values;
    }

    private static FitnessSessionLocalRecord readRecord(Cursor cursor) {
        long remoteIdValue = cursor.isNull(cursor.getColumnIndexOrThrow(COL_REMOTE_ID))
                ? -1L
                : cursor.getLong(cursor.getColumnIndexOrThrow(COL_REMOTE_ID));
        long endedAtValue = cursor.isNull(cursor.getColumnIndexOrThrow(COL_ENDED_AT_MS))
                ? -1L
                : cursor.getLong(cursor.getColumnIndexOrThrow(COL_ENDED_AT_MS));

        return new FitnessSessionLocalRecord(
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                remoteIdValue > 0 ? remoteIdValue : null,
                cursor.getLong(cursor.getColumnIndexOrThrow(COL_STARTED_AT_MS)),
                endedAtValue > 0 ? endedAtValue : null,
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_TOTAL_STEPS)),
                cursor.getFloat(cursor.getColumnIndexOrThrow(COL_DISTANCE_METERS)),
                cursor.getFloat(cursor.getColumnIndexOrThrow(COL_AVG_SPEED_KMH)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_AVG_HEART_RATE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_CALORIES_BURNED)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_DURATION_SECONDS)),
                cursor.getString(cursor.getColumnIndexOrThrow(COL_ACTIVITY_TYPE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_ACTIVE)) == 1,
                cursor.getString(cursor.getColumnIndexOrThrow(COL_SYNC_STATE))
        );
    }

    public static final class DashboardSummary {
        public final int sessionCount;
        public final int totalSteps;
        public final float totalDistanceMeters;
        public final int totalCalories;
        public final int bestSessionSteps;

        private DashboardSummary(int sessionCount,
                                 int totalSteps,
                                 float totalDistanceMeters,
                                 int totalCalories,
                                 int bestSessionSteps) {
            this.sessionCount = sessionCount;
            this.totalSteps = totalSteps;
            this.totalDistanceMeters = totalDistanceMeters;
            this.totalCalories = totalCalories;
            this.bestSessionSteps = bestSessionSteps;
        }
    }
}