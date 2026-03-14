package com.example.exercisedetector;

import com.google.gson.annotations.SerializedName;

public final class RepRequest {

    @SerializedName("session")
    public final long sessionId;

    @SerializedName("exercise_type")
    public final String exerciseType;

    @SerializedName("rep_number")
    public final int repNumber;

    public RepRequest(long sessionId, String exerciseType, int repNumber) {
        this.sessionId = sessionId;
        this.exerciseType = exerciseType;
        this.repNumber = repNumber;
    }
}
