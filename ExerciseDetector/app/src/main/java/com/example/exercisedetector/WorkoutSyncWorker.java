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

public final class WorkoutSyncWorker extends Worker {

    private static final String UNIQUE_WORK_NAME = "workout-sync-work";
    private static final String UNIQUE_PERIODIC_WORK_NAME = "workout-sync-periodic-work";
    private static final long PERIODIC_SYNC_MINUTES = 15L;

    public WorkoutSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WorkoutSyncWorker.class)
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
                WorkoutSyncWorker.class,
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

        WorkoutDatabaseHelper databaseHelper = new WorkoutDatabaseHelper(context);
        ExerciseApiService apiService = ApiClient.getInstance().create(ExerciseApiService.class);
        List<WorkoutSessionLocalRecord> pendingSessions = databaseHelper.getPendingSessions();

        try {
            for (WorkoutSessionLocalRecord record : pendingSessions) {
                Long remoteId = record.remoteId;
                if (remoteId == null) {
                    Response<SessionResponse> createResponse = apiService
                            .startSession(new SessionStartRequest())
                            .execute();
                    if (!createResponse.isSuccessful() || createResponse.body() == null) {
                        return Result.retry();
                    }
                    remoteId = (long) createResponse.body().id;
                    databaseHelper.updateRemoteId(record.localId, remoteId);
                }

                List<WorkoutRepLocalRecord> pendingReps = databaseHelper.getPendingRepsForSession(record.localId);
                for (WorkoutRepLocalRecord rep : pendingReps) {
                    Response<Void> repResponse = apiService.postRep(
                            new RepRequest(remoteId, rep.exerciseType, rep.repNumber)
                    ).execute();
                    if (!repResponse.isSuccessful()) {
                        return Result.retry();
                    }
                    databaseHelper.markRepSynced(rep.localId);
                }

                String endedAt = record.endedAtMs == null
                        ? null
                        : formatIsoTimestamp(record.endedAtMs);

                Response<Void> updateResponse = apiService.updateSession(
                        remoteId,
                        new SessionUpdateRequest(
                                record.squatCount,
                                record.pushupCount,
                                record.durationSeconds,
                                endedAt
                        )
                ).execute();

                if (!updateResponse.isSuccessful()) {
                    return Result.retry();
                }

                databaseHelper.markSessionSynced(record.localId);
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