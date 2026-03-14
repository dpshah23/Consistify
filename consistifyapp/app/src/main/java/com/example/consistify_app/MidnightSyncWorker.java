package com.example.consistify_app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonObject;
import retrofit2.Response;

public class MidnightSyncWorker extends Worker {

    public MidnightSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            GamificationManager gamificationManager = new GamificationManager(getApplicationContext());
            AuthManager authManager = new AuthManager(getApplicationContext());
            String userId = authManager.getUserId();

            if (userId == null || userId.isEmpty()) {
                return Result.failure();
            }

            int dailySteps = gamificationManager.getDailySteps();
            int dailySquats = gamificationManager.getDailySquats();
            int dailyPushups = gamificationManager.getDailyPushups();

            Response<JsonObject> response = ApiClient.getApi()
                    .syncGamification(userId, dailySquats, dailyPushups, dailySteps)
                    .execute(); // Synchronous call is safe inside Worker

            if (response.isSuccessful() && response.body() != null) {
                // Future consideration: Parse the level up/consistency score
                // and show a massive notification if they leveled up.
                // JsonObject data = response.body();
                // boolean leveledUp = data.has("level_up") && data.get("level_up").getAsBoolean();
                
                gamificationManager.resetDailyStats();
                return Result.success();
            } else {
                return Result.retry();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }
}
