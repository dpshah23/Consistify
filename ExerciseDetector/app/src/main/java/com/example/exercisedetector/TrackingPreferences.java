package com.example.exercisedetector;

import android.content.Context;
import android.content.SharedPreferences;

public final class TrackingPreferences {

    private static final String PREFS = "tracking_prefs";
    private static final String KEY_TRACKING_ACTIVE = "tracking_active";
    private static final String KEY_BATTERY_PROMPT_SHOWN = "battery_prompt_shown";

    private TrackingPreferences() {
    }

    public static boolean isTrackingActive(Context context) {
        return prefs(context).getBoolean(KEY_TRACKING_ACTIVE, false);
    }

    public static void setTrackingActive(Context context, boolean active) {
        prefs(context).edit().putBoolean(KEY_TRACKING_ACTIVE, active).apply();
    }

    public static boolean wasBatteryPromptShown(Context context) {
        return prefs(context).getBoolean(KEY_BATTERY_PROMPT_SHOWN, false);
    }

    public static void setBatteryPromptShown(Context context, boolean shown) {
        prefs(context).edit().putBoolean(KEY_BATTERY_PROMPT_SHOWN, shown).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}