package com.example.exercisedetector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class squat extends AppCompatActivity {

    private static final String TAG = "squat";

    private PreviewView previewView;
    private TextView exerciseModeText;
    private TextView syncStatusText;
    private TextView phaseText;
    private TextView instructionText;
    private TextView squatCountText;
    private TextView pushupCountText;
    private TextView metricsText;
    private TextView permissionHintText;
    private Button autoModeButton;
    private Button squatModeButton;
    private Button pushupModeButton;
    private Button resetButton;

    private ExecutorService cameraExecutor;
    private PoseDetector poseDetector;
    private ExerciseRepCounter repCounter;
    private ProcessCameraProvider cameraProvider;
    private WorkoutDatabaseHelper workoutDatabaseHelper;
    private boolean isProcessingFrame;

    // --- Local session tracking ---
    private long activeLocalSessionId = -1L;
    private long sessionStartMs;
    private int lastPostedSquatCount = 0;
    private int lastPostedPushupCount = 0;
    private int lastPersistedSquatCount = 0;
    private int lastPersistedPushupCount = 0;
    private long lastPersistedAtMs = 0L;
    private boolean hasPendingLocalChanges = false;
    private ExerciseRepCounter.AnalysisResult lastResult = null;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    permissionHintText.setVisibility(View.GONE);
                    previewView.post(this::startCamera);
                } else {
                    permissionHintText.setText(R.string.camera_permission_required);
                    permissionHintText.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_squat);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        previewView = findViewById(R.id.previewView);
        exerciseModeText = findViewById(R.id.exerciseModeText);
        syncStatusText = findViewById(R.id.syncStatusText);
        phaseText = findViewById(R.id.phaseText);
        instructionText = findViewById(R.id.instructionText);
        squatCountText = findViewById(R.id.squatCountText);
        pushupCountText = findViewById(R.id.pushupCountText);
        metricsText = findViewById(R.id.metricsText);
        permissionHintText = findViewById(R.id.permissionHintText);
        autoModeButton = findViewById(R.id.autoModeButton);
        squatModeButton = findViewById(R.id.squatModeButton);
        pushupModeButton = findViewById(R.id.pushupModeButton);
        resetButton = findViewById(R.id.resetButton);

        cameraExecutor = Executors.newSingleThreadExecutor();
        workoutDatabaseHelper = new WorkoutDatabaseHelper(this);
        WorkoutSyncWorker.schedulePeriodic(this);
        poseDetector = PoseDetection.getClient(
                new AccuratePoseDetectorOptions.Builder()
                        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                        .build()
        );
        repCounter = new ExerciseRepCounter();

        autoModeButton.setOnClickListener(view -> applyDetectionMode(ExerciseRepCounter.DetectionMode.AUTO));
        squatModeButton.setOnClickListener(view -> applyDetectionMode(ExerciseRepCounter.DetectionMode.SQUAT));
        pushupModeButton.setOnClickListener(view -> applyDetectionMode(ExerciseRepCounter.DetectionMode.PUSHUP));

        resetButton.setOnClickListener(view -> {
            finalizeSession();
            repCounter.reset();
            lastPostedSquatCount = 0;
            lastPostedPushupCount = 0;
            lastPersistedSquatCount = 0;
            lastPersistedPushupCount = 0;
            lastPersistedAtMs = 0L;
            hasPendingLocalChanges = false;
            lastResult = null;
            renderIdleState();
            startNewSession();
        });

        updateModeButtons();
        renderIdleState();

        if (hasCameraPermission()) {
            previewView.post(this::startCamera);
        } else {
            requestCameraPermission();
        }
        startNewSession();
    }

    private void renderIdleState() {
        exerciseModeText.setText(getString(R.string.searching_pose));
        updateSyncStatus();
        phaseText.setText(getString(R.string.idle_phase));
        instructionText.setText(getString(R.string.idle_instruction));
        metricsText.setText(getString(R.string.idle_metrics));
        squatCountText.setText(String.valueOf(0));
        pushupCountText.setText(String.valueOf(0));
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
                permissionHintText.setVisibility(View.GONE);
            } catch (ExecutionException | InterruptedException exception) {
                Log.e(TAG, "Unable to start camera", exception);
                permissionHintText.setText(R.string.camera_permission_required);
                permissionHintText.setVisibility(View.VISIBLE);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            return;
        }

        int rotation = previewView.getDisplay() == null
                ? Surface.ROTATION_0
                : previewView.getDisplay().getRotation();

        Preview preview = new Preview.Builder()
                .setTargetRotation(rotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
                .build();
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

        CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        try {
            if (!cameraProvider.hasCamera(cameraSelector)) {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            }
        } catch (Exception exception) {
            Log.w(TAG, "Falling back to default back camera", exception);
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        }

        cameraProvider.unbindAll();
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
        } catch (Exception exception) {
            Log.e(TAG, "Unable to bind camera use cases", exception);
            permissionHintText.setText(R.string.camera_unavailable);
            permissionHintText.setVisibility(View.VISIBLE);
        }
    }

    private void analyzeFrame(ImageProxy imageProxy) {
        if (isProcessingFrame) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        isProcessingFrame = true;

        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.getImageInfo().getRotationDegrees()
        );

        poseDetector.process(inputImage)
                .addOnSuccessListener(pose -> renderAnalysis(repCounter.analyze(pose, SystemClock.elapsedRealtime())))
                .addOnFailureListener(exception -> Log.e(TAG, "Pose detection failed", exception))
                .addOnCompleteListener(task -> {
                    isProcessingFrame = false;
                    imageProxy.close();
                });
    }

    private void renderAnalysis(ExerciseRepCounter.AnalysisResult result) {
        if (activeLocalSessionId <= 0L) {
            startNewSession();
        }

        if (result.squatCount > lastPostedSquatCount) {
            queueRepRange("squat", lastPostedSquatCount + 1, result.squatCount);
            lastPostedSquatCount = result.squatCount;
        }
        if (result.pushupCount > lastPostedPushupCount) {
            queueRepRange("pushup", lastPostedPushupCount + 1, result.pushupCount);
            lastPostedPushupCount = result.pushupCount;
        }

        persistSessionSnapshot(result, false);
        lastResult = result;

        runOnUiThread(() -> {
            exerciseModeText.setText(result.exerciseLabel);
            updateSyncStatus();
            phaseText.setText(result.phaseLabel);
            instructionText.setText(result.instruction);
            metricsText.setText(result.metricsText);
            squatCountText.setText(String.valueOf(result.squatCount));
            pushupCountText.setText(String.valueOf(result.pushupCount));
        });
    }

    private void applyDetectionMode(ExerciseRepCounter.DetectionMode detectionMode) {
        repCounter.setDetectionMode(detectionMode);
        updateModeButtons();
    }

    private void updateModeButtons() {
        ExerciseRepCounter.DetectionMode detectionMode = repCounter.getDetectionMode();
        setModeButtonState(autoModeButton, detectionMode == ExerciseRepCounter.DetectionMode.AUTO);
        setModeButtonState(squatModeButton, detectionMode == ExerciseRepCounter.DetectionMode.SQUAT);
        setModeButtonState(pushupModeButton, detectionMode == ExerciseRepCounter.DetectionMode.PUSHUP);
    }

    private static void setModeButtonState(Button button, boolean selected) {
        button.setEnabled(!selected);
        button.setAlpha(selected ? 1f : 0.65f);
    }

    private void updateSyncStatus() {
        if (!NetworkUtils.isInternetAvailable(this)) {
            syncStatusText.setText(R.string.sync_offline_queue);
            return;
        }
        syncStatusText.setText(hasPendingLocalChanges ? R.string.sync_pending : R.string.sync_online);
    }

    private void queueRepRange(String exerciseType, int startRepNumber, int endRepNumber) {
        if (activeLocalSessionId <= 0L || startRepNumber > endRepNumber) {
            return;
        }

        long baseTimestampMs = System.currentTimeMillis();
        for (int repNumber = startRepNumber; repNumber <= endRepNumber; repNumber++) {
            workoutDatabaseHelper.queueRep(
                    activeLocalSessionId,
                    exerciseType,
                    repNumber,
                    baseTimestampMs + repNumber
            );
        }

        hasPendingLocalChanges = true;
        WorkoutSyncWorker.enqueue(this);
    }

    private void persistSessionSnapshot(ExerciseRepCounter.AnalysisResult result, boolean force) {
        if (activeLocalSessionId <= 0L) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean countsChanged = result.squatCount != lastPersistedSquatCount
                || result.pushupCount != lastPersistedPushupCount;
        boolean timedRefresh = nowMs - lastPersistedAtMs >= 2_000L;
        if (!force && !countsChanged && !timedRefresh) {
            return;
        }

        int durationSeconds = (int) ((SystemClock.elapsedRealtime() - sessionStartMs) / 1000L);
        workoutDatabaseHelper.updateSession(
                activeLocalSessionId,
                result.squatCount,
                result.pushupCount,
                durationSeconds,
                nowMs
        );
        lastPersistedSquatCount = result.squatCount;
        lastPersistedPushupCount = result.pushupCount;
        lastPersistedAtMs = nowMs;
        hasPendingLocalChanges = true;

        if (force || countsChanged) {
            WorkoutSyncWorker.enqueue(this);
        }
    }

    private void startNewSession() {
        WorkoutSessionLocalRecord activeSession = workoutDatabaseHelper.getActiveSession();
        if (activeSession != null) {
            activeLocalSessionId = activeSession.localId;
            sessionStartMs = SystemClock.elapsedRealtime() - (activeSession.durationSeconds * 1000L);
            lastPostedSquatCount = activeSession.squatCount;
            lastPostedPushupCount = activeSession.pushupCount;
            lastPersistedSquatCount = activeSession.squatCount;
            lastPersistedPushupCount = activeSession.pushupCount;
            lastPersistedAtMs = 0L;
            hasPendingLocalChanges = !activeSession.isSynced();
            repCounter.restoreCounts(activeSession.squatCount, activeSession.pushupCount);
            squatCountText.setText(String.valueOf(activeSession.squatCount));
            pushupCountText.setText(String.valueOf(activeSession.pushupCount));
        } else {
            sessionStartMs = SystemClock.elapsedRealtime();
            long nowMs = System.currentTimeMillis();
            activeLocalSessionId = workoutDatabaseHelper.createOrGetActiveSession(nowMs);
            lastPostedSquatCount = 0;
            lastPostedPushupCount = 0;
            lastPersistedSquatCount = 0;
            lastPersistedPushupCount = 0;
            lastPersistedAtMs = 0L;
            hasPendingLocalChanges = true;
        }

        updateSyncStatus();
        WorkoutSyncWorker.enqueue(this);
    }

    private void finalizeSession() {
        if (activeLocalSessionId <= 0L) {
            return;
        }

        int squats = lastResult != null ? lastResult.squatCount : lastPersistedSquatCount;
        int pushups = lastResult != null ? lastResult.pushupCount : lastPersistedPushupCount;
        int durationSeconds = (int) ((SystemClock.elapsedRealtime() - sessionStartMs) / 1000);
        long nowMs = System.currentTimeMillis();
        workoutDatabaseHelper.finishSession(
                activeLocalSessionId,
                squats,
                pushups,
                durationSeconds,
                nowMs
        );
        activeLocalSessionId = -1L;
        lastPersistedAtMs = 0L;
        hasPendingLocalChanges = true;
        updateSyncStatus();
        WorkoutSyncWorker.enqueue(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        finalizeSession();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (poseDetector != null) {
            poseDetector.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}