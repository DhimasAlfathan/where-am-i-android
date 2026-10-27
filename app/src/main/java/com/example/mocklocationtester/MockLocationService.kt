package com.example.mocklocationtester

import android.app.*
import android.annotation.SuppressLint
import android.content.Intent
import android.location.provider.ProviderProperties
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

class MockLocationService : Service() {
    companion object {
        var running: Boolean = false
        var status: String = "Mock location is inactive."
        private const val CHANNEL = "mock_location"
        private const val STOP = "stop_mock"
    }
    private lateinit var manager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val installed = mutableSetOf<String>()
    private var latitude = 0.0
    private var longitude = 0.0
    private var failure: String? = null
    private val tick = object : Runnable {
        override fun run() {
            try {
                installed.forEach { provider ->
                    val location = Location(provider).apply {
                        latitude = this@MockLocationService.latitude
                        longitude = this@MockLocationService.longitude
                        accuracy = 3f
                        time = System.currentTimeMillis()
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }
                    // Android automatically marks test-provider locations as mock.
                    manager.setTestProviderLocation(provider, location)
                }
                running = true
                status = "Mock active: $latitude, $longitude"
                handler.postDelayed(this, 1000)
            } catch (e: Exception) { fail(e) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LocationManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Mock location active", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent == null) {
            stopSelf(); return START_NOT_STICKY
        }
        latitude = intent.getDoubleExtra("lat", Double.NaN)
        longitude = intent.getDoubleExtra("lon", Double.NaN)
        try {
            val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            startForeground(1, Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Mock location active")
                .setContentText("Test location: $latitude, $longitude")
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "Stop Mock", stop).build())
                .build())
            require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) { "Invalid coordinates" }
            handler.removeCallbacks(tick)
            // Repeated Start updates coordinates without creating another loop.
            if (installed.isEmpty()) {
                for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                    // These compile-time integers equal the legacy Criteria constants on API 26+.
                    @SuppressLint("InlinedApi")
                    @Suppress("DEPRECATION")
                    manager.addTestProvider(provider, false, false, false, false,
                        false, false, false, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE)
                    installed.add(provider)
                    manager.setTestProviderEnabled(provider, true)
                }
            }
            failure = null
            tick.run()
        } catch (e: Exception) { fail(e) }
        return START_NOT_STICKY
    }
    private fun fail(error: Exception) {
        running = false
        failure = if (error is SecurityException)
            "Permission denied. Select this app in Developer Options > Select mock location app and check location permissions."
        else "Mock failed: ${error.localizedMessage}"
        status = failure!!
        handler.removeCallbacks(tick)
        stopSelf()
    }
    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tick)
        var cleanupFailed = false
        installed.toList().forEach {
            try { manager.removeTestProvider(it) }
            catch (_: Exception) { cleanupFailed = true }
        }
        installed.clear()
        status = if (cleanupFailed) "Cleanup failed. Clear the mock location app selection in Developer Options; restart your device if needed."
            else failure ?: "Mock stopped. Original providers restored; wait for your real location to update."
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
