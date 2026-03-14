package com.example.exercisedetector;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

public final class FitnessSyncWorker extends Worker {

    private static final String UNIQUE_WORK_NAME = "fitness-sync-work";
    private static final String UNIQUE_PERIODIC_WORK_NAME = "fitness-sync-periodic-work";
    private static final long PERIODIC_SYNC_MINUTES = 15L;

    public FitnessSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(FitnessSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
                request
        );
    }

        public static void schedulePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        PeriodicWorkRequest periodicRequest = new PeriodicWorkRequest.Builder(
            FitnessSyncWorker.class,
            PERIODIC_SYNC_MINUTES,
            TimeUnit.MINUTES
        ).setConstraints(constraints).build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        );
        }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!NetworkUtils.isInternetAvailable(context)) {
            return Result.retry();
        }

        FitnessDatabaseHelper databaseHelper = new FitnessDatabaseHelper(context);
        ExerciseApiService apiService = ApiClient.getInstance().create(ExerciseApiService.class);
        List<FitnessSessionLocalRecord> pendingSessions = databaseHelper.getPendingSessions();

        try {
            for (FitnessSessionLocalRecord record : pendingSessions) {
                Long remoteId = record.remoteId;
                if (remoteId == null) {
                    Response<SessionResponse> createResponse = apiService
                            .startFitnessSession(new FitnessSessionStartRequest())
                            .execute();
                    if (!createResponse.isSuccessful() || createResponse.body() == null) {
                        return Result.retry();
                    }
                    remoteId = (long) createResponse.body().id;
                    databaseHelper.updateRemoteId(record.localId, remoteId);
                }

                String endedAt = record.endedAtMs == null
                        ? null
                    : formatIsoTimestamp(record.endedAtMs);

                Response<Void> updateResponse = apiService.updateFitnessSession(
                        remoteId,
                        new FitnessSessionUpdateRequest(
                                record.totalSteps,
                                record.distanceMeters,
                                record.avgSpeedKmh,
                                record.avgHeartRate,
                                record.caloriesBurned,
                                record.durationSeconds,
                                record.activityType,
                                endedAt
                        )
                ).execute();

                if (!updateResponse.isSuccessful()) {
                    return Result.retry();
                }

                databaseHelper.markSynced(record.localId);
            }
        } catch (Exception exception) {
            return Result.retry();
        }

        return Result.success();
    }

    private static String formatIsoTimestamp(long timestampMs) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(timestampMs);
    }
}