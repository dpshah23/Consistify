package com.example.consistify_app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class GamificationManager {
    private GamificationDatabaseHelper dbHelper;
    private Context mContext;

    public GamificationManager(Context context) {
        this.mContext = context;
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

    public boolean deductFitCoins(int amount) {
        if (getFitCoins() >= amount) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("UPDATE " + GamificationDatabaseHelper.TABLE_USER_PROFILE + 
                    " SET total_fitcoins = total_fitcoins - " + amount + 
                    " WHERE id = 1");
            return true;
        }
        return false;
    }

    public String getCurrentLevel() {
        int xp = getTotalXP();
        if (xp < 100) return "🐢 Tortoise";
        if (xp < 300) return "🐺 Wolf";
        if (xp < 600) return "🦅 Eagle";
        if (xp < 1000) return "🐆 Leopard";
        return "🦁 Lion";
    }

    public int getXPForNextLevel() {
        int xp = getTotalXP();
        if (xp < 100) return 100;
        if (xp < 300) return 300;
        if (xp < 600) return 600;
        if (xp < 1000) return 1000;
        return xp; // Maxed out
    }

    public int getBaseXPForCurrentLevel() {
        int xp = getTotalXP();
        if (xp < 100) return 0;
        if (xp < 300) return 100;
        if (xp < 600) return 300;
        if (xp < 1000) return 600;
        return 1000;
    }

    public int getDailySteps() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String today = GamificationDatabaseHelper.getTodayDateString();
        Cursor c = db.rawQuery("SELECT " + GamificationDatabaseHelper.COLUMN_STEPS + " FROM " + GamificationDatabaseHelper.TABLE_DAILY_STATS + " WHERE date = ?", new String[]{today});
        int steps = 0;
        if (c.moveToFirst()) steps = c.getInt(0);
        c.close();
        return steps;
    }

    public int getDailySquats() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String today = GamificationDatabaseHelper.getTodayDateString();
        Cursor c = db.rawQuery("SELECT " + GamificationDatabaseHelper.COLUMN_SQUATS + " FROM " + GamificationDatabaseHelper.TABLE_DAILY_STATS + " WHERE date = ?", new String[]{today});
        int squats = 0;
        if (c.moveToFirst()) squats = c.getInt(0);
        c.close();
        return squats;
    }

    public int getDailyPushups() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String today = GamificationDatabaseHelper.getTodayDateString();
        Cursor c = db.rawQuery("SELECT " + GamificationDatabaseHelper.COLUMN_PUSHUPS + " FROM " + GamificationDatabaseHelper.TABLE_DAILY_STATS + " WHERE date = ?", new String[]{today});
        int pushups = 0;
        if (c.moveToFirst()) pushups = c.getInt(0);
        c.close();
        return pushups;
    }

    public void resetDailyStats() {
        // Technically this worker runs at midnight, but just in case,
        // rather than deleting, we let ensureDailyRecordExists() spawn a new day entry tomorrow.
        // We can optionally clear the active memory cache here if any exist.
    }

    public int[] getDailyQuestTargets() {
        String level = getCurrentLevel();
        if (level.contains("Tortoise")) return new int[]{10, 5, 2000}; // squats, pushups, steps
        if (level.contains("Wolf")) return new int[]{20, 10, 5000};
        if (level.contains("Eagle")) return new int[]{40, 20, 8000};
        if (level.contains("Leopard")) return new int[]{60, 30, 10000};
        return new int[]{100, 50, 15000}; // Lion
    }

    public boolean isDailyQuestCompleted() {
        int[] targets = getDailyQuestTargets();
        return getDailySquats() >= targets[0] && 
               getDailyPushups() >= targets[1] && 
               getDailySteps() >= targets[2];
    }

    public boolean claimDailyQuestReward() {
        String today = GamificationDatabaseHelper.getTodayDateString();
        android.content.SharedPreferences prefs = mContext.getSharedPreferences("quests", Context.MODE_PRIVATE);
        boolean alreadyClaimed = prefs.getBoolean("quest_claimed_" + today, false);
        
        if (!alreadyClaimed && isDailyQuestCompleted()) {
            // Reward: +50 XP, +20 FitCoins
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("UPDATE " + GamificationDatabaseHelper.TABLE_USER_PROFILE + 
                    " SET total_xp = total_xp + 50, " +
                    "total_fitcoins = total_fitcoins + 20 " + 
                    " WHERE id = 1");
            
            prefs.edit().putBoolean("quest_claimed_" + today, true).apply();
            return true;
        }
        return false;
    }

    public boolean isDailyQuestClaimed() {
        String today = GamificationDatabaseHelper.getTodayDateString();
        android.content.SharedPreferences prefs = mContext.getSharedPreferences("quests", Context.MODE_PRIVATE);
        return prefs.getBoolean("quest_claimed_" + today, false);
    }
}
