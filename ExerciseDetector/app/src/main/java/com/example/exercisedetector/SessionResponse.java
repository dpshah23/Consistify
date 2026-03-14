package com.example.exercisedetector;

import com.google.gson.annotations.SerializedName;

public final class SessionResponse {

    @SerializedName("id")
    public int id;

    @SerializedName("started_at")
    public String startedAt;
}
