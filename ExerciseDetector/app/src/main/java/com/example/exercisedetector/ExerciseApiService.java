package com.example.exercisedetector;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ExerciseApiService {

    /** Create a new workout session. Returns the session with its generated id. */
    @POST("sessions/")
    Call<SessionResponse> startSession(@Body SessionStartRequest body);

    /** Finalize a session — set total counts and duration. */
    @PATCH("sessions/{id}/")
    Call<Void> updateSession(@Path("id") long sessionId, @Body SessionUpdateRequest body);

    /** Log a single rep in real time. */
    @POST("reps/")
    Call<Void> postRep(@Body RepRequest body);

    // ── Fitness / steps endpoints ─────────────────────────────────────────

    /** Start a new fitness session (steps / heart rate / speed). */
    @POST("fitness/")
    Call<SessionResponse> startFitnessSession(@Body FitnessSessionStartRequest body);

    /** Incrementally update or finalise a fitness session. */
    @PATCH("fitness/{id}/")
    Call<Void> updateFitnessSession(@Path("id") long sessionId,
                                    @Body FitnessSessionUpdateRequest body);

    /** Read persisted fitness sessions for dashboard insights/history. */
    @GET("fitness/")
    Call<java.util.List<FitnessSessionResponse>> listFitnessSessions();
}
