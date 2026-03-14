package com.example.exercisedetector;

import com.google.gson.annotations.SerializedName;

public final class FitnessSessionUpdateRequest {

    @SerializedName("total_steps")
    public final int totalSteps;

    @SerializedName("distance_meters")
    public final float distanceMeters;

    @SerializedName("avg_speed_kmh")
    public final float avgSpeedKmh;

    @SerializedName("avg_heart_rate")
    public final int avgHeartRate;

    @SerializedName("calories_burned")
    public final int caloriesBurned;

    @SerializedName("duration_seconds")
    public final int durationSeconds;

    @SerializedName("activity_type")
    public final String activityType;

    @SerializedName("ended_at")
    public final String endedAt;

    public FitnessSessionUpdateRequest(int totalSteps, float distanceMeters, float avgSpeedKmh,
                                       int avgHeartRate, int caloriesBurned, int durationSeconds,
                                       String activityType) {
        this(totalSteps, distanceMeters, avgSpeedKmh, avgHeartRate, caloriesBurned,
                durationSeconds, activityType, null);
    }

    public FitnessSessionUpdateRequest(int totalSteps, float distanceMeters, float avgSpeedKmh,
                                       int avgHeartRate, int caloriesBurned, int durationSeconds,
                                       String activityType, String endedAt) {
        this.totalSteps      = totalSteps;
        this.distanceMeters  = distanceMeters;
        this.avgSpeedKmh     = avgSpeedKmh;
        this.avgHeartRate    = avgHeartRate;
        this.caloriesBurned  = caloriesBurned;
        this.durationSeconds = durationSeconds;
        this.activityType    = activityType;
        this.endedAt         = endedAt;
    }
}
