package com.example.exercisedetector;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView activityBadgeText;
    private TextView syncStatusText;
    private TextView stepCountText;
    private TextView statusText;
    private TextView distanceText;
    private TextView speedText;
    private TextView paceText;
    private TextView heartRateText;
    private TextView caloriesText;
    private TextView summaryStepsText;
    private TextView summaryDistanceText;
    private TextView summarySessionsText;
    private TextView bestSessionText;
    private TextView emptyHistoryText;
    private LinearLayout historyContainer;
    private Button startTrackingButton;
    private Button detectorButton;

    private FitnessDatabaseHelper databaseHelper;
    private boolean tracking;
    private boolean receiverRegistered;

    private final BroadcastReceiver snapshotReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            tracking = intent.getBooleanExtra(StepTrackingService.EXTRA_TRACKING, false);

            StepDetectionEngine.StepSnapshot snapshot = new StepDetectionEngine.StepSnapshot(
                    intent.getIntExtra(StepTrackingService.EXTRA_STEPS, 0),
                    intent.getFloatExtra(StepTrackingService.EXTRA_DISTANCE, 0f),
                    intent.getFloatExtra(StepTrackingService.EXTRA_SPEED, 0f),
                    intent.getFloatExtra(StepTrackingService.EXTRA_CADENCE, 0f),
                    intent.getFloatExtra(StepTrackingService.EXTRA_HEART_RATE, 0f),
                    intent.getFloatExtra(StepTrackingService.EXTRA_CALORIES, 0f),
                    intent.getStringExtra(StepTrackingService.EXTRA_ACTIVITY_TYPE) == null
                            ? "STILL"
                            : intent.getStringExtra(StepTrackingService.EXTRA_ACTIVITY_TYPE)
            );

            renderLiveSnapshot(snapshot, tracking);
            syncStatusText.setText(intent.getStringExtra(StepTrackingService.EXTRA_SYNC_LABEL));
            updateTrackingButton();

            if (!tracking) {
                refreshDashboard();
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::onPermissionsResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        databaseHelper = new FitnessDatabaseHelper(this);
        FitnessSyncWorker.schedulePeriodic(this);
    WorkoutSyncWorker.schedulePeriodic(this);

        activityBadgeText = findViewById(R.id.activityBadgeText);
        syncStatusText = findViewById(R.id.syncStatusText);
        stepCountText = findViewById(R.id.stepCountText);
        statusText = findViewById(R.id.statusText);
        distanceText = findViewById(R.id.distanceText);
        speedText = findViewById(R.id.speedText);
        paceText = findViewById(R.id.paceText);
        heartRateText = findViewById(R.id.heartRateText);
        caloriesText = findViewById(R.id.caloriesText);
        summaryStepsText = findViewById(R.id.summaryStepsText);
        summaryDistanceText = findViewById(R.id.summaryDistanceText);
        summarySessionsText = findViewById(R.id.summarySessionsText);
        bestSessionText = findViewById(R.id.bestSessionText);
        emptyHistoryText = findViewById(R.id.emptyHistoryText);
        historyContainer = findViewById(R.id.historyContainer);
        startTrackingButton = findViewById(R.id.startTrackingButton);
        detectorButton = findViewById(R.id.detectorButton);

        startTrackingButton.setOnClickListener(view -> {
            if (tracking) {
                stopTracking();
            } else {
                checkPermissionsAndStartTracking();
            }
        });
        detectorButton.setOnClickListener(view ->
                startActivity(new Intent(MainActivity.this, squat.class)));

        renderIdleLiveCard();
        refreshDashboard();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerSnapshotReceiver();
        refreshDashboard();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterSnapshotReceiver();
    }

    private void checkPermissionsAndStartTracking() {
        List<String> neededPermissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.BODY_SENSORS);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (neededPermissions.isEmpty()) {
            startTracking();
            return;
        }

        permissionsLauncher.launch(neededPermissions.toArray(new String[0]));
    }

    private void onPermissionsResult(Map<String, Boolean> results) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && Boolean.FALSE.equals(results.get(Manifest.permission.ACTIVITY_RECOGNITION))) {
            Toast.makeText(this, R.string.tracking_permission_required, Toast.LENGTH_LONG).show();
            return;
        }

        startTracking();
    }

    private void startTracking() {
        tracking = true;
        StepTrackingService.start(this);
        maybeRequestBatteryOptimizationExemption();
        syncStatusText.setText(currentSyncLabel());
        statusText.setText(R.string.steps_status_tracking);
        updateTrackingButton();
    }

    private void stopTracking() {
        tracking = false;
        startService(StepTrackingService.createStopIntent(this));
        statusText.setText(R.string.steps_status_finished);
        updateTrackingButton();
    }

    private void refreshDashboard() {
        FitnessDatabaseHelper.DashboardSummary summary = databaseHelper.getDashboardSummary();
        renderSummary(summary);
        renderHistory(databaseHelper.getRecentSessions(6));

        FitnessSessionLocalRecord activeSession = databaseHelper.getActiveSession();
        tracking = TrackingPreferences.isTrackingActive(this) || activeSession != null;

        if (activeSession != null) {
            renderLiveSnapshot(activeSession.toSnapshot(), tracking);
            syncStatusText.setText(activeSession.isSynced()
                    ? getString(R.string.sync_online)
                    : currentSyncLabel());
        } else {
            renderIdleLiveCard();
        }

        updateTrackingButton();
    }

    private void renderLiveSnapshot(StepDetectionEngine.StepSnapshot snapshot, boolean trackingActive) {
        activityBadgeText.setText(snapshot.activityType);
        stepCountText.setText(formatSteps(snapshot.steps));
        distanceText.setText(formatDistance(snapshot.distanceM));
        speedText.setText(String.format(Locale.US, "%.1f km/h", snapshot.speedKmh));
        paceText.setText(String.format(Locale.US, "%.0f /min", snapshot.cadenceSpm));
        heartRateText.setText(snapshot.heartRateBpm > 0f
                ? String.format(Locale.US, "%.0f BPM", snapshot.heartRateBpm)
                : "-- BPM");
        caloriesText.setText(String.format(Locale.US, "%.0f kcal", snapshot.calories));

        if (!trackingActive) {
            statusText.setText(R.string.steps_status_finished);
            return;
        }

        switch (snapshot.activityType) {
            case "RUNNING":
                statusText.setText(R.string.steps_live_running);
                break;
            case "WALKING":
                statusText.setText(R.string.steps_live_walking);
                break;
            default:
                statusText.setText(R.string.steps_live_still);
                break;
        }
    }

    private void renderIdleLiveCard() {
        activityBadgeText.setText("STILL");
        syncStatusText.setText(currentSyncLabel());
        stepCountText.setText("0");
        statusText.setText(R.string.steps_status_idle);
        distanceText.setText("0 m");
        speedText.setText("0.0 km/h");
        paceText.setText("0 /min");
        heartRateText.setText("-- BPM");
        caloriesText.setText("0 kcal");
    }

    private void renderSummary(FitnessDatabaseHelper.DashboardSummary summary) {
        summaryStepsText.setText(formatSteps(summary.totalSteps));
        summaryDistanceText.setText(formatDistanceCompact(summary.totalDistanceMeters));
        summarySessionsText.setText(String.valueOf(summary.sessionCount));
        bestSessionText.setText(getString(
                R.string.home_best_session_value,
                formatSteps(summary.bestSessionSteps)
        ));
    }

    private void renderHistory(List<FitnessSessionLocalRecord> sessions) {
        historyContainer.removeAllViews();

        if (sessions.isEmpty()) {
            emptyHistoryText.setVisibility(View.VISIBLE);
            return;
        }

        emptyHistoryText.setVisibility(View.GONE);
        for (FitnessSessionLocalRecord session : sessions) {
            historyContainer.addView(buildHistoryCard(session));
        }
    }

    private View buildHistoryCard(FitnessSessionLocalRecord session) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_surface_alt_card);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(12);
        card.setLayoutParams(cardParams);

        int padding = dp(16);
        card.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(15f);
        title.setText(String.format(
                Locale.US,
                "%s  •  %s",
                formatStartTime(session.startedAtMs),
                session.active ? getString(R.string.history_live_now) : session.activityType
        ));

        TextView steps = new TextView(this);
        steps.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        steps.setTextSize(20f);
        steps.setPadding(0, dp(8), 0, 0);
        steps.setText(getString(R.string.history_steps_value, formatSteps(session.totalSteps)));

        TextView detail = new TextView(this);
        detail.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        detail.setTextSize(13f);
        detail.setPadding(0, dp(6), 0, 0);
        detail.setText(getString(
                R.string.history_detail_value,
                formatDistanceCompact(session.distanceMeters),
                session.caloriesBurned,
                formatDuration(session.durationSeconds),
                session.isSynced() ? getString(R.string.sync_online) : getString(R.string.sync_offline_queue)
        ));

        card.addView(title);
        card.addView(steps);
        card.addView(detail);
        return card;
    }

    private void updateTrackingButton() {
        startTrackingButton.setText(tracking ? R.string.steps_stop : R.string.steps_start);
    }

    private void registerSnapshotReceiver() {
        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter(StepTrackingService.ACTION_SNAPSHOT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(snapshotReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(snapshotReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void unregisterSnapshotReceiver() {
        if (!receiverRegistered) {
            return;
        }
        unregisterReceiver(snapshotReceiver);
        receiverRegistered = false;
    }

    private String currentSyncLabel() {
        return NetworkUtils.isInternetAvailable(this)
                ? getString(R.string.sync_online)
                : getString(R.string.sync_offline_queue);
    }

    private static String formatSteps(int steps) {
        return String.format(Locale.US, "%,d", steps);
    }

    private static String formatDistance(float distanceMeters) {
        if (distanceMeters >= 1000f) {
            return String.format(Locale.US, "%.2f km", distanceMeters / 1000f);
        }
        return String.format(Locale.US, "%.0f m", distanceMeters);
    }

    private static String formatDistanceCompact(float distanceMeters) {
        if (distanceMeters >= 1000f) {
            return String.format(Locale.US, "%.2f km", distanceMeters / 1000f);
        }
        return String.format(Locale.US, "%.0f m", distanceMeters);
    }

    private static String formatDuration(int durationSeconds) {
        int hours = durationSeconds / 3600;
        int minutes = (durationSeconds % 3600) / 60;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm", hours, minutes);
        }
        return String.format(Locale.US, "%dm", minutes);
    }

    private static String formatStartTime(long startedAtMs) {
        SimpleDateFormat format = new SimpleDateFormat("dd MMM  •  hh:mm a", Locale.US);
        return format.format(new Date(startedAtMs));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void maybeRequestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || TrackingPreferences.wasBatteryPromptShown(this)) {
            return;
        }

        PowerManager powerManager = getSystemService(PowerManager.class);
        if (powerManager != null
                && powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            TrackingPreferences.setBatteryPromptShown(this, true);
            return;
        }

        TrackingPreferences.setBatteryPromptShown(this, true);
        Toast.makeText(this, R.string.battery_optimization_hint, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName())
        );
        try {
            startActivity(intent);
        } catch (Exception exception) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                // If settings cannot be opened, tracking still continues with the foreground service.
            }
        }
    }
}