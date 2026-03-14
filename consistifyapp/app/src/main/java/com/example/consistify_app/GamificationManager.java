package com.example.consistify_app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class GamificationManager {
    private GamificationDatabaseHelper dbHelper;

    public GamificationManager(Context context) {
        dbHelper = new GamificationDatabaseHelper(context);
    }
    
    // Core game rules
    // 10 squats = 10 XP, 5 coins -> 1 squat = 1 XP, 0.5 coin
    // 10 pushups = 12 XP, 6 coins -> 1 pushup = 1.2 XP, 0.6 coin
    // 1000 steps = 5 XP, 3 coins -> 1 step = 0.005 XP, 0.003 coin

    private void ensureDailyRecordExists(SQLiteDatabase db, String date) {
        Cursor c = db.query(GamificationDatabaseHelper.TABLE_DAILY_STATS, null, "date=?", new String[]{date}, null, null, null);
        if (!c.moveToFirst()) {
            ContentValues values = new ContentValues();
            values.put(GamificationDatabaseHelper.COLUMN_DATE, date);
            db.insert(GamificationDatabaseHelper.TABLE_DAILY_STATS, null, values);
        }
        c.close();
    }

    public void addSquats(int count) {
        int xpGained = count;
        int coinsGained = count / 2; // 0.5 per squat integer math
        
        addProgress(GamificationDatabaseHelper.COLUMN_SQUATS, count, xpGained, coinsGained);
    }

    public void addPushups(int count) {
        int xpGained = (int) (count * 1.2);
        int coinsGained = (int) (count * 0.6);

        addProgress(GamificationDatabaseHelper.COLUMN_PUSHUPS, count, xpGained, coinsGained);
    }

    public void addSteps(int count) {
        int xpGained = (int) (count * 0.005);
        int coinsGained = (int) (count * 0.003);

        addProgress(GamificationDatabaseHelper.COLUMN_STEPS, count, xpGained, coinsGained);
    }

    private void addProgress(String column, int count, int xpGained, int coinsGained) {
        if (count <= 0) return;
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String today = GamificationDatabaseHelper.getTodayDateString();
        ensureDailyRecordExists(db, today);

        // Update Daily stats
        db.execSQL("UPDATE " + GamificationDatabaseHelper.TABLE_DAILY_STATS + 
                " SET " + column + " = " + column + " + " + count + ", " +
                GamificationDatabaseHelper.COLUMN_XP + " = " + GamificationDatabaseHelper.COLUMN_XP + " + " + xpGained + ", " +
                GamificationDatabaseHelper.COLUMN_FITCOINS + " = " + GamificationDatabaseHelper.COLUMN_FITCOINS + " + " + coinsGained +
                " WHERE date = ?", new String[]{today});
                
        // Cap daily XP logic can go here (max 150 daily limit per requirements).
        
        // Update User profile totals
        db.execSQL("UPDATE " + GamificationDatabaseHelper.TABLE_USER_PROFILE + 
                " SET total_xp = total_xp + " + xpGained + ", " +
                "total_fitcoins = total_fitcoins + " + coinsGained + 
                " WHERE id = 1");

        // Simple Streak update logic: If last_active_date is not today, update it.
        Cursor c = db.rawQuery("SELECT last_active_date FROM " + GamificationDatabaseHelper.TABLE_USER_PROFILE, null);
        if (c.moveToFirst()) {
            String lastActive = c.getString(0);
            if (lastActive == null || !lastActive.equals(today)) {
                // Here we should realistically check if yesterday was last active to increase streak, or reset to 1.
                // For simplicity, we just mark today as active now.
                ContentValues vals = new ContentValues();
                vals.put(GamificationDatabaseHelper.COLUMN_LAST_ACTIVE_DATE, today);
                vals.put(GamificationDatabaseHelper.COLUMN_CURRENT_STREAK, 1); // Mock initial logic
                db.update(GamificationDatabaseHelper.TABLE_USER_PROFILE, vals, "id=1", null);
            }
        }
        c.close();
    }
    
    public int getTotalXP() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT total_xp FROM user_profile WHERE id=1", null);
        int xp = 0;
        if (c.moveToFirst()) xp = c.getInt(0);
        c.close();
        return xp;
    }

    public int getFitCoins() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT total_fitcoins FROM user_profile WHERE id=1", null);
        int coins = 0;
        if (c.moveToFirst()) coins = c.getInt(0);
        c.close();
        return coins;
    }

    public String getCurrentLevel() {
        int xp = getTotalXP();
        if (xp < 100) return "🐢 Tortoise";
        if (xp < 300) return "🐺 Wolf";
        if (xp < 600) return "🦅 Eagle";
        if (xp < 1000) return "🐆 Leopard";
        return "🦁 Lion";
    }
}
