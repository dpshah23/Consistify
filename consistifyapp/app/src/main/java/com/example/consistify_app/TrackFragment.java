package com.example.consistify_app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Image;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

public class TrackFragment extends Fragment implements SensorEventListener {

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
    private Button saveSessionButton;

    private ExecutorService cameraExecutor;
    private PoseDetector poseDetector;
    private ExerciseRepCounter repCounter;
    private ProcessCameraProvider cameraProvider;
    private boolean isProcessingFrame;

    // Gamification Manager to save stats
    private GamificationManager gamificationManager;

    // Step tracking
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private StepDetectionEngine stepEngine;
    private int currentStepsWalked = 0;

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

        previewView = view.findViewById(R.id.previewView);
        exerciseModeText = view.findViewById(R.id.exerciseModeText);
        phaseText = view.findViewById(R.id.phaseText);
        instructionText = view.findViewById(R.id.instructionText);
        squatCountText = view.findViewById(R.id.squatCountText);
        pushupCountText = view.findViewById(R.id.pushupCountText);
        stepCountText = view.findViewById(R.id.stepCountText);
        permissionHintText = view.findViewById(R.id.permissionHintText);
        
        autoModeButton = view.findViewById(R.id.autoModeButton);
        squatModeButton = view.findViewById(R.id.squatModeButton);
        pushupModeButton = view.findViewById(R.id.pushupModeButton);
        saveSessionButton = view.findViewById(R.id.btn_save_session);

        // Step Counter logic setup
        sensorManager = (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            // Fallback to accelerometer for step engine if needed
            if (stepSensor == null) {
                stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
                stepEngine = new StepDetectionEngine(snapshot -> {
                    if (snapshot.steps > 0) {
                        currentStepsWalked += snapshot.steps;
                        stepEngine.reset();
                        stepCountText.setText(String.valueOf(currentStepsWalked));
                    }
                });
            }
        }

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

        saveSessionButton.setOnClickListener(v -> {
            int squats = repCounter.analyze(null, SystemClock.elapsedRealtime()).squatCount;
            int pushups = repCounter.analyze(null, SystemClock.elapsedRealtime()).pushupCount;
            
            gamificationManager.addSquats(squats);
            gamificationManager.addPushups(pushups);
            gamificationManager.addSteps(currentStepsWalked);
            
            Toast.makeText(requireContext(), "Saved: " + squats + " squats, " + pushups + " pushups, " + currentStepsWalked + " steps! XP & Coins awarded.", Toast.LENGTH_LONG).show();
            
            // Reset counters
            repCounter.reset();
            currentStepsWalked = 0;
            updateUI(repCounter.analyze(null, 0));
            stepCountText.setText("0");
        });

        applyDetectionMode(ExerciseRepCounter.DetectionMode.AUTO);

        if (hasCameraPermission()) {
            previewView.post(this::startCamera);
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sensorManager != null && stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            currentStepsWalked++;
            stepCountText.setText(String.valueOf(currentStepsWalked));
        } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION && stepEngine != null) {
            stepEngine.onSensorChanged(event);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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
