package com.example.consistify_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.google.gson.JsonObject;

public class ProfileFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        
        GamificationManager manager = new GamificationManager(requireContext());
        TextView tvLevel = view.findViewById(R.id.tv_profile_level);
        tvLevel.setText("Level: " + manager.getCurrentLevel());
        
        // Fetch current streak
        GamificationDatabaseHelper dbHelper = new GamificationDatabaseHelper(requireContext());
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT current_streak FROM user_profile WHERE id=1", null);
        int streak = 0;
        if (c.moveToFirst()) {
            streak = c.getInt(0);
        }
        c.close();
        
        TextView tvStreak = view.findViewById(R.id.tv_streak);
        tvStreak.setText(streak + " Days \uD83D\uDD25"); // Fire emoji

        // Fetch dynamic profile
        TextView tvUsername = view.findViewById(R.id.tv_username);
        TextView tvConsistency = view.findViewById(R.id.tv_consistency_score);
        TextView tvRecentAchievements = view.findViewById(R.id.tv_recent_achievements);
        
        AuthManager authManager = new AuthManager(requireContext());
        String userId = authManager.getUserId();
        
        if (userId != null) {
            int dailySquats = manager.getDailySquats();
            int dailyPushups = manager.getDailyPushups();
            int dailySteps = manager.getDailySteps();

            ApiClient.getApi().syncGamification(userId, dailySquats, dailyPushups, dailySteps).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> responseSync) {
                    fetchProfileData(userId, tvUsername, tvLevel, tvStreak, tvConsistency, tvRecentAchievements);
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    fetchProfileData(userId, tvUsername, tvLevel, tvStreak, tvConsistency, tvRecentAchievements);
                }
            });
        }

        return view;
    }

    private void fetchProfileData(String userId, TextView tvUsername, TextView tvLevel, TextView tvStreak, TextView tvConsistency, TextView tvRecentAchievements) {
        // 1. Fetch Username Profiling
        ApiClient.getApi().getProfile(userId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (isAdded() && getContext() != null && response.isSuccessful() && response.body() != null) {
                    if (response.body().has("user")) {
                        JsonObject user = response.body().getAsJsonObject("user");
                        if (user.has("username") && !user.get("username").isJsonNull()) {
                            tvUsername.setText(user.get("username").getAsString());
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {}
        });

        // 2. Fetch Gamification Stats (Streak, Level, Consistency, Achievements)
        ApiClient.getApi().getGamificationStatus(userId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (isAdded() && getContext() != null && response.isSuccessful() && response.body() != null) {
                    JsonObject body = response.body();
                    if (body.has("level") && !body.get("level").isJsonNull()) {
                        tvLevel.setText("Level: " + body.get("level").getAsString());
                    }
                    if (body.has("current_streak") && !body.get("current_streak").isJsonNull()) {
                        tvStreak.setText(body.get("current_streak").getAsInt() + " Days \uD83D\uDD25");
                    }
                    if (body.has("consistency_score") && !body.get("consistency_score").isJsonNull()) {
                        tvConsistency.setText(String.valueOf(body.get("consistency_score").getAsInt()));
                    }
                    if (body.has("recent_achievements") && !body.get("recent_achievements").isJsonNull()) {
                        com.google.gson.JsonArray achievementsJson = body.getAsJsonArray("recent_achievements");
                        if (achievementsJson != null && achievementsJson.size() > 0) {
                            java.util.List<String> achList = new java.util.ArrayList<>();
                            for(int i = 0; i < achievementsJson.size(); i++) {
                                achList.add("• " + achievementsJson.get(i).getAsString());
                            }
                            tvRecentAchievements.setText(android.text.TextUtils.join("\n\n", achList));
                            tvRecentAchievements.setGravity(android.view.Gravity.START);
                        } else {
                            tvRecentAchievements.setText("No achievements unlocked yet. Keep grinding!");
                            tvRecentAchievements.setGravity(android.view.Gravity.CENTER);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {}
        });
    }
}
