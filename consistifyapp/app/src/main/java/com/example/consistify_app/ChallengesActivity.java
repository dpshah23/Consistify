package com.example.consistify_app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.text.InputType;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChallengesActivity extends AppCompatActivity {

    private LinearLayout container;
    private ProgressBar progressBar;
    private AuthManager authManager;
    private String currentUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenges);

        container = findViewById(R.id.challenges_container);
        progressBar = findViewById(R.id.progress_challenges);
        authManager = new AuthManager(this);

        // Fetch user info for UI rendering
        fetchChallenges();
    }

    private void fetchChallenges() {
        String userId = authManager.getUserId();
        if (userId == null) return;

        progressBar.setVisibility(View.VISIBLE);
        container.removeAllViews();

        // Fetch current username to distinguish challenger/challenged
        ApiClient.getApi().getProfile(userId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> res) {
                if (res.isSuccessful() && res.body() != null) {
                    if (res.body().has("user")) {
                        JsonObject user = res.body().getAsJsonObject("user");
                        if (user.has("username") && !user.get("username").isJsonNull()) {
                            currentUsername = user.get("username").getAsString();
                        }
                    } else if (res.body().has("username") && !res.body().get("username").isJsonNull()) {
                        currentUsername = res.body().get("username").getAsString();
                    }
                }

                // Proceed to fetch challenges
                ApiClient.getApi().getUserChallenges(userId).enqueue(new Callback<JsonObject>() {
                    @Override
                    public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            JsonArray challenges = response.body().getAsJsonArray("challenges");
                            if (challenges.size() == 0) {
                                showEmptyState();
                            } else {
                                for (int i = 0; i < challenges.size(); i++) {
                                    JsonObject c = challenges.get(i).getAsJsonObject();
                                    renderChallenge(c);
                                }
                            }
                        } else {
                            showEmptyState();
                        }
                    }

                    @Override
                    public void onFailure(Call<JsonObject> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(ChallengesActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                        showEmptyState();
                    }
                });
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void renderChallenge(JsonObject c) {
        String id = c.get("id").getAsString();
        String challenger = c.get("challenger").getAsString();
        String challenged = c.get("challenged").getAsString();
        String exercise = c.get("exercise_type").getAsString();
        String status = c.get("status").getAsString();
        int challengerScore = (c.has("challenger_score") && !c.get("challenger_score").isJsonNull()) ? c.get("challenger_score").getAsInt() : 0;
        int challengedScore = (c.has("challenged_score") && !c.get("challenged_score").isJsonNull()) ? c.get("challenged_score").getAsInt() : 0;

        boolean isChallenger = challenger.equals(currentUsername);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 24);
        card.setLayoutParams(params);
        card.setBackgroundResource(R.drawable.bg_input_field);
        card.setPadding(32, 32, 32, 32);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(isChallenger ? "You challenged " + challenged : challenger + " challenged you");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);

        TextView tvDetails = new TextView(this);
        tvDetails.setText("Exercise: " + exercise.toUpperCase() + "\nStatus: " + status.toUpperCase());
        tvDetails.setTextColor(Color.parseColor("#A0AAB2"));
        tvDetails.setTextSize(14f);
        tvDetails.setPadding(0, 8, 0, 8);

        card.addView(tvTitle);
        card.addView(tvDetails);

        if (status.equals("completed")) {
            String winner = c.has("winner") && !c.get("winner").isJsonNull() ? c.get("winner").getAsString() : "Tie";
            TextView tvScores = new TextView(this);
            tvScores.setText(challenger + ": " + challengerScore + " vs " + challenged + ": " + challengedScore + "\nWinner: " + winner);
            tvScores.setTextColor(Color.parseColor("#00E676"));
            tvScores.setTextSize(14f);
            card.addView(tvScores);
        } else if (status.equals("pending")) {
            if (!isChallenger) {
                LinearLayout btnLayout = new LinearLayout(this);
                btnLayout.setOrientation(LinearLayout.HORIZONTAL);
                
                Button btnAccept = new Button(this);
                btnAccept.setText("Accept");
                btnAccept.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676")));
                btnAccept.setTextColor(Color.BLACK);
                btnAccept.setOnClickListener(v -> respondChallenge(id, "accept"));
                
                Button btnDecline = new Button(this);
                btnDecline.setText("Decline");
                btnDecline.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF5252")));
                btnDecline.setTextColor(Color.BLACK);
                btnDecline.setOnClickListener(v -> respondChallenge(id, "decline"));
                
                btnLayout.addView(btnAccept);
                btnLayout.addView(btnDecline);
                card.addView(btnLayout);
            }
        } else if (status.equals("accepted")) {
            // Check if user has already submitted score
            boolean scoreSubmitted = (isChallenger && challengerScore > 0) || (!isChallenger && challengedScore > 0);
            
            if (!scoreSubmitted) {
                Button btnSubmit = new Button(this);
                btnSubmit.setText("Submit 3-Min Blitz Score");
                btnSubmit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#03DAC5")));
                btnSubmit.setTextColor(Color.BLACK);
                btnSubmit.setOnClickListener(v -> showSubmitScoreDialog(id));
                card.addView(btnSubmit);
            } else {
                TextView tvWait = new TextView(this);
                tvWait.setText("Waiting for opponent's score...");
                tvWait.setTextColor(Color.parseColor("#FFCA28"));
                card.addView(tvWait);
            }
        }

        container.addView(card);
    }

    private void respondChallenge(String challengeId, String action) {
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getApi().respondChallenge(challengeId, action).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    fetchChallenges(); // reload
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ChallengesActivity.this, "Error responding", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ChallengesActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSubmitScoreDialog(String challengeId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Submit Your Score");
        builder.setMessage("Enter the number of reps you performed in 3 minutes:");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.WHITE);
        builder.setView(input);

        builder.setPositiveButton("Submit", (dialog, which) -> {
            String scoreStr = input.getText().toString();
            if (!scoreStr.isEmpty()) {
                submitScore(challengeId, Integer.parseInt(scoreStr));
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void submitScore(String challengeId, int score) {
        progressBar.setVisibility(View.VISIBLE);
        String userId = authManager.getUserId();
        ApiClient.getApi().submitChallengeScore(challengeId, userId, score).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ChallengesActivity.this, "Score submitted!", Toast.LENGTH_SHORT).show();
                    fetchChallenges(); // reload
                } else {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ChallengesActivity.this, "Error submitting score", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ChallengesActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmptyState() {
        TextView empty = new TextView(this);
        empty.setText("No challenges yet! Go to Leaderboard to challenge someone.");
        empty.setTextColor(Color.parseColor("#AAAAAA"));
        empty.setTextSize(16f);
        empty.setPadding(32, 32, 32, 32);
        container.addView(empty);
    }
}
