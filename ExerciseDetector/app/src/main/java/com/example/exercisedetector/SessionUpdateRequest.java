package com.example.exercisedetector;

import com.google.gson.annotations.SerializedName;

public final class SessionUpdateRequest {

    @SerializedName("squat_count")
    public final int squatCount;

    @SerializedName("pushup_count")
    public final int pushupCount;

    @SerializedName("duration_seconds")
    public final int durationSeconds;

    @SerializedName("ended_at")
    public final String endedAt;

    public SessionUpdateRequest(int squatCount, int pushupCount, int durationSeconds) {
        this(squatCount, pushupCount, durationSeconds, null);
    }

    public SessionUpdateRequest(int squatCount,
                                int pushupCount,
                                int durationSeconds,
                                String endedAt) {
        this.squatCount = squatCount;
        this.pushupCount = pushupCount;
        this.durationSeconds = durationSeconds;
        this.endedAt = endedAt;
    }
}
