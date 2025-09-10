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
import com.yandex.mapkit.map.CameraUpdateReason

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "HomeFragment"
        private const val CHART_UPDATE_INTERVAL = 50L
    }

    private lateinit var mapView: MapView
    private var isStatsExpanded = false

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

    private lateinit var userLocationLayer: com.yandex.mapkit.user_location.UserLocationLayer
    private var followUser = true  // флаг: карта следует за пользователем


    private var lastCameraPosition: Point? = null



    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PotholeDataService.LOCATION_ACTION) {
                val lat = intent.getDoubleExtra(PotholeDataService.EXTRA_LAT, 0.0)
                val lon = intent.getDoubleExtra(PotholeDataService.EXTRA_LON, 0.0)
                if (followUser) {
                    mapView.map.move(
                        CameraPosition(Point(lat, lon), 17f, 0f, 0f)
                    )
                }
            }
        }
    }


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

        // Инициализация карты
        mapView = view.findViewById(R.id.map_view)
        setupMap()


        // Регистрация BroadcastReceiver для GPS
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(locationReceiver, IntentFilter(PotholeDataService.LOCATION_ACTION))


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

    private fun setupMap() {
        // Начальная позиция (Москва)
        mapView.map.move(CameraPosition(Point(55.751574, 37.573856), 11f, 0f, 0f))

        val mapKit = MapKitFactory.getInstance()
        userLocationLayer = mapKit.createUserLocationLayer(mapView.mapWindow)
        userLocationLayer.isVisible = true
        userLocationLayer.setObjectListener(object : UserLocationObjectListener {
            override fun onObjectAdded(view: UserLocationView) {
                view.arrow.setIcon(ImageProvider.fromResource(requireContext(), R.drawable.ic_avatar))
            }
            override fun onObjectRemoved(view: UserLocationView) {}
            override fun onObjectUpdated(view: UserLocationView, event: ObjectEvent) {}
        })

        // Кнопка возврата к текущему местоположению
        val goLocationButton = view?.findViewById<ImageButton>(R.id.go_location)
        goLocationButton?.setOnClickListener {
            followUser = true
            // Сразу центрируем карту на последней известной позиции
            PotholeDataService.lastKnownLocation?.let {
                mapView.map.move(CameraPosition(Point(it.latitude, it.longitude), 17f, 0f, 0f))
            }
        }

        // Слушаем изменение камеры, чтобы отключить слежение при ручном перемещении
        mapView.map.addCameraListener { map, position, reason, finished ->
            // reason показывает причину движения камеры
            if (reason == CameraUpdateReason.GESTURES) {
                followUser = false
            }
        }

        // Слушаем GPS обновления
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val location = PotholeDataService.lastKnownLocation ?: return
                if (followUser) {
                    val azimuth = location.bearing.toFloat() // направление движения в градусах
                    mapView.map.move(
                        CameraPosition(
                            Point(location.latitude, location.longitude),
                            17f,
                            0f,
                            azimuth // вот здесь задаем разворот камеры
                        )
                    )
                }
            }
        }, IntentFilter("LOCATION_UPDATE"))
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
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(locationReceiver)
        _binding = null
        super.onDestroyView()
    }
}