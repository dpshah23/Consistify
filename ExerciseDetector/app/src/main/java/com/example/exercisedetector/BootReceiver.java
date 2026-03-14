package com.example.exercisedetector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            FitnessSyncWorker.schedulePeriodic(context);
            WorkoutSyncWorker.schedulePeriodic(context);
            if (TrackingPreferences.isTrackingActive(context)) {
                StepTrackingService.start(context);
            }
        }
    }
}