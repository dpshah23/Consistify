package com.example.consistify_app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GamificationDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "consistify_gamification.db";
    private static final int DATABASE_VERSION = 1;

    // Daily Stats Table
    public static final String TABLE_DAILY_STATS = "daily_stats";
    public static final String COLUMN_DATE = "date"; // Primary Key (YYYY-MM-DD)
    public static final String COLUMN_SQUATS = "squats";
    public static final String COLUMN_PUSHUPS = "pushups";
    public static final String COLUMN_STEPS = "steps";
    public static final String COLUMN_XP = "xp";
    public static final String COLUMN_FITCOINS = "fitcoins";
    
    // User profile Table (1 row)
    public static final String TABLE_USER_PROFILE = "user_profile";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TOTAL_XP = "total_xp";
    public static final String COLUMN_TOTAL_FITCOINS = "total_fitcoins";
    public static final String COLUMN_CURRENT_STREAK = "current_streak";
    public static final String COLUMN_LAST_ACTIVE_DATE = "last_active_date";

    public GamificationDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createDailyTable = "CREATE TABLE " + TABLE_DAILY_STATS + " (" +
                COLUMN_DATE + " TEXT PRIMARY KEY, " +
                COLUMN_SQUATS + " INTEGER DEFAULT 0, " +
                COLUMN_PUSHUPS + " INTEGER DEFAULT 0, " +
                COLUMN_STEPS + " INTEGER DEFAULT 0, " +
                COLUMN_XP + " INTEGER DEFAULT 0, " +
                COLUMN_FITCOINS + " INTEGER DEFAULT 0)";
        db.execSQL(createDailyTable);

        String createUserTable = "CREATE TABLE " + TABLE_USER_PROFILE + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY, " +
                COLUMN_TOTAL_XP + " INTEGER DEFAULT 0, " +
                COLUMN_TOTAL_FITCOINS + " INTEGER DEFAULT 0, " +
                COLUMN_CURRENT_STREAK + " INTEGER DEFAULT 0, " +
                COLUMN_LAST_ACTIVE_DATE + " TEXT)";
        db.execSQL(createUserTable);
        
        // Ensure 1 row
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, 1);
        db.insert(TABLE_USER_PROFILE, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DAILY_STATS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USER_PROFILE);
        onCreate(db);
    }

    public static String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
