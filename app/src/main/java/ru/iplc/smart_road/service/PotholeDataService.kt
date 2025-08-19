package ru.iplc.smart_road.service

import android.Manifest
import android.content.pm.PackageManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.iplc.smart_road.R
import ru.iplc.smart_road.data.model.PotholeData
import ru.iplc.smart_road.db.AppDatabase
import java.util.concurrent.CopyOnWriteArrayList

class PotholeDataService : Service(), SensorEventListener, LocationListener {

    private val binder = LocalBinder()
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var accelSensor: Sensor? = null

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private val batchData = CopyOnWriteArrayList<PotholeData>()




    override fun onCreate() {
        super.onCreate()
        Log.d("PotholeDataService", "Start")
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Регистрируем акселерометр на максимальной частоте
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelSensor?.let {
            //sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Регистрируем GPS
        try {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
            } else {
                Log.w("PotholeDataService", "Нет разрешений на геолокацию")
            }
        } catch (ex: SecurityException) {
            ex.printStackTrace()
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): PotholeDataService = this@PotholeDataService
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val accelData = Triple(event.values[0], event.values[1], event.values[2])
            val currentLocation = lastKnownLocation

            if (currentLocation != null) {
                val data = PotholeData(
                    timestamp = System.currentTimeMillis(),
                    latitude = currentLocation.latitude,
                    longitude = currentLocation.longitude,
                    accelX = accelData.first,
                    accelY = accelData.second,
                    accelZ = accelData.third
                )
                batchData.add(data)
                Log.d("PotholeDataService", "Добавлены данные: $data")


                // Сохраняем пачку в Room
                if (batchData.size >= 50) {
                    Log.d("PotholeDataService", "Пачка достигла 50, сохраняем в базу")
                    flushBatch()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification() // Сделай метод, который создаёт Notification
        startForeground(30903, notification)
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("PotholeDataService", "Приложение смахнули — останавливаем сервис")
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channelId = "pothole_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pothole Data Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Pothole Data Collection")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // свой иконку
            .build()
    }

    private fun flushBatch() {
        val batchCopy = batchData.toList()
        batchData.clear()

        serviceScope.launch {
            AppDatabase.getInstance(applicationContext)
                .potholeDao()
                .insertAll(batchCopy)
            Log.d("PotholeDataService", "Пачка из ${batchCopy.size} записей сохранена в Room")
        }
    }

    override fun onLocationChanged(location: Location) {
        lastKnownLocation = location
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        Log.d("PotholeDataService", "Сервис уничтожен")
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
        serviceJob.cancel()
        batchData.clear()
        super.onDestroy()
    }

    companion object {
        var lastKnownLocation: Location? = null
    }
}