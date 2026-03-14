package com.example.consistify_app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

    public class TrackFragment extends Fragment {

    private static final String TAG = "TrackFragment";

    private PreviewView previewView;
    private TextView exerciseModeText;
    private TextView phaseText;
    private TextView instructionText;
    private TextView squatCountText;
    private TextView pushupCountText;
    private TextView stepCountText;
    private TextView permissionHintText;
    
    private Button autoModeButton;
    private Button squatModeButton;
    private Button pushupModeButton;
    private Button startDetectionButton;
    private Button endSessionButton;

    private LinearLayout hudContainer;
    private FrameLayout cameraContainer;
    private TextView globalStepCountText;
    private TextView hudDistanceText;
    private TextView hudCaloriesText;
    private TextView hudPaceText;

    private ExecutorService cameraExecutor;
    private PoseDetector poseDetector;
    private ExerciseRepCounter repCounter;
    private ProcessCameraProvider cameraProvider;
    private boolean isProcessingFrame;

    // Gamification Manager to save stats
    private GamificationManager gamificationManager;

    private final BroadcastReceiver stepReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("STEPS_UPDATED".equals(intent.getAction())) {
                int totalSteps = intent.getIntExtra("TOTAL_STEPS", gamificationManager.getDailySteps());
                float distance = intent.getFloatExtra("DISTANCE", totalSteps * 0.72f);
                float calories = intent.getFloatExtra("CALORIES", totalSteps * 0.040f);
                float speed = intent.getFloatExtra("SPEED", 4.0f);
                
                updateGlobalStepCount(totalSteps, distance, calories, speed);
            }
        }
    };

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    permissionHintText.setVisibility(View.GONE);
                    previewView.post(this::startCamera);
                } else {
                    permissionHintText.setText("Camera Permission Required");
                    permissionHintText.setVisibility(View.VISIBLE);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_track, container, false);

        gamificationManager = new GamificationManager(requireContext());

        hudContainer = view.findViewById(R.id.hud_container);
        cameraContainer = view.findViewById(R.id.camera_container);
        globalStepCountText = view.findViewById(R.id.globalStepCountText);
        hudDistanceText = view.findViewById(R.id.tv_hud_distance);
        hudCaloriesText = view.findViewById(R.id.tv_hud_calories);
        hudPaceText = view.findViewById(R.id.tv_hud_pace);

        previewView = view.findViewById(R.id.previewView);
        exerciseModeText = view.findViewById(R.id.exerciseModeText);
        phaseText = view.findViewById(R.id.phaseText);
        instructionText = view.findViewById(R.id.instructionText);
        squatCountText = view.findViewById(R.id.squatCountText);
        pushupCountText = view.findViewById(R.id.pushupCountText);
        permissionHintText = view.findViewById(R.id.permissionHintText);
        
        autoModeButton = view.findViewById(R.id.autoModeButton);
        squatModeButton = view.findViewById(R.id.squatModeButton);
        pushupModeButton = view.findViewById(R.id.pushupModeButton);
        startDetectionButton = view.findViewById(R.id.btn_start_detection);
        endSessionButton = view.findViewById(R.id.btn_end_session);

        // Compute baseline approximations if service hasn't broadcast yet
        int steps = gamificationManager.getDailySteps();
        updateGlobalStepCount(steps, steps * 0.72f, steps * 0.040f, 0f);

        cameraExecutor = Executors.newSingleThreadExecutor();
        poseDetector = PoseDetection.getClient(
                new AccuratePoseDetectorOptions.Builder()
                        .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
                        .build()
        );
        repCounter = new ExerciseRepCounter();

        autoModeButton.setOnClickListener(v -> applyDetectionMode(ExerciseRepCounter.DetectionMode.AUTO));
        squatModeButton.setOnClickListener(v -> applyDetectionMode(ExerciseRepCounter.DetectionMode.SQUAT));
        pushupModeButton.setOnClickListener(v -> applyDetectionMode(ExerciseRepCounter.DetectionMode.PUSHUP));

        startDetectionButton.setOnClickListener(v -> {
            hudContainer.setVisibility(View.GONE);
            cameraContainer.setVisibility(View.VISIBLE);
            if (hasCameraPermission()) {
                previewView.post(this::startCamera);
            } else {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        });

        endSessionButton.setOnClickListener(v -> {
            int squats = repCounter.analyze(null, SystemClock.elapsedRealtime()).squatCount;
            int pushups = repCounter.analyze(null, SystemClock.elapsedRealtime()).pushupCount;
            
            gamificationManager.addSquats(squats);
            gamificationManager.addPushups(pushups);
            
            // Unbind camera
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
            }

            // Notify other fragments of the new stats
            Intent updateIntent = new Intent("STATS_UPDATED");
            updateIntent.setPackage(requireContext().getPackageName());
            requireContext().sendBroadcast(updateIntent);

            Toast.makeText(requireContext(), "Session Ended. " + squats + " squats, " + pushups + " pushups logged!", Toast.LENGTH_LONG).show();
            
            // Reset counters
            repCounter.reset();
            updateUI(repCounter.analyze(null, 0));
            
            cameraContainer.setVisibility(View.GONE);
            hudContainer.setVisibility(View.VISIBLE);
        });

        applyDetectionMode(ExerciseRepCounter.DetectionMode.AUTO);

        return view;
    }

    private void updateGlobalStepCount(int steps, float distanceM, float calories, float speedKmh) {
        if (GlobalContext.isActive(this)) {
            globalStepCountText.setText(String.valueOf(steps));
            hudDistanceText.setText(String.format("%.2f km", distanceM / 1000f));
            hudCaloriesText.setText(String.format("%.0f kcal", calories));
            hudPaceText.setText(String.format("%.1f km/h", speedKmh));
        }
    }

    private static class GlobalContext {
        static boolean isActive(Fragment f) {
            return f.isAdded() && f.getActivity() != null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(stepReceiver, new IntentFilter("STEPS_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(stepReceiver, new IntentFilter("STEPS_UPDATED"));
        }
        int steps = gamificationManager.getDailySteps();
        updateGlobalStepCount(steps, steps * 0.72f, steps * 0.040f, 0f);
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            requireContext().unregisterReceiver(stepReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "stepReceiver not registered", e);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
                permissionHintText.setVisibility(View.GONE);
            } catch (ExecutionException | InterruptedException exception) {
                Log.e(TAG, "Unable to start camera", exception);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        int rotation = previewView.getDisplay() == null ? android.view.Surface.ROTATION_0 : previewView.getDisplay().getRotation();

        Preview preview = new Preview.Builder().setTargetRotation(rotation).build();
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
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        }

        cameraProvider.unbindAll();
        try {
            cameraProvider.bindToLifecycle(getViewLifecycleOwner(), cameraSelector, preview, imageAnalysis);
        } catch (Exception exception) {
            Log.e(TAG, "Unable to bind camera", exception);
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
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        poseDetector.process(inputImage)
                .addOnSuccessListener(pose -> {
                    if (isAdded() && getActivity() != null) {
                        ExerciseRepCounter.AnalysisResult result = repCounter.analyze(pose, SystemClock.elapsedRealtime());
                        getActivity().runOnUiThread(() -> updateUI(result));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Pose detection failed"))
                .addOnCompleteListener(task -> {
                    isProcessingFrame = false;
                    imageProxy.close();
                });
    }

    private void updateUI(ExerciseRepCounter.AnalysisResult result) {
        if(result == null) return;
        exerciseModeText.setText(result.exerciseLabel);
        phaseText.setText(result.phaseLabel);
        instructionText.setText(result.instruction);
        squatCountText.setText(String.valueOf(result.squatCount));
        pushupCountText.setText(String.valueOf(result.pushupCount));
    }

    private void applyDetectionMode(ExerciseRepCounter.DetectionMode mode) {
        repCounter.setDetectionMode(mode);
        autoModeButton.setAlpha(mode == ExerciseRepCounter.DetectionMode.AUTO ? 1f : 0.6f);
        squatModeButton.setAlpha(mode == ExerciseRepCounter.DetectionMode.SQUAT ? 1f : 0.6f);
        pushupModeButton.setAlpha(mode == ExerciseRepCounter.DetectionMode.PUSHUP ? 1f : 0.6f);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
