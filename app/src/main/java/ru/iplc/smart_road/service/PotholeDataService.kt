package ru.iplc.smart_road.service

import android.Manifest
import android.app.*

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.iplc.smart_road.R
import ru.iplc.smart_road.data.model.PotholeData
import ru.iplc.smart_road.db.AppDatabase
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentLinkedQueue
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class PotholeDataService : Service(), SensorEventListener, LocationListener {


    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var accelSensor: Sensor? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private val batchData = ConcurrentLinkedQueue<PotholeData>()


    private var wakeLock: PowerManager.WakeLock? = null

    private var lastSaveTime = 0L
    private val SAVE_INTERVAL = 5000L // 5 секунд

    private var lastUiBroadcastTs = 0L
    private val UI_BROADCAST_INTERVAL_MS = 50L // 20 Гц



    override fun onCreate() {
        super.onCreate()
        Log.d("PotholeDataService", "Service created")

        acquireWakeLock()
        setupSensors()
        setupLocation()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PotholeDataService::WakeLock"
        ).apply {
            //acquire(10 * 60 * 1000L /*10 minutes*/)
            acquire(0 /*10 minutes*/)
        }
    }

    override fun onBind(intent: Intent?): IBinder?  {
        return null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("PotholeDataService", "Accuracy changed for sensor: ${sensor?.name} to: $accuracy")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d("PotholeDataService", "Location provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d("PotholeDataService", "Location provider disabled: $provider")
    }

    private fun setupSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelSensor?.let {
            //sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

    }

    private fun setupLocation() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000L, // 5 секунд
                    5f,
                    this
                )

                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000L, // 2 секунды
                    5f,
                    this
                )

                Log.d("PotholeDataService", "Location updates started")
            } else {
                Log.w("PotholeDataService", "Нет разрешений на геолокацию")
                // Останавливаем сервис если нет разрешений
                stopSelf()
            }
        } catch (ex: SecurityException) {
            Log.e("PotholeDataService", "Security exception in location setup", ex)
            stopSelf()
        } catch (ex: Exception) {
            Log.e("PotholeDataService", "Error setting up location", ex)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // Важно для автоматического перезапуска
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val currentLocation = lastKnownLocation
            // Отправляем данные акселерометра в реальном времени
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                // сразу компенсируем гравитацию:
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2] - 9.81f

                val now = System.currentTimeMillis()
                if (now - lastUiBroadcastTs >= UI_BROADCAST_INTERVAL_MS) {
                    lastUiBroadcastTs = now
                    sendAccelDataBroadcast(x, y, z, now)
                }
            }

            if (currentLocation != null) {
                val data = when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> PotholeData(
                        timestamp = System.currentTimeMillis(),
                        latitude = currentLocation.latitude,
                        longitude = currentLocation.longitude,
                        accelX = event.values[0],
                        accelY = event.values[1],
                        accelZ = event.values[2],
                        //sensorType = "ACCELEROMETER"
                    )

                    else -> null
                }

                data?.let { potholeData ->
                    batchData.add(potholeData)

                    // Сохраняем либо по достижению лимита, либо по времени
                    val currentTime = System.currentTimeMillis()
                    if (batchData.size >= 100 || currentTime - lastSaveTime > SAVE_INTERVAL) {
                        flushBatch()
                        lastSaveTime = currentTime
                    }
                }
            }
        }
    }

    private fun sendAccelDataBroadcast(x: Float, y: Float, z: Float, ts: Long) {
        val intent = Intent(ACCEL_DATA_ACTION).apply {
            putExtra(EXTRA_ACCEL_X, x)
            putExtra(EXTRA_ACCEL_Y, y)
            putExtra(EXTRA_ACCEL_Z, z)
            putExtra(EXTRA_TIMESTAMP, ts)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun flushBatch() {
        if (batchData.isEmpty()) return

        val batchCopy = batchData.toList()
        batchData.clear()

        serviceScope.launch {
            try {
                AppDatabase.getInstance(applicationContext)
                    .potholeDao()
                    .insertAll(batchCopy)
                Log.d("PotholeDataService", "Saved ${batchCopy.size} records")
            } catch (e: Exception) {
                Log.e("PotholeDataService", "Database error", e)
                // Возвращаем данные обратно в batchData при ошибке
                batchData.addAll(batchCopy)
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        lastKnownLocation = location
    }

    override fun onDestroy() {
        Log.d("PotholeDataService", "Service destroyed")
        // Сохраняем оставшиеся данные
        flushBatch()

        // Освобождаем ресурсы
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        wakeLock?.release()
        serviceJob.cancel()

        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "pothole_service_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pothole Data Collection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Поиск ям включен"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        // Intent для перехода в приложение при клике на уведомление
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Поиск ям")
            .setContentText("Включен")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 30903
        var lastKnownLocation: Location? = null

        // Константы для передачи данных акселерометра
        const val ACCEL_DATA_ACTION = "ru.iplc.smart_road.ACCEL_DATA"
        const val EXTRA_ACCEL_X = "accel_x"
        const val EXTRA_ACCEL_Y = "accel_y"
        const val EXTRA_ACCEL_Z = "accel_z"
        const val EXTRA_TIMESTAMP = "timestamp"

    }

}