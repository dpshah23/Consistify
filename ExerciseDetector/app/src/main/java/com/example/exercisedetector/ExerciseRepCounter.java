package com.example.exercisedetector;

import android.graphics.PointF;

import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;

import java.util.Locale;

public final class ExerciseRepCounter {

    private static final float MIN_LANDMARK_CONFIDENCE = 0.55f;
    private static final float SMOOTHING_ALPHA = 0.35f;

    private static final float SQUAT_TOP_KNEE_ANGLE = 165f;
    private static final float SQUAT_BOTTOM_KNEE_ANGLE = 95f;
    private static final float SQUAT_TOP_HIP_ANGLE = 155f;
    private static final float SQUAT_BOTTOM_HIP_ANGLE = 120f;

    private static final float PUSHUP_TOP_ELBOW_ANGLE = 140f;
    private static final float PUSHUP_BOTTOM_ELBOW_ANGLE = 122f;
    private static final float PUSHUP_BODY_LINE_ANGLE = 132f;
    private static final float FRONT_PUSHUP_CANDIDATE_TORSO_HEIGHT_RATIO = 1.45f;
    private static final float FRONT_PUSHUP_TRACKING_TORSO_HEIGHT_RATIO = 2.30f;
    private static final float FRONT_PUSHUP_PLANK_TORSO_HEIGHT_RATIO = 1.40f;
    private static final float FRONT_PUSHUP_BOTTOM_TORSO_HEIGHT_RATIO = 2.05f;
    private static final float FRONT_PUSHUP_ARM_STACK_RATIO = 0.03f;

    private static final float UPRIGHT_TORSO_THRESHOLD = 35f;
    private static final float HORIZONTAL_TORSO_THRESHOLD = 42f;
    private static final float SIDE_PUSHUP_TRACKING_HORIZONTAL_THRESHOLD = 52f;

    private static final int EXERCISE_CONFIRMATION_FRAMES = 4;
    private static final int PUSHUP_EXERCISE_CONFIRMATION_FRAMES = 2;
    private static final int EXERCISE_RELEASE_FRAMES = 10;
    private static final int PUSHUP_POSITION_CONFIRMATION_FRAMES = 2;
    private static final long REP_COOLDOWN_MS = 650L;

    private int squatCount;
    private int pushupCount;

    private ExerciseType activeExercise = ExerciseType.NONE;
    private DetectionMode detectionMode = DetectionMode.AUTO;
    private int squatCandidateFrames;
    private int pushupCandidateFrames;
    private int unmatchedFrames;

    private boolean squatPrimed;
    private boolean squatBottomReached;
    private boolean pushupPrimed;
    private boolean pushupBottomReached;
    private int pushupTopFrames;
    private int pushupBottomFrames;

    private long lastSquatRepTimestampMs;
    private long lastPushupRepTimestampMs;

    private float smoothedKneeAngle = Float.NaN;
    private float smoothedHipAngle = Float.NaN;
    private float smoothedElbowAngle = Float.NaN;
    private float smoothedBodyLineAngle = Float.NaN;
    private float smoothedFrontTorsoRatio = Float.NaN;

    public AnalysisResult analyze(Pose pose, long timestampMs) {
        PoseMetrics metrics = PoseMetrics.from(pose);
        updateActiveExercise(metrics);

        if (!metrics.hasCorePose) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    activeExercise.displayName,
                    "Searching for full-body pose",
                    "Step back until shoulders, hips, knees, and ankles are visible.",
                    metrics.summaryText()
            );
        }

        if (activeExercise == ExerciseType.SQUAT) {
            return analyzeSquat(metrics, timestampMs);
        }

        if (activeExercise == ExerciseType.PUSHUP) {
            return analyzePushup(metrics, timestampMs);
        }

        return buildIdleResult(metrics);
    }

    public void reset() {
        squatCount = 0;
        pushupCount = 0;
        activeExercise = detectionMode == DetectionMode.SQUAT
            ? ExerciseType.SQUAT
            : detectionMode == DetectionMode.PUSHUP
            ? ExerciseType.PUSHUP
            : ExerciseType.NONE;
        squatCandidateFrames = 0;
        pushupCandidateFrames = 0;
        unmatchedFrames = 0;
        resetSquatState();
        resetPushupState();
        smoothedKneeAngle = Float.NaN;
        smoothedHipAngle = Float.NaN;
        smoothedElbowAngle = Float.NaN;
        smoothedBodyLineAngle = Float.NaN;
        smoothedFrontTorsoRatio = Float.NaN;
        pushupTopFrames = 0;
        pushupBottomFrames = 0;
    }

    public void setDetectionMode(DetectionMode detectionMode) {
        this.detectionMode = detectionMode;
        switch (detectionMode) {
            case SQUAT:
                switchExercise(ExerciseType.SQUAT);
                break;
            case PUSHUP:
                switchExercise(ExerciseType.PUSHUP);
                break;
            case AUTO:
            default:
                switchExercise(ExerciseType.NONE);
                break;
        }
    }

    public DetectionMode getDetectionMode() {
        return detectionMode;
    }

    public void restoreCounts(int squatCount, int pushupCount) {
        this.squatCount = Math.max(0, squatCount);
        this.pushupCount = Math.max(0, pushupCount);
    }

    private AnalysisResult buildIdleResult(PoseMetrics metrics) {
        if (metrics.isSquatCandidate()) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.NONE.displayName,
                    "Squat stance detected",
                    "Stand tall, then squat until your hips drop below knee level and return to full extension.",
                    "Knee " + formatAngle(metrics.kneeAngle) + " | Hip " + formatAngle(metrics.hipAngle)
            );
        }

        if (metrics.isPushupCandidate()) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.NONE.displayName,
                    "Push-up stance detected",
                    "Keep a straight plank, lower with control, and press back to full arm extension.",
                    metrics.pushupSummaryText()
            );
        }

        return new AnalysisResult(
                squatCount,
                pushupCount,
                ExerciseType.NONE.displayName,
                "Looking for squat or push-up",
                "Use an upright full-body view for squats or a side/front plank view for push-ups.",
                metrics.summaryText()
        );
    }

    private AnalysisResult analyzeSquat(PoseMetrics metrics, long timestampMs) {
        if (Float.isNaN(metrics.kneeAngle) || Float.isNaN(metrics.hipAngle)) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.SQUAT.displayName,
                    "Squat tracking paused",
                    "Keep your hips, knees, and ankles in frame for accurate squat counting.",
                    metrics.summaryText()
            );
        }

        smoothedKneeAngle = smooth(smoothedKneeAngle, metrics.kneeAngle);
        smoothedHipAngle = smooth(smoothedHipAngle, metrics.hipAngle);

        boolean standingTall = smoothedKneeAngle >= SQUAT_TOP_KNEE_ANGLE
                && smoothedHipAngle >= SQUAT_TOP_HIP_ANGLE;
        boolean depthReached = smoothedKneeAngle <= SQUAT_BOTTOM_KNEE_ANGLE
                && smoothedHipAngle <= SQUAT_BOTTOM_HIP_ANGLE;

        if (standingTall && !squatBottomReached) {
            squatPrimed = true;
        }

        if (squatPrimed && depthReached) {
            squatBottomReached = true;
        }

        if (squatPrimed && squatBottomReached && standingTall
                && timestampMs - lastSquatRepTimestampMs > REP_COOLDOWN_MS) {
            squatCount += 1;
            lastSquatRepTimestampMs = timestampMs;
            resetSquatState();
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.SQUAT.displayName,
                    "Squat rep counted",
                    "Rep locked in. Lower again under control for the next one.",
                    "Knee " + formatAngle(smoothedKneeAngle) + " | Hip " + formatAngle(smoothedHipAngle)
            );
        }

        if (squatBottomReached) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.SQUAT.displayName,
                    "Drive up",
                    "Stand fully upright to finish the squat rep.",
                    "Knee " + formatAngle(smoothedKneeAngle) + " | Hip " + formatAngle(smoothedHipAngle)
            );
        }

        if (standingTall) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.SQUAT.displayName,
                    "Ready for squat",
                    "Lower until your knees and hips close further, then return to full extension.",
                    "Knee " + formatAngle(smoothedKneeAngle) + " | Hip " + formatAngle(smoothedHipAngle)
            );
        }

        return new AnalysisResult(
                squatCount,
                pushupCount,
                ExerciseType.SQUAT.displayName,
                "Lower deeper",
                "Aim for more depth before you stand back up so the squat counts cleanly.",
                "Knee " + formatAngle(smoothedKneeAngle) + " | Hip " + formatAngle(smoothedHipAngle)
        );
    }

    private AnalysisResult analyzePushup(PoseMetrics metrics, long timestampMs) {
        boolean sideViewFrame = metrics.isSidePushupTrackingFrame();
        boolean frontViewFrame = metrics.isFrontPushupTrackingFrame();

        if (Float.isNaN(metrics.elbowAngle) || (!sideViewFrame && !frontViewFrame)) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.PUSHUP.displayName,
                    "Push-up tracking paused",
                    "Keep shoulders, elbows, wrists, and hips visible. Side view tracks body line; front view needs both shoulders and hips in frame.",
                    metrics.summaryText()
            );
        }

        smoothedElbowAngle = smooth(smoothedElbowAngle, metrics.elbowAngle);

        boolean plankLocked;
        boolean topCandidate;
        boolean bottomCandidate;
        String metricsText;
        String plankInstruction;

        if (sideViewFrame) {
            smoothedBodyLineAngle = smooth(smoothedBodyLineAngle, metrics.bodyLineAngle);
            smoothedFrontTorsoRatio = Float.NaN;

            plankLocked = smoothedBodyLineAngle >= PUSHUP_BODY_LINE_ANGLE - 10f;
            topCandidate = smoothedElbowAngle >= PUSHUP_TOP_ELBOW_ANGLE;
            bottomCandidate = smoothedElbowAngle <= PUSHUP_BOTTOM_ELBOW_ANGLE
                && smoothedBodyLineAngle >= PUSHUP_BODY_LINE_ANGLE - 20f;
            metricsText = "Elbow " + formatAngle(smoothedElbowAngle)
                    + " | Body line " + formatAngle(smoothedBodyLineAngle);
            plankInstruction = "Keep shoulders, hips, and ankles aligned before lowering further.";
        } else {
            smoothedFrontTorsoRatio = smooth(smoothedFrontTorsoRatio, metrics.torsoHeightToShoulderWidth);
            smoothedBodyLineAngle = Float.NaN;

            plankLocked = smoothedFrontTorsoRatio <= FRONT_PUSHUP_PLANK_TORSO_HEIGHT_RATIO;
            topCandidate = smoothedElbowAngle >= PUSHUP_TOP_ELBOW_ANGLE;
            bottomCandidate = smoothedElbowAngle <= PUSHUP_BOTTOM_ELBOW_ANGLE
                    && smoothedFrontTorsoRatio <= FRONT_PUSHUP_BOTTOM_TORSO_HEIGHT_RATIO;
            metricsText = "Elbow " + formatAngle(smoothedElbowAngle)
                    + " | Torso ratio " + formatRatio(smoothedFrontTorsoRatio);
            plankInstruction = "Keep your hips level with your shoulders and avoid piking or sagging.";
        }

        pushupTopFrames = topCandidate
                ? Math.min(PUSHUP_POSITION_CONFIRMATION_FRAMES, pushupTopFrames + 1)
                : Math.max(0, pushupTopFrames - 1);
        pushupBottomFrames = bottomCandidate
                ? Math.min(PUSHUP_POSITION_CONFIRMATION_FRAMES, pushupBottomFrames + 1)
                : Math.max(0, pushupBottomFrames - 1);

        boolean topPosition = pushupTopFrames >= PUSHUP_POSITION_CONFIRMATION_FRAMES;
        boolean bottomPosition = pushupBottomFrames >= PUSHUP_POSITION_CONFIRMATION_FRAMES;

        if (topPosition && !pushupBottomReached) {
            pushupPrimed = true;
        }

        if (pushupPrimed && bottomPosition) {
            pushupBottomReached = true;
        }

        if (pushupPrimed && pushupBottomReached && topPosition
                && timestampMs - lastPushupRepTimestampMs > REP_COOLDOWN_MS) {
            pushupCount += 1;
            lastPushupRepTimestampMs = timestampMs;
            resetPushupState();
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.PUSHUP.displayName,
                    "Push-up rep counted",
                    "Full lockout reached. Keep the plank straight for the next rep.",
                    metricsText
            );
        }

        if (!plankLocked) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.PUSHUP.displayName,
                    "Straighten your plank",
                    plankInstruction,
                    metricsText
            );
        }

        if (pushupBottomReached) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.PUSHUP.displayName,
                    "Press up",
                    "Extend your arms back to the top to complete the push-up.",
                    metricsText
            );
        }

        if (topPosition) {
            return new AnalysisResult(
                    squatCount,
                    pushupCount,
                    ExerciseType.PUSHUP.displayName,
                    "Ready for push-up",
                    "Lower your chest while keeping the plank straight.",
                    metricsText
            );
        }

        return new AnalysisResult(
                squatCount,
                pushupCount,
                ExerciseType.PUSHUP.displayName,
                "Lower with control",
                "Bend your elbows more, then press back to full extension.",
                metricsText
        );
    }

    private void updateActiveExercise(PoseMetrics metrics) {
        if (detectionMode == DetectionMode.SQUAT) {
            if (activeExercise != ExerciseType.SQUAT) {
                switchExercise(ExerciseType.SQUAT);
            }
            unmatchedFrames = 0;
            squatCandidateFrames = 0;
            pushupCandidateFrames = 0;
            return;
        }

        if (detectionMode == DetectionMode.PUSHUP) {
            if (activeExercise != ExerciseType.PUSHUP) {
                switchExercise(ExerciseType.PUSHUP);
            }
            unmatchedFrames = 0;
            squatCandidateFrames = 0;
            pushupCandidateFrames = 0;
            return;
        }

        boolean squatCandidate = metrics.isSquatCandidate();
        boolean pushupCandidate = metrics.isPushupTrackingFrame();

        squatCandidateFrames = squatCandidate
                ? Math.min(EXERCISE_CONFIRMATION_FRAMES, squatCandidateFrames + 1)
                : Math.max(0, squatCandidateFrames - 1);
        pushupCandidateFrames = pushupCandidate
                ? Math.min(EXERCISE_CONFIRMATION_FRAMES, pushupCandidateFrames + 1)
                : Math.max(0, pushupCandidateFrames - 1);

        if (squatCandidateFrames >= EXERCISE_CONFIRMATION_FRAMES
                && activeExercise != ExerciseType.SQUAT) {
            switchExercise(ExerciseType.SQUAT);
            return;
        }

        if (pushupCandidateFrames >= PUSHUP_EXERCISE_CONFIRMATION_FRAMES
                && activeExercise != ExerciseType.PUSHUP) {
            switchExercise(ExerciseType.PUSHUP);
            return;
        }

        if (!squatCandidate && !pushupCandidate) {
            unmatchedFrames += 1;
            if (unmatchedFrames >= EXERCISE_RELEASE_FRAMES) {
                switchExercise(ExerciseType.NONE);
            }
            return;
        }

        unmatchedFrames = 0;
    }

    private void switchExercise(ExerciseType exerciseType) {
        activeExercise = exerciseType;
        unmatchedFrames = 0;
        if (exerciseType != ExerciseType.SQUAT) {
            resetSquatState();
            smoothedKneeAngle = Float.NaN;
            smoothedHipAngle = Float.NaN;
        }
        if (exerciseType != ExerciseType.PUSHUP) {
            resetPushupState();
            smoothedElbowAngle = Float.NaN;
            smoothedBodyLineAngle = Float.NaN;
            smoothedFrontTorsoRatio = Float.NaN;
        }
        if (exerciseType == ExerciseType.NONE) {
            squatCandidateFrames = 0;
            pushupCandidateFrames = 0;
        }
    }

    private void resetSquatState() {
        squatPrimed = false;
        squatBottomReached = false;
    }

    private void resetPushupState() {
        pushupPrimed = false;
        pushupBottomReached = false;
        pushupTopFrames = 0;
        pushupBottomFrames = 0;
    }

    private static float smooth(float previous, float current) {
        if (Float.isNaN(current)) {
            return previous;
        }
        if (Float.isNaN(previous)) {
            return current;
        }
        return previous + (current - previous) * SMOOTHING_ALPHA;
    }

    private static String formatAngle(float angle) {
        if (Float.isNaN(angle)) {
            return "--";
        }
        return Math.round(angle) + "°";
    }

    private static String formatRatio(float value) {
        if (Float.isNaN(value)) {
            return "--";
        }
        return String.format(Locale.US, "%.2f", value);
    }

    public static final class AnalysisResult {
        public final int squatCount;
        public final int pushupCount;
        public final String exerciseLabel;
        public final String phaseLabel;
        public final String instruction;
        public final String metricsText;

        private AnalysisResult(
                int squatCount,
                int pushupCount,
                String exerciseLabel,
                String phaseLabel,
                String instruction,
                String metricsText
        ) {
            this.squatCount = squatCount;
            this.pushupCount = pushupCount;
            this.exerciseLabel = exerciseLabel;
            this.phaseLabel = phaseLabel;
            this.instruction = instruction;
            this.metricsText = metricsText;
        }
    }

    private enum ExerciseType {
        NONE("Searching for pose"),
        SQUAT("Squat mode"),
        PUSHUP("Push-up mode");

        private final String displayName;

        ExerciseType(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum DetectionMode {
        AUTO,
        SQUAT,
        PUSHUP
    }

    private static final class PoseMetrics {
        private final float kneeAngle;
        private final float hipAngle;
        private final float elbowAngle;
        private final float bodyLineAngle;
        private final float torsoFromHorizontal;
        private final float torsoFromVertical;
        private final float torsoHeightToShoulderWidth;
        private final boolean sidePushupCandidate;
        private final boolean sidePushupTrackingFrame;
        private final boolean frontPushupCandidate;
        private final boolean frontPushupTrackingFrame;
        private final boolean hasCorePose;

        private PoseMetrics(
                float kneeAngle,
                float hipAngle,
                float elbowAngle,
                float bodyLineAngle,
                float torsoFromHorizontal,
                float torsoFromVertical,
                float torsoHeightToShoulderWidth,
                boolean sidePushupCandidate,
            boolean sidePushupTrackingFrame,
                boolean frontPushupCandidate,
            boolean frontPushupTrackingFrame,
                boolean hasCorePose
        ) {
            this.kneeAngle = kneeAngle;
            this.hipAngle = hipAngle;
            this.elbowAngle = elbowAngle;
            this.bodyLineAngle = bodyLineAngle;
            this.torsoFromHorizontal = torsoFromHorizontal;
            this.torsoFromVertical = torsoFromVertical;
            this.torsoHeightToShoulderWidth = torsoHeightToShoulderWidth;
            this.sidePushupCandidate = sidePushupCandidate;
            this.sidePushupTrackingFrame = sidePushupTrackingFrame;
            this.frontPushupCandidate = frontPushupCandidate;
            this.frontPushupTrackingFrame = frontPushupTrackingFrame;
            this.hasCorePose = hasCorePose;
        }

        private static PoseMetrics from(Pose pose) {
            float kneeAngle = mirroredAngle(
                    pose,
                    PoseLandmark.LEFT_HIP,
                    PoseLandmark.LEFT_KNEE,
                    PoseLandmark.LEFT_ANKLE,
                    PoseLandmark.RIGHT_HIP,
                    PoseLandmark.RIGHT_KNEE,
                    PoseLandmark.RIGHT_ANKLE
            );
            float hipAngle = mirroredAngle(
                    pose,
                    PoseLandmark.LEFT_SHOULDER,
                    PoseLandmark.LEFT_HIP,
                    PoseLandmark.LEFT_KNEE,
                    PoseLandmark.RIGHT_SHOULDER,
                    PoseLandmark.RIGHT_HIP,
                    PoseLandmark.RIGHT_KNEE
            );
            float elbowAngle = mirroredAngle(
                    pose,
                    PoseLandmark.LEFT_SHOULDER,
                    PoseLandmark.LEFT_ELBOW,
                    PoseLandmark.LEFT_WRIST,
                    PoseLandmark.RIGHT_SHOULDER,
                    PoseLandmark.RIGHT_ELBOW,
                    PoseLandmark.RIGHT_WRIST
            );
            float bodyLineAngle = mirroredAngle(
                    pose,
                    PoseLandmark.LEFT_SHOULDER,
                    PoseLandmark.LEFT_HIP,
                    PoseLandmark.LEFT_ANKLE,
                    PoseLandmark.RIGHT_SHOULDER,
                    PoseLandmark.RIGHT_HIP,
                    PoseLandmark.RIGHT_ANKLE
            );

                PoseLandmark leftShoulder = getVisibleLandmark(pose, PoseLandmark.LEFT_SHOULDER);
                PoseLandmark rightShoulder = getVisibleLandmark(pose, PoseLandmark.RIGHT_SHOULDER);
                PoseLandmark leftHip = getVisibleLandmark(pose, PoseLandmark.LEFT_HIP);
                PoseLandmark rightHip = getVisibleLandmark(pose, PoseLandmark.RIGHT_HIP);
                PoseLandmark leftElbow = getVisibleLandmark(pose, PoseLandmark.LEFT_ELBOW);
                PoseLandmark rightElbow = getVisibleLandmark(pose, PoseLandmark.RIGHT_ELBOW);
                PoseLandmark leftWrist = getVisibleLandmark(pose, PoseLandmark.LEFT_WRIST);
                PoseLandmark rightWrist = getVisibleLandmark(pose, PoseLandmark.RIGHT_WRIST);

            PointScore shoulders = mergePoints(
                    leftShoulder,
                    rightShoulder
            );
            PointScore hips = mergePoints(
                    leftHip,
                    rightHip
                );
                PointScore elbows = mergePoints(
                    leftElbow,
                    rightElbow
                );
                PointScore wrists = mergePoints(
                    leftWrist,
                    rightWrist
            );

            boolean hasCorePose = shoulders.valid && hips.valid;
            float torsoFromHorizontal = Float.NaN;
            if (hasCorePose) {
                torsoFromHorizontal = lineAngleFromHorizontal(shoulders.point, hips.point);
            }

            float torsoFromVertical = Float.isNaN(torsoFromHorizontal)
                    ? Float.NaN
                    : 90f - torsoFromHorizontal;

                boolean hasBothShoulders = leftShoulder != null && rightShoulder != null;
                boolean hasBothHips = leftHip != null && rightHip != null;

                float torsoHeightToShoulderWidth = Float.NaN;
                boolean armsStackedDownward = false;
                if (hasBothShoulders && shoulders.valid && hips.valid) {
                float shoulderWidth = distance(leftShoulder.getPosition(), rightShoulder.getPosition());
                if (shoulderWidth > 1f) {
                    float torsoHeight = Math.abs(hips.point.y - shoulders.point.y);
                    torsoHeightToShoulderWidth = torsoHeight / shoulderWidth;

                    if (elbows.valid && wrists.valid) {
                    float minSegmentDrop = shoulderWidth * FRONT_PUSHUP_ARM_STACK_RATIO;
                    armsStackedDownward = elbows.point.y - shoulders.point.y >= minSegmentDrop
                        && wrists.point.y - elbows.point.y >= minSegmentDrop;
                    }
                }
                }

                boolean sidePushupTrackingFrame = !Float.isNaN(elbowAngle)
                    && !Float.isNaN(bodyLineAngle)
                    && !Float.isNaN(torsoFromHorizontal)
                    && torsoFromHorizontal <= SIDE_PUSHUP_TRACKING_HORIZONTAL_THRESHOLD;

                boolean sidePushupCandidate = sidePushupTrackingFrame
                    && torsoFromHorizontal <= HORIZONTAL_TORSO_THRESHOLD
                    && bodyLineAngle >= PUSHUP_BODY_LINE_ANGLE - 10f;

                boolean frontPushupTrackingFrame = !Float.isNaN(elbowAngle)
                    && hasBothShoulders
                    && hasBothHips
                    && !Float.isNaN(torsoHeightToShoulderWidth)
                    && torsoHeightToShoulderWidth <= FRONT_PUSHUP_TRACKING_TORSO_HEIGHT_RATIO;

                boolean frontPushupCandidate = frontPushupTrackingFrame
                    && torsoHeightToShoulderWidth <= FRONT_PUSHUP_CANDIDATE_TORSO_HEIGHT_RATIO
                    && (armsStackedDownward || torsoHeightToShoulderWidth <= FRONT_PUSHUP_PLANK_TORSO_HEIGHT_RATIO);

            return new PoseMetrics(
                    kneeAngle,
                    hipAngle,
                    elbowAngle,
                    bodyLineAngle,
                    torsoFromHorizontal,
                    torsoFromVertical,
                    torsoHeightToShoulderWidth,
                    sidePushupCandidate,
                    sidePushupTrackingFrame,
                    frontPushupCandidate,
                    frontPushupTrackingFrame,
                    hasCorePose
            );
        }

        private boolean isSquatCandidate() {
            return !Float.isNaN(kneeAngle)
                    && !Float.isNaN(hipAngle)
                    && !Float.isNaN(torsoFromVertical)
                    && torsoFromVertical <= UPRIGHT_TORSO_THRESHOLD;
        }

        private boolean isPushupCandidate() {
            return sidePushupCandidate || frontPushupCandidate;
        }

        private boolean isPushupTrackingFrame() {
            return sidePushupTrackingFrame || frontPushupTrackingFrame;
        }

        private boolean isSidePushupCandidate() {
            return sidePushupCandidate;
        }

        private boolean isSidePushupTrackingFrame() {
            return sidePushupTrackingFrame;
        }

        private boolean isFrontPushupCandidate() {
            return frontPushupCandidate;
        }

        private boolean isFrontPushupTrackingFrame() {
            return frontPushupTrackingFrame;
        }

        private String pushupSummaryText() {
            if (frontPushupCandidate) {
                return "Elbow " + formatAngle(elbowAngle) + " | Torso ratio " + formatRatio(torsoHeightToShoulderWidth);
            }
            return "Elbow " + formatAngle(elbowAngle) + " | Body line " + formatAngle(bodyLineAngle);
        }

        private String summaryText() {
            return "Torso " + formatAngle(torsoFromVertical) + " from vertical | Knee "
                    + formatAngle(kneeAngle) + " | Elbow " + formatAngle(elbowAngle);
        }
    }

    private static float mirroredAngle(
            Pose pose,
            int leftFirst,
            int leftMiddle,
            int leftLast,
            int rightFirst,
            int rightMiddle,
            int rightLast
    ) {
        AngleValue left = angleForTypes(pose, leftFirst, leftMiddle, leftLast);
        AngleValue right = angleForTypes(pose, rightFirst, rightMiddle, rightLast);

        if (left.valid && right.valid) {
            if (Math.abs(left.angle - right.angle) <= 18f) {
                return (left.angle + right.angle) / 2f;
            }
            return left.score >= right.score ? left.angle : right.angle;
        }

        if (left.valid) {
            return left.angle;
        }

        if (right.valid) {
            return right.angle;
        }

        return Float.NaN;
    }

    private static AngleValue angleForTypes(Pose pose, int firstType, int middleType, int lastType) {
        PoseLandmark first = getVisibleLandmark(pose, firstType);
        PoseLandmark middle = getVisibleLandmark(pose, middleType);
        PoseLandmark last = getVisibleLandmark(pose, lastType);

        if (first == null || middle == null || last == null) {
            return AngleValue.invalid();
        }

        return new AngleValue(
                calculateAngle(first.getPosition(), middle.getPosition(), last.getPosition()),
                Math.min(first.getInFrameLikelihood(), Math.min(middle.getInFrameLikelihood(), last.getInFrameLikelihood())),
                true
        );
    }

    private static float calculateAngle(PointF first, PointF middle, PointF last) {
        double radians = Math.atan2(last.y - middle.y, last.x - middle.x)
                - Math.atan2(first.y - middle.y, first.x - middle.x);
        double angle = Math.abs(Math.toDegrees(radians));
        if (angle > 180d) {
            angle = 360d - angle;
        }
        return (float) angle;
    }

    private static PoseLandmark getVisibleLandmark(Pose pose, int type) {
        PoseLandmark landmark = pose.getPoseLandmark(type);
        if (landmark == null || landmark.getInFrameLikelihood() < MIN_LANDMARK_CONFIDENCE) {
            return null;
        }
        return landmark;
    }

    private static PointScore mergePoints(PoseLandmark first, PoseLandmark second) {
        if (first != null && second != null) {
            return new PointScore(
                    new PointF(
                            (first.getPosition().x + second.getPosition().x) / 2f,
                            (first.getPosition().y + second.getPosition().y) / 2f
                    ),
                    Math.min(first.getInFrameLikelihood(), second.getInFrameLikelihood()),
                    true
            );
        }

        if (first != null) {
            return new PointScore(first.getPosition(), first.getInFrameLikelihood(), true);
        }

        if (second != null) {
            return new PointScore(second.getPosition(), second.getInFrameLikelihood(), true);
        }

        return PointScore.invalid();
    }

    private static float lineAngleFromHorizontal(PointF start, PointF end) {
        return (float) Math.toDegrees(Math.atan2(
                Math.abs(end.y - start.y),
                Math.abs(end.x - start.x)
        ));
    }

    private static float distance(PointF first, PointF second) {
        return (float) Math.hypot(
                first.x - second.x,
                first.y - second.y
        );
    }

    private static final class AngleValue {
        private final float angle;
        private final float score;
        private final boolean valid;

        private AngleValue(float angle, float score, boolean valid) {
            this.angle = angle;
            this.score = score;
            this.valid = valid;
        }

        private static AngleValue invalid() {
            return new AngleValue(Float.NaN, 0f, false);
        }
    }

    private static final class PointScore {
        private final PointF point;
        private final float score;
        private final boolean valid;

        private PointScore(PointF point, float score, boolean valid) {
            this.point = point;
            this.score = score;
            this.valid = valid;
        }

        private static PointScore invalid() {
            return new PointScore(new PointF(), 0f, false);
        }
    }
}