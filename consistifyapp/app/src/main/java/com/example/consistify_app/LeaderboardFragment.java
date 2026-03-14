package com.example.consistify_app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LeaderboardFragment extends Fragment {

    private TextView tabDaily, tabWeekly, tabAllTime;
    private LinearLayout leaderboardContainer;
    private ProgressBar progressLoader;
    
    private String currentTimeframe = "daily";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_leaderboard, container, false);

        tabDaily = view.findViewById(R.id.tab_daily);
        tabWeekly = view.findViewById(R.id.tab_weekly);
        tabAllTime = view.findViewById(R.id.tab_all_time);
        leaderboardContainer = view.findViewById(R.id.leaderboard_container);
        progressLoader = view.findViewById(R.id.progress_loader);

        tabDaily.setOnClickListener(v -> switchTab("daily"));
        tabWeekly.setOnClickListener(v -> switchTab("weekly"));
        tabAllTime.setOnClickListener(v -> switchTab("all_time"));

        // Load daily by default
        loadLeaderboard("daily");

        return view;
    }

    private void switchTab(String timeframe) {
        if (currentTimeframe.equals(timeframe)) return;
        currentTimeframe = timeframe;

        // Reset tabs
        tabDaily.setBackground(null);
        tabDaily.setTextColor(Color.parseColor("#A0AAB2"));
        tabDaily.setTypeface(null, Typeface.NORMAL);
        
        tabWeekly.setBackground(null);
        tabWeekly.setTextColor(Color.parseColor("#A0AAB2"));
        tabWeekly.setTypeface(null, Typeface.NORMAL);
        
        tabAllTime.setBackground(null);
        tabAllTime.setTextColor(Color.parseColor("#A0AAB2"));
        tabAllTime.setTypeface(null, Typeface.NORMAL);

        // Highlight selected
        TextView selectedTab = null;
        if (timeframe.equals("daily")) selectedTab = tabDaily;
        else if (timeframe.equals("weekly")) selectedTab = tabWeekly;
        else if (timeframe.equals("all_time")) selectedTab = tabAllTime;

        if (selectedTab != null) {
            selectedTab.setBackgroundResource(R.drawable.bg_input_field);
            selectedTab.setTextColor(Color.parseColor("#FFFFFF"));
            selectedTab.setTypeface(null, Typeface.BOLD);
        }

        loadLeaderboard(timeframe);
    }

    private void loadLeaderboard(String timeframe) {
        leaderboardContainer.removeAllViews();
        progressLoader.setVisibility(View.VISIBLE);

        ApiClient.getApi().getLeaderboard(timeframe).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!isAdded() || getContext() == null) return;
                progressLoader.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    JsonArray leaderboard = response.body().getAsJsonArray("leaderboard");
                    if (leaderboard != null && leaderboard.size() > 0) {
                        for (int i = 0; i < leaderboard.size(); i++) {
                            JsonObject userObj = leaderboard.get(i).getAsJsonObject();
                            String username = userObj.get("username").getAsString();
                            int xp = userObj.get("xp").getAsInt();
                            int rank = userObj.get("rank").getAsInt();
                            
                            addLeaderboardRow(rank, username, xp);
                        }
                    } else {
                        showEmptyState();
                    }
                } else {
                    Toast.makeText(getContext(), "Failed to load leaderboard", Toast.LENGTH_SHORT).show();
                    showEmptyState();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (!isAdded() || getContext() == null) return;
                progressLoader.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Network error", Toast.LENGTH_SHORT).show();
                showEmptyState();
            }
        });
    }

    private void addLeaderboardRow(int rank, String username, int xp) {
        LinearLayout row = new LinearLayout(getContext());
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(32, 32, 32, 32);
        
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getLayoutParams();
        params.setMargins(0, 0, 0, 16);
        row.setLayoutParams(params);
        row.setBackgroundResource(R.drawable.bg_input_field);

        // Rank
        TextView tvRank = new TextView(getContext());
        tvRank.setText("#" + rank);
        tvRank.setTextColor(rank <= 3 ? Color.parseColor("#FFCA28") : Color.parseColor("#FFFFFF"));
        tvRank.setTextSize(18f);
        tvRank.setTypeface(null, Typeface.BOLD);
        tvRank.setPadding(0, 0, 32, 0);

        // Username
        TextView tvUsername = new TextView(getContext());
        tvUsername.setText(username);
        tvUsername.setTextColor(Color.parseColor("#FFFFFF"));
        tvUsername.setTextSize(16f);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvUsername.setLayoutParams(nameParams);

        // XP
        TextView tvXp = new TextView(getContext());
        tvXp.setText(xp + " XP");
        tvXp.setTextColor(Color.parseColor("#00E676"));
        tvXp.setTextSize(16f);
        tvXp.setTypeface(null, Typeface.BOLD);

        row.addView(tvRank);
        row.addView(tvUsername);
        row.addView(tvXp);

        leaderboardContainer.addView(row);
    }
    
    private void showEmptyState() {
        TextView empty = new TextView(getContext());
        empty.setText("No data available for this timeframe.");
        empty.setTextColor(Color.parseColor("#A0AAB2"));
        empty.setTextSize(16f);
        empty.setPadding(32, 32, 32, 32);
        empty.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        leaderboardContainer.addView(empty);
    }
}
