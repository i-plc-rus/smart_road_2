
package ru.iplc.smart_road.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.card.MaterialCardView
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import ru.iplc.smart_road.R
import ru.iplc.smart_road.databinding.FragmentHomeBinding
import ru.iplc.smart_road.service.PotholeDataService
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.util.concurrent.ConcurrentLinkedQueue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ru.iplc.smart_road.SmartRoadApp
import ru.iplc.smart_road.data.remote.ApiService
import ru.iplc.smart_road.ui.view.StatsItemView
import kotlin.math.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "HomeFragment"
        private const val INITIAL_ZOOM = 15f
        private const val FOLLOW_ZOOM = 17f
        private const val LOCATION_TIMEOUT = 20000L
        private val INVALID_POINT = Point(0.0, 0.0)
        private const val CHART_UPDATE_INTERVAL = 50L
        private const val MAX_VISIBLE_POINTS = 200
        private const val MAX_POINTS_PER_FRAME = 10
        private const val MAX_QUEUE_SIZE = 500
        private const val MIN_MOVEMENT_DISTANCE = 2.0 // Минимальное расстояние для обновления позиции (метры)
        private const val BEARING_SMOOTHING_FACTOR = 0.2f // Коэффициент сглаживания направления
        private const val ANIMATION_DURATION = 0.5f // Длительность анимации в секундах
    }

    private lateinit var mapView: MapView
    private lateinit var userLocationLayer: com.yandex.mapkit.user_location.UserLocationLayer
    //private var followUser = true
    private var waitingForLocation = true
    private var isStatsExpanded = false
    private var previousPosition: Point? = null
    private var currentBearing: Float = 0f
//    private var isUserInteracting = false
    private var lastUpdateTime: Long = 0
    private val MIN_UPDATE_INTERVAL = 1000L // Минимальный интервал обновления (мс)



    private lateinit var accelChart: LineChart
    private lateinit var xData: LineDataSet
    private lateinit var yData: LineDataSet
    private lateinit var zData: LineDataSet
    private var chartUpdateRunnable: Runnable? = null
    private var timeCounter = 0f
    private lateinit var accelReceiver: BroadcastReceiver
    private data class AccelPoint(val x: Float, val y: Float, val z: Float, val ts: Long)
    private val accelDataQueue = ConcurrentLinkedQueue<AccelPoint>()
    private var startTimestamp = 0L
    private val WINDOW_SIZE_SEC = 15f
    private var fakeDataAdded = false

    private val apiService: ApiService by lazy {
        (requireContext().applicationContext as SmartRoadApp).apiService
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "Fragment initialized")

        initMapView(view)
        initLocationLayer()
        setupButtons(view)
        setupStatsPanel(view)
        setupTelemetryControls()
        setupTelemetryButton()

        accelChart = view.findViewById(R.id.accel_chart)
        setupChart()
        addFakeInitialData()
        startChartUpdates()
        observeAccelData()
        loadPatternStats(view)
    }

    private fun initMapView(view: View) {
        mapView = view.findViewById(R.id.map_view)

        applyMapTheme()

        mapView.mapWindow.map.addInputListener(object : InputListener {
            override fun onMapTap(map: Map, point: Point) {}
            override fun onMapLongTap(map: Map, point: Point) {}
        })

        Log.d(TAG, "Map initialized without default position")
    }


    private fun moveToPosition(position: Point, zoom: Float, source: String) {
        Log.d(TAG, "Moving to valid position (from $source): (${position.latitude}, ${position.longitude}), zoom: $zoom")
        mapView.mapWindow.map.move(
            CameraPosition(position, zoom, 0f, 0f)
        )
    }

    private val locationListener = object : UserLocationObjectListener {
        override fun onObjectAdded(userLocationView: UserLocationView) {
            Log.d(TAG, "Location object added")
            userLocationView.arrow.setIcon(
                ImageProvider.fromResource(requireContext(), R.drawable.user_arrow)
            )
            // ⚡ тут НЕ вызываем checkPosition, потому что почти всегда (0,0)
        }

        override fun onObjectRemoved(userLocationView: UserLocationView) {
            Log.d(TAG, "Location object removed")
        }

        override fun onObjectUpdated(userLocationView: UserLocationView, event: ObjectEvent) {
            val pos = userLocationView.arrow.geometry ?: userLocationView.pin?.geometry
            if (pos != null && pos.isValidPosition()) {
                checkPosition(pos, "onObjectUpdated")
            } else {
                Log.d(TAG, "onObjectUpdated invalid pos: $pos")
                // fallback: userLocationLayer.cameraPosition()
                val fallback = userLocationLayer.cameraPosition()?.target
                if (fallback != null && fallback.isValidPosition()) {
                    checkPosition(fallback, "cameraPosition")
                }
            }
        }
    }


    private fun smoothBearing(current: Float, new: Float): Float {
        // Нормализуем углы
        var normalizedCurrent = current % 360
        var normalizedNew = new % 360

        // Корректируем разрыв на 360 градусов
        if (normalizedCurrent - normalizedNew > 180) {
            normalizedNew += 360
        } else if (normalizedNew - normalizedCurrent > 180) {
            normalizedCurrent += 360
        }

        // Применяем сглаживание
        return (normalizedCurrent * (1 - BEARING_SMOOTHING_FACTOR) + normalizedNew * BEARING_SMOOTHING_FACTOR) % 360
    }

    private fun updateCamera(position: Point, direction: Float) {
        mapView.mapWindow.map.move(
            CameraPosition(position, FOLLOW_ZOOM, direction, 0f),
            com.yandex.mapkit.Animation(com.yandex.mapkit.Animation.Type.SMOOTH, ANIMATION_DURATION),
            null
        )
    }



    private fun calculateBearing(from: Point, to: Point): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon2 = Math.toRadians(to.longitude)

        val deltaLon = lon2 - lon1
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360) % 360 // Нормализуем к диапазону 0-360
    }

    private fun calculateDistance(from: Point, to: Point): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val lon1 = Math.toRadians(from.longitude)
        val lon2 = Math.toRadians(to.longitude)

        val deltaLat = lat2 - lat1
        val deltaLon = lon2 - lon1

        val a = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun setupButtons(view: View) {
        view.findViewById<ImageButton>(R.id.go_location).setOnClickListener {
            val target = userLocationLayer.cameraPosition()?.target
            if (target != null && target.isValidPosition()) {
                val currentCamera = mapView.mapWindow.map.cameraPosition
                mapView.mapWindow.map.move(
                    CameraPosition(target, FOLLOW_ZOOM, currentCamera.azimuth, 0f)
                )
                Toast.makeText(requireContext(), "Центрирование на местоположении", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Manual centering on location: $target")
                // Безопасное обновление статуса
//                binding.locationStatus?.text = "Центрирование на местоположении"
            } else {
                Toast.makeText(requireContext(), "Местоположение не определено", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Center button pressed - no valid location")
                // Безопасное обновление статуса
                binding.locationStatus?.text = "Местоположение не определено"
                binding.locationProgress.visibility = View.VISIBLE
                // Перезапускаем таймаут
                view.removeCallbacks(locationTimeoutRunnable)
                view.postDelayed(locationTimeoutRunnable, LOCATION_TIMEOUT)
            }
        }
    }


    private fun initLocationLayer() {
        val mapKit = MapKitFactory.getInstance()
        userLocationLayer = mapKit.createUserLocationLayer(mapView.mapWindow).apply {
            isVisible = true
            isHeadingModeActive = true
            setObjectListener(locationListener)
        }
        view?.postDelayed(locationTimeoutRunnable, LOCATION_TIMEOUT)
        Log.d(TAG, "Location layer initialized, waiting for updates...")
    }



    private val locationTimeoutRunnable = Runnable {
        if (waitingForLocation) {
            Log.d(TAG, "Location timeout reached")
            binding.locationProgress.visibility = View.GONE
            waitingForLocation = false
            // Безопасное обновление статуса
            binding.locationStatus?.text = "Не удалось определить местоположение"
            Toast.makeText(requireContext(), "Не удалось определить местоположение", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPosition(position: Point?, source: String) {
        if (position != null && position.isValidPosition() && waitingForLocation) {
            Log.d(TAG, "First valid position from $source: $position")
            moveToPosition(position, INITIAL_ZOOM, source)
            waitingForLocation = false
            binding.locationProgress.visibility = View.GONE
            view?.removeCallbacks(locationTimeoutRunnable)
        }
    }


    // Улучшенная проверка валидности позиции
    private fun Point.isValidPosition(): Boolean {
        return latitude != 0.0 &&
                longitude != 0.0 &&
                abs(latitude) <= 90 &&
                abs(longitude) <= 180
    }







    private fun isSignificantMovement(from: Point, to: Point): Boolean {
        val distance = calculateDistance(from, to)
        Log.d(TAG, "Distance between points: $distance meters")
        return distance > 1.0
    }





    private fun setupStatsPanel(view: View) {
        val statsCard = view.findViewById<MaterialCardView>(R.id.stats_card)
        val statsHeader = view.findViewById<View>(R.id.stats_header)
        val statsContent = view.findViewById<View>(R.id.stats_content)
        val expandArrow = view.findViewById<ImageView>(R.id.expand_arrow)

        statsHeader.setOnClickListener {
            isStatsExpanded = !isStatsExpanded
            toggleStatsPanel(statsContent, expandArrow, isStatsExpanded)
        }

        statsContent.visibility = View.GONE
        expandArrow.setImageResource(R.drawable.ic_arrow_down)
    }

    private fun toggleStatsPanel(content: View, arrow: ImageView, isExpanded: Boolean) {
        if (isExpanded) {
            content.visibility = View.VISIBLE
            arrow.setImageResource(R.drawable.ic_arrow_up)
            content.animate().alpha(1f).setDuration(200).start()
        } else {
            content.animate().alpha(0f).setDuration(200).withEndAction {
                content.visibility = View.GONE
            }.start()
            arrow.setImageResource(R.drawable.ic_arrow_down)
        }
    }

    private fun applyMapTheme() {
        val isDarkTheme = isDarkTheme()
        mapView.map.isNightModeEnabled = isDarkTheme
    }

    private fun isDarkTheme(): Boolean {
        val flags = requireContext().resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return flags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun setupTelemetryButton() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isTelemetryOn = prefs.getBoolean("telemetry_enabled", false)
        binding.telemetryToggle.isSelected = isTelemetryOn
        if (isTelemetryOn) {
            startTelemetryService(requireContext())
        }
        binding.telemetryToggle.setOnClickListener {
            val newState = !binding.telemetryToggle.isSelected
            binding.telemetryToggle.isSelected = newState
            prefs.edit().putBoolean("telemetry_enabled", newState).apply()
            if (newState) {
                startTelemetryService(requireContext())
            } else {
                stopTelemetryService(requireContext())
            }
        }
    }

    private fun startTelemetryService(context: Context) {
        val intent = Intent(context, PotholeDataService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Toast.makeText(context, "Сбор данных запущен", Toast.LENGTH_SHORT).show()
    }

    private fun stopTelemetryService(context: Context) {
        val intent = Intent(context, PotholeDataService::class.java)
        context.stopService(intent)
        Toast.makeText(context, "Сбор данных остановлен", Toast.LENGTH_SHORT).show()
    }

    private fun setupTelemetryControls() {
        // Placeholder for telemetry controls
    }

    private fun setupChart() {
        val isDarkTheme = isDarkTheme()
        val textColor = if (isDarkTheme) ContextCompat.getColor(requireContext(), R.color.white) else ContextCompat.getColor(requireContext(), R.color.black)
        val gridColor = if (isDarkTheme) ContextCompat.getColor(requireContext(), R.color.gray_600) else ContextCompat.getColor(requireContext(), R.color.gray_300)
        val backgroundColor = if (isDarkTheme) ContextCompat.getColor(requireContext(), R.color.surface_dark) else ContextCompat.getColor(requireContext(), R.color.surface_light)

        val xAxis = accelChart.xAxis
        xAxis.isEnabled = false

        val yAxisLeft = accelChart.axisLeft
        yAxisLeft.isEnabled = true
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.axisMinimum = -20f
        yAxisLeft.axisMaximum = 20f
        yAxisLeft.granularity = 5f
        yAxisLeft.textColor = textColor
        yAxisLeft.gridColor = gridColor
        yAxisLeft.axisLineColor = gridColor

        val yAxisRight = accelChart.axisRight
        yAxisRight.isEnabled = false

        xData = LineDataSet(mutableListOf(), "X").apply {
            color = ContextCompat.getColor(requireContext(), R.color.green)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }
        yData = LineDataSet(mutableListOf(), "Y").apply {
            color = ContextCompat.getColor(requireContext(), R.color.yellow)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }
        zData = LineDataSet(mutableListOf(), "Z").apply {
            color = ContextCompat.getColor(requireContext(), R.color.red)
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        val dataSets = mutableListOf<ILineDataSet>(xData, yData, zData)
        accelChart.data = LineData(dataSets)
        accelChart.description.isEnabled = false
        accelChart.legend.isEnabled = false
        accelChart.setTouchEnabled(false)
        accelChart.setPinchZoom(false)
        accelChart.setScaleEnabled(false)
        accelChart.setDrawGridBackground(false)
        accelChart.setBackgroundColor(backgroundColor)
    }

    private fun addFakeInitialData() {
        if (fakeDataAdded) return
        fakeDataAdded = true
        val data: LineData = accelChart.data ?: return
        val now = System.currentTimeMillis()
        startTimestamp = now - (WINDOW_SIZE_SEC * 1000L).toLong()
        val stepMs = 500L
        var t = startTimestamp
        while (t <= now) {
            val timeSec = (t - startTimestamp) / 1000f
            data.addEntry(Entry(timeSec, 0f), 0)
            data.addEntry(Entry(timeSec, 0f), 1)
            data.addEntry(Entry(timeSec, 0f), 2)
            t += stepMs
        }
        data.notifyDataChanged()
        accelChart.notifyDataSetChanged()
        accelChart.setVisibleXRangeMaximum(WINDOW_SIZE_SEC)
        accelChart.moveViewToX(WINDOW_SIZE_SEC)
        accelChart.invalidate()
    }

    private fun startChartUpdates() {
        chartUpdateRunnable = object : Runnable {
            override fun run() {
                processAccelDataQueue()
                view?.postDelayed(this, CHART_UPDATE_INTERVAL)
            }
        }
        chartUpdateRunnable?.let { view?.post(it) }
    }

    private fun processAccelDataQueue() {
        val data = accelChart.data ?: return
        var updated = false
        var latestTimeSec = 0f
        while (true) {
            val point = accelDataQueue.poll() ?: break
            if (startTimestamp == 0L) startTimestamp = point.ts
            val timeSec = (point.ts - startTimestamp) / 1000f
            latestTimeSec = timeSec
            data.addEntry(Entry(timeSec, point.x), 0)
            data.addEntry(Entry(timeSec, point.y), 1)
            data.addEntry(Entry(timeSec, point.z), 2)
            updated = true
        }
        if (updated) {
            for (i in 0 until data.dataSetCount) {
                val set = data.getDataSetByIndex(i)
                while (set.entryCount > 0 && latestTimeSec - set.getEntryForIndex(0).x > WINDOW_SIZE_SEC) {
                    set.removeFirst()
                }
            }
            data.notifyDataChanged()
            accelChart.notifyDataSetChanged()
            accelChart.setVisibleXRangeMaximum(WINDOW_SIZE_SEC)
            accelChart.moveViewToX(latestTimeSec)
            accelChart.invalidate()
        }
    }

    private fun observeAccelData() {
        accelReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == PotholeDataService.ACCEL_DATA_ACTION) {
                    val x = intent.getFloatExtra(PotholeDataService.EXTRA_ACCEL_X, 0f)
                    val y = intent.getFloatExtra(PotholeDataService.EXTRA_ACCEL_Y, 0f)
                    val z = intent.getFloatExtra(PotholeDataService.EXTRA_ACCEL_Z, 0f)
                    val ts = intent.getLongExtra(PotholeDataService.EXTRA_TIMESTAMP, System.currentTimeMillis())
                    accelDataQueue.add(AccelPoint(x, y, z, ts))
                }
            }
        }
        val filter = IntentFilter(PotholeDataService.ACCEL_DATA_ACTION)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(accelReceiver, filter)
    }

    private fun updateChartColors() {
        val isDarkTheme = isDarkTheme()
        val textColor = if (isDarkTheme) ContextCompat.getColor(requireContext(), R.color.white) else ContextCompat.getColor(requireContext(), R.color.black)
        val gridColor = if (isDarkTheme) ContextCompat.getColor(requireContext(), R.color.gray_600) else ContextCompat.getColor(requireContext(), R.color.gray_300)
        val backgroundColor = if (isDarkTheme) ContextCompat.getColor(requireContext(), R.color.surface_dark) else ContextCompat.getColor(requireContext(), R.color.surface_light)
        accelChart.axisLeft.textColor = textColor
        accelChart.axisLeft.gridColor = gridColor
        accelChart.axisLeft.axisLineColor = gridColor
        accelChart.setBackgroundColor(backgroundColor)
        accelChart.invalidate()
    }

    private fun loadPatternStats(view: View) {
        val potholesView = view.findViewById<StatsItemView>(R.id.stats_potholes)
        val bumpsView = view.findViewById<StatsItemView>(R.id.stats_speed_bumps)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getPatternStat()
                if (response.isSuccessful) {
                    val stats = response.body().orEmpty()
                    val potholes = stats.find { it.type == "joint" }?.c ?: 0
                    val speedBumps = stats.find { it.type == "speed_bump" }?.c ?: 0
                    potholesView?.setStatValue(potholes.toString())
                    bumpsView?.setStatValue(speedBumps.toString())
                    Log.d(TAG, "Stats loaded: potholes=$potholes, speedBumps=$speedBumps")
                } else {
                    Log.w(TAG, "getPatternStat failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stats", e)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
        Log.d(TAG, "Fragment started")
    }

    override fun onStop() {
        view?.removeCallbacks(locationTimeoutRunnable)
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
        Log.d(TAG, "Fragment stopped")
    }

    override fun onResume() {
        super.onResume()
        updateChartColors()
        applyMapTheme()
    }

    override fun onDestroyView() {
        chartUpdateRunnable?.let { view?.removeCallbacks(it) }
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(accelReceiver)
        super.onDestroyView()
    }
}