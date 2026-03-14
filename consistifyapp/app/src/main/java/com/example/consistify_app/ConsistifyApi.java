package com.example.consistify_app;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import com.google.gson.JsonObject;

public interface ConsistifyApi {
    @FormUrlEncoded
    @POST("accounts/login/")
    Call<JsonObject> loginUser(
            @Field("email") String email,
            @Field("password") String password
    );

    @FormUrlEncoded
    @POST("accounts/signup/")
    Call<JsonObject> signupUser(
            @Field("username") String username,
            @Field("email") String email,
            @Field("password") String password
    );

    @GET("accounts/profile/{user_id}/")
    Call<JsonObject> getProfile(@Path("user_id") String userId);

    @GET("social/feed/{page}/")
    Call<JsonObject> getFeed(@Path("page") int page, @Query("user_id") String userId);

    @FormUrlEncoded
    @POST("social/like/")
    Call<JsonObject> likePost(
            @Field("user_id") String userId,
            @Field("post_id") int postId
    );

    @FormUrlEncoded
    @POST("social/create/")
    Call<JsonObject> createPost(
            @Field("user_id") String userId,
            @Field("content") String content,
            @Field("image") String imageBase64
    );

    @FormUrlEncoded
    @POST("api/gamification/process/")
    Call<JsonObject> syncGamification(
            @Field("user_id") String userId,
            @Field("squats") int squats,
            @Field("pushups") int pushups,
            @Field("steps") int steps
    );

    @GET("api/analytics/leaderboard/")
    Call<JsonObject> getLeaderboard(@Query("timeframe") String timeframe);
}
