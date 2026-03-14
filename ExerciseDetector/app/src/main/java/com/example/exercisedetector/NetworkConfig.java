package com.example.exercisedetector;

/**
 * Central place to change the Django server URL.
 *
 * Android emulator  → Django on host machine localhost:
 *     BASE_URL = "http://10.0.2.2:8000/api/"
 *
 * Physical device on the same Wi-Fi as your dev machine:
 *     BASE_URL = "http://192.168.X.X:8000/api/"  (replace with your LAN IP)
 */
public final class NetworkConfig {
    public static final String BASE_URL = "http://10.0.2.2:8000/api/";

    private NetworkConfig() {}
}
