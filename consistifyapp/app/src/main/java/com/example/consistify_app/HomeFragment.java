package com.example.consistify_app;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.animation.ObjectAnimator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private LinearLayout postsContainer;
    private TextView tvLoading;
    private AuthManager authManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        authManager = new AuthManager(requireContext());

        GamificationManager manager = new GamificationManager(requireContext());
        TextView tvLevel = view.findViewById(R.id.tv_home_level);
        TextView tvXp = view.findViewById(R.id.tv_home_xp);
        
        tvLevel.setText("Level: " + manager.getCurrentLevel());
        tvXp.setText(manager.getTotalXP() + " XP (Total)");

        ProgressBar pbLevel = view.findViewById(R.id.pb_level);
        TextView tvXpRemaining = view.findViewById(R.id.tv_xp_remaining);
        
        int currentXp = manager.getTotalXP();
        int baseXP = manager.getBaseXPForCurrentLevel();
        int nextXp = manager.getXPForNextLevel();
        
        if (currentXp >= 1000) {
            pbLevel.setMax(100);
            pbLevel.setProgress(100);
            tvXpRemaining.setText("Max Level Reached!");
        } else {
            int xpInCurrentLevel = currentXp - baseXP;
            int xpRequiredForThisLevel = nextXp - baseXP;
            pbLevel.setMax(xpRequiredForThisLevel);
            
            ObjectAnimator animation = ObjectAnimator.ofInt(pbLevel, "progress", 0, xpInCurrentLevel);
            animation.setDuration(1200); // 1.2 seconds smooth animation
            animation.setInterpolator(new DecelerateInterpolator());
            animation.start();
            
            tvXpRemaining.setText((nextXp - currentXp) + " XP to next level");
        }

        postsContainer = view.findViewById(R.id.posts_container);
        tvLoading = view.findViewById(R.id.tv_loading_posts);

        EditText etNewPost = view.findViewById(R.id.et_new_post);
        Button btnPost = view.findViewById(R.id.btn_post);

        btnPost.setOnClickListener(v -> {
            String content = etNewPost.getText().toString().trim();
            if (content.isEmpty()) return;
            
            String userId = authManager.getUserId();
            ApiClient.getApi().createPost(userId, content, "").enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (response.isSuccessful()) {
                        etNewPost.setText("");
                        Toast.makeText(requireContext(), "Posted!", Toast.LENGTH_SHORT).show();
                        loadFeed(); // Reload feed
                    } else {
                        Toast.makeText(requireContext(), "Failed to create post", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    Toast.makeText(requireContext(), "Network Error", Toast.LENGTH_SHORT).show();
                }
            });
        });

        loadFeed();

        return view;
    }

    private void loadFeed() {
        ApiClient.getApi().getFeed(1).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    postsContainer.removeAllViews();
                    if (response.body().has("posts") && response.body().get("posts").isJsonArray()) {
                        JsonArray posts = response.body().getAsJsonArray("posts");
                        
                        // Backend might return ascending vs descending, but we just parse it.
                        // We will build views for each.
                        for (int i = 0; i < posts.size(); i++) {
                            JsonObject postObj = posts.get(i).getAsJsonObject();
                            String author = postObj.has("author_username") ? postObj.get("author_username").getAsString() : "User";
                            String content = postObj.has("content") ? postObj.get("content").getAsString() : "";
                            
                            addPostToFeed(author, content);
                        }
                    } else {
                        tvLoading.setText("No posts found yet. Be the first to share!");
                        postsContainer.addView(tvLoading);
                    }
                } else {
                    renderMockFeed();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                renderMockFeed(); // Render mock feed if network fails so it isn't empty visually
            }
        });
    }

    private void renderMockFeed() {
        postsContainer.removeAllViews();
        addPostToFeed("Beast Master", "Just crushed 100 pushups today for the Wolf rank!");
        addPostToFeed("Iron Liger", "Consistency is key. 5 day streak.");
        addPostToFeed("Alpha Runner", "Finished a 5km sprint. Let's go!");
    }

    private void addPostToFeed(String author, String content) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 24);
        card.setLayoutParams(params);
        card.setBackgroundColor(Color.parseColor("#1E1E1E"));
        card.setPadding(32, 32, 32, 32);

        TextView tvAuthor = new TextView(requireContext());
        tvAuthor.setText(author);
        tvAuthor.setTextColor(Color.parseColor("#03DAC5"));
        tvAuthor.setTextSize(16f);
        tvAuthor.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvContent = new TextView(requireContext());
        tvContent.setText(content);
        tvContent.setTextColor(Color.WHITE);
        tvContent.setTextSize(14f);
        tvContent.setPadding(0, 16, 0, 0);

        card.addView(tvAuthor);
        card.addView(tvContent);

        postsContainer.addView(card, 0); // Add directly at the top
    }
}
