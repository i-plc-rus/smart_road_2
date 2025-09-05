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
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.card.MaterialCardView
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import ru.iplc.smart_road.MainActivity
import ru.iplc.smart_road.R
import ru.iplc.smart_road.databinding.FragmentHomeBinding
import ru.iplc.smart_road.service.PotholeDataService
import ru.iplc.smart_road.utils.schedulePotholeUpload
import kotlin.math.abs

import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
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


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    companion object {
        private const val TAG = "HomeFragment"

        private const val INITIAL_ZOOM = 15f
        private const val FOLLOW_ZOOM = 17f
        private const val LOCATION_TIMEOUT = 5000L
        private val INVALID_POINT = Point(0.0, 0.0)
        private const val CHART_UPDATE_INTERVAL = 50L

        private const val MAX_VISIBLE_POINTS = 200     // можно чуть увеличить окно
        private const val MAX_POINTS_PER_FRAME = 10    // ограничиваем работу на кадр
        private const val MAX_QUEUE_SIZE = 500         // верхняя граница очереди

    }

    private lateinit var mapView: MapView
    private lateinit var userLocationLayer: com.yandex.mapkit.user_location.UserLocationLayer
    private var followUser = true
    private var waitingForLocation = true
    private var isStatsExpanded = false


    private lateinit var accelChart: LineChart
    private lateinit var xData: LineDataSet
    private lateinit var yData: LineDataSet
    private lateinit var zData: LineDataSet

    //private val accelDataQueue = ConcurrentLinkedQueue<Triple<Float, Float, Float>>()
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
        // Подпишемся на данные от сервиса
        observeAccelData()

        loadPatternStats(view)

    }

    private fun addFakeInitialData() {
        if (fakeDataAdded) return
        fakeDataAdded = true

        // Подготавливаем data
        val data: LineData = accelChart.data ?: return

        // Установим startTimestamp в (now - WINDOW_SIZE_SEC)
        val now = System.currentTimeMillis()
        startTimestamp = now - (WINDOW_SIZE_SEC * 1000L).toLong()

        // Заполним окно точками с шагом 500ms (можно уменьшить/увеличить)
        val stepMs = 500L
        var t = startTimestamp
        while (t <= now) {
            val timeSec = (t - startTimestamp) / 1000f
            // добавляем нулевые точки в каждый набор
            data.addEntry(Entry(timeSec, 0f), 0)
            data.addEntry(Entry(timeSec, 0f), 1)
            data.addEntry(Entry(timeSec, 0f), 2)
            t += stepMs
        }

        data.notifyDataChanged()
        accelChart.notifyDataSetChanged()
        accelChart.setVisibleXRangeMaximum(WINDOW_SIZE_SEC)
        // перемещаем вид к концу окна (в секундах)
        accelChart.moveViewToX(WINDOW_SIZE_SEC)
        accelChart.invalidate()
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



    private fun setupChart() {
        val isDarkTheme = isDarkTheme()

        // Цвета в зависимости от темы
        val textColor = if (isDarkTheme) {
            ContextCompat.getColor(requireContext(), R.color.white)
        } else {
            ContextCompat.getColor(requireContext(), R.color.black)
        }

        val gridColor = if (isDarkTheme) {
            ContextCompat.getColor(requireContext(), R.color.gray_600)
        } else {
            ContextCompat.getColor(requireContext(), R.color.gray_300)
        }

        val backgroundColor = if (isDarkTheme) {
            ContextCompat.getColor(requireContext(), R.color.surface_dark)
        } else {
            ContextCompat.getColor(requireContext(), R.color.surface_light)
        }

        // Ось X
        val xAxis = accelChart.xAxis
        xAxis.isEnabled = false   // убираем подписи снизу

        // Левая ось Y
        val yAxisLeft = accelChart.axisLeft
        yAxisLeft.isEnabled = true   // оставляем подписи
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.axisMinimum = -20f
        yAxisLeft.axisMaximum = 20f
        yAxisLeft.granularity = 5f
        yAxisLeft.textColor = textColor
        yAxisLeft.gridColor = gridColor
        yAxisLeft.axisLineColor = gridColor

        // Правая ось Y
        val yAxisRight = accelChart.axisRight
        yAxisRight.isEnabled = false  // скрываем

        // Данные
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

        // Оформление
        accelChart.description.isEnabled = false
        accelChart.legend.isEnabled = false  // убираем легенду
        accelChart.setTouchEnabled(false)
        accelChart.setPinchZoom(false)
        accelChart.setScaleEnabled(false)
        accelChart.setDrawGridBackground(false)
        accelChart.setBackgroundColor(backgroundColor)
    }

    // Функция для проверки темной темы
    private fun isDarkTheme(): Boolean {
        val flags = requireContext().resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return flags == android.content.res.Configuration.UI_MODE_NIGHT_YES
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
            // Удаляем точки старше 15 секунд
            for (i in 0 until data.dataSetCount) {
                val set = data.getDataSetByIndex(i)
                while (set.entryCount > 0 &&
                    latestTimeSec - set.getEntryForIndex(0).x > WINDOW_SIZE_SEC) {
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




    private fun addAccelEntry(x: Float, y: Float, z: Float) {
        val data = accelChart.data ?: return
        timeCounter += 0.5f // каждые 0.5 сек (как observeAccelData)

        data.addEntry(Entry(timeCounter, x), 0)
        data.addEntry(Entry(timeCounter, y), 1)
        data.addEntry(Entry(timeCounter, z), 2)

        data.notifyDataChanged()
        accelChart.notifyDataSetChanged()
        accelChart.setVisibleXRangeMaximum(20f) // показываем последние 20 точек
        accelChart.moveViewToX(timeCounter)
    }

    private fun setupTelemetryButton() {
        val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isTelemetryOn = prefs.getBoolean("telemetry_enabled", false)

        // Устанавливаем состояние кнопки
        binding.telemetryToggle.isSelected = isTelemetryOn

        // Запуск сервиса только если включено
        if (isTelemetryOn) {
            startTelemetryService(requireContext())
        }

        binding.telemetryToggle.setOnClickListener {
            val newState = !binding.telemetryToggle.isSelected
            binding.telemetryToggle.isSelected = newState

            // Сохраняем состояние
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



    private fun toggleTelemetryService(context: Context, enabled: Boolean) {
        val intent = Intent(context, PotholeDataService::class.java)
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Toast.makeText(context, "Сбор данных запущен", Toast.LENGTH_SHORT).show()
        } else {
            context.stopService(intent)
            Toast.makeText(context, "Сбор данных остановлен", Toast.LENGTH_SHORT).show()
        }
    }



    private fun setupTelemetryControls() {
        binding.telemetryToggle.setOnClickListener {
//            if (TelemetryManager.isTelemetryRunning()){
//                TelemetryManager.stopTelemetryCollection(requireContext())
//                updateButtonState()
//                Toast.makeText(requireContext(), "Сбор данных остановлен", Toast.LENGTH_SHORT).show()
//            }else
//            {
//                TelemetryManager.startTelemetryCollection(requireContext())
//                updateButtonState()
//                Toast.makeText(requireContext(), "Сбор данных запущен", Toast.LENGTH_SHORT).show()
//            }

        }

        /*binding.telemetryToggle.setOnClickListener {
            TelemetryManager.stopTelemetryCollection(requireContext())
            updateButtonState()
            Toast.makeText(requireContext(), "Сбор данных остановлен", Toast.LENGTH_SHORT).show()
        }*/
    }

    private fun updateButtonState() {
        // Обновляем состояние кнопок
        //binding.telemetryToggle.isEnabled = !TelemetryManager.isTelemetryRunning()
        //binding.telemetryToggle.isEnabled = TelemetryManager.isTelemetryRunning()
        //binding.telemetryToggle.isSelected = TelemetryManager.isTelemetryRunning()

//        binding.telemetryToggle.text = if (TelemetryManager.isTelemetryRunning()) {
//            "Статус: Сбор данных активен ✅"
//        } else {
//            "Статус: Сбор данных остановлен ⏸️"
//        }
    }


    private fun setupStatsPanel(view: View) {
        val statsCard = view.findViewById<MaterialCardView>(R.id.stats_card)
        val statsHeader = view.findViewById<View>(R.id.stats_header)
        val statsContent = view.findViewById<View>(R.id.stats_content)
        val expandArrow = view.findViewById<ImageView>(R.id.expand_arrow)

        // Обработчик клика по шапке
        statsHeader.setOnClickListener {
            isStatsExpanded = !isStatsExpanded
            toggleStatsPanel(statsContent, expandArrow, isStatsExpanded)
        }

        // Начальное состояние - свернуто
        statsContent.visibility = View.GONE
        expandArrow.setImageResource(R.drawable.ic_arrow_down)
    }

    private fun toggleStatsPanel(
        content: View,
        arrow: ImageView,
        isExpanded: Boolean
    ) {
        if (isExpanded) {
            content.visibility = View.VISIBLE
            arrow.setImageResource(R.drawable.ic_arrow_up)
            // Анимация разворачивания
            content.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else {
            content.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    content.visibility = View.GONE
                }
                .start()
            arrow.setImageResource(R.drawable.ic_arrow_down)
        }
    }

    private fun initMapView(view: View) {
        mapView = view.findViewById(R.id.map_view)
        val moscow = Point(55.751244, 37.618423)
        mapView.map.move(
            CameraPosition(moscow, INITIAL_ZOOM, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0f),
            null
        )
        applyMapTheme() // ✅ применяем тему к карте
        Log.d(TAG, "Initial map position set to Moscow")
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

    private val locationListener = object : UserLocationObjectListener {
        override fun onObjectAdded(userLocationView: UserLocationView) {
            Log.d(TAG, "Location object added")
            userLocationView.arrow.setIcon(
                ImageProvider.fromResource(requireContext(), R.drawable.user_arrow)
            )
            checkPosition(userLocationView.arrow.geometry, "onObjectAdded")
        }

        override fun onObjectRemoved(userLocationView: UserLocationView) {
            Log.d(TAG, "Location object removed")
        }

        override fun onObjectUpdated(userLocationView: UserLocationView, event: ObjectEvent) {
            val position = userLocationView.arrow.geometry
            val direction = userLocationView.arrow.direction ?: 0f
            Log.d(TAG, "Location updated: ${position?.let { "(${it.latitude}, ${it.longitude})" } ?: "null"}, dir: $direction")

            if (position != null && position.isValid()) {
                if (waitingForLocation) {
                    moveToPosition(position, INITIAL_ZOOM, "Initial position")
                    waitingForLocation = false
                } else if (followUser) {
                    updateCamera(position, direction)
                }
            }
        }
    }

    private val locationTimeoutRunnable = Runnable {
        if (waitingForLocation) {
            Log.d(TAG, "Location timeout reached")
            val lastPosition = userLocationLayer.cameraPosition()?.target
            if (lastPosition != null && lastPosition.isValid()) {
                moveToPosition(lastPosition, INITIAL_ZOOM, "Timeout position")
            } else {
                Log.w(TAG, "No valid position available after timeout")
            }
            waitingForLocation = false
        }
    }

    private fun checkPosition(position: Point?, source: String) {
        if (position != null && position.isValid() && waitingForLocation) {
            moveToPosition(position, INITIAL_ZOOM, source)
            waitingForLocation = false
        }
    }

    private fun Point.isValid(): Boolean {
        return this != INVALID_POINT &&
                latitude != 0.0 &&
                longitude != 0.0 &&
                abs(latitude) <= 90 &&
                abs(longitude) <= 180
    }

    private fun moveToPosition(position: Point, zoom: Float, source: String) {
        Log.d(TAG, "Moving to valid position (from $source): (${position.latitude}, ${position.longitude}), zoom: $zoom")
        mapView.map.move(
            CameraPosition(position, zoom, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0.5f),
            null
        )
    }

    private fun updateCamera(position: Point, direction: Float) {
        mapView.map.move(
            CameraPosition(
                position,
                mapView.map.cameraPosition.zoom,
                direction,
                0f
            ),
            Animation(Animation.Type.SMOOTH, 0.3f),
            null
        )
    }

    private fun setupButtons(view: View) {
        view.findViewById<ImageButton>(R.id.burger_icon).setOnClickListener {
            (activity as? MainActivity)?.openDrawer()
        }

        view.findViewById<ImageButton>(R.id.go_location).setOnClickListener {
            val target = userLocationLayer.cameraPosition()?.target
            if (target != null && target.isValid()) {
                moveToPosition(target, FOLLOW_ZOOM, "Manual center")
            } else {
                Toast.makeText(requireContext(), "Местоположение не определено", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Center button pressed - no valid location")
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
        applyMapTheme() // ✅ обновляем тему карты при возврате
    }

    private fun applyMapTheme() {
        val isDarkTheme = isDarkTheme()
        mapView.map.isNightModeEnabled = isDarkTheme
    }

    private fun updateChartColors() {
        val isDarkTheme = isDarkTheme()

        val textColor = if (isDarkTheme) {
            ContextCompat.getColor(requireContext(), R.color.white)
        } else {
            ContextCompat.getColor(requireContext(), R.color.black)
        }

        val gridColor = if (isDarkTheme) {
            ContextCompat.getColor(requireContext(), R.color.gray_600)
        } else {
            ContextCompat.getColor(requireContext(), R.color.gray_300)
        }

        val backgroundColor = if (isDarkTheme) {
            ContextCompat.getColor(requireContext(), R.color.surface_dark)
        } else {
            ContextCompat.getColor(requireContext(), R.color.surface_light)
        }

        // Обновляем цвета осей
        accelChart.axisLeft.textColor = textColor
        accelChart.axisLeft.gridColor = gridColor
        accelChart.axisLeft.axisLineColor = gridColor

        // Обновляем фон
        accelChart.setBackgroundColor(backgroundColor)
        accelChart.invalidate() // Перерисовываем график
    }

    private fun loadPatternStats(view: View) {
        val potholesView = view.findViewById<StatsItemView>(R.id.stats_potholes)
        val bumpsView = view.findViewById<StatsItemView>(R.id.stats_speed_bumps)

        // ⚡ используем lifecycleScope для корутин
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = apiService.getPatternStat() // ApiClient твой Retrofit
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



    override fun onDestroyView() {
        chartUpdateRunnable?.let { view?.removeCallbacks(it) }
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(accelReceiver)
        super.onDestroyView()
    }

}