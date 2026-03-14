package com.example.exercisedetector;

import com.google.gson.annotations.SerializedName;

public final class FitnessSessionResponse {

    @SerializedName("id")
    public long id;

    @SerializedName("started_at")
    public String startedAt;

    @SerializedName("ended_at")
    public String endedAt;

    @SerializedName("total_steps")
    public int totalSteps;

    @SerializedName("distance_meters")
    public float distanceMeters;

    @SerializedName("avg_speed_kmh")
    public float avgSpeedKmh;

    @SerializedName("avg_heart_rate")
    public int avgHeartRate;

    @SerializedName("calories_burned")
    public int caloriesBurned;

    @SerializedName("duration_seconds")
    public int durationSeconds;

    @SerializedName("activity_type")
    public String activityType;
}