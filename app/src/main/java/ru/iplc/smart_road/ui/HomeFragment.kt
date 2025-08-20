package ru.iplc.smart_road.ui

import android.content.Intent
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
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.Fragment
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

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    companion object {
        private const val TAG = "HomeFragment"

        private const val INITIAL_ZOOM = 15f
        private const val FOLLOW_ZOOM = 17f
        private const val LOCATION_TIMEOUT = 5000L
        private val INVALID_POINT = Point(0.0, 0.0)
    }

    private lateinit var mapView: MapView
    private lateinit var userLocationLayer: com.yandex.mapkit.user_location.UserLocationLayer
    private var followUser = true
    private var waitingForLocation = true
    private var isStatsExpanded = false


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
        binding.telemetryToggle.isSelected = true
    }

    private fun setupTelemetryButton() {
        binding.telemetryToggle.setOnClickListener {
            binding.telemetryToggle.isSelected = !binding.telemetryToggle.isSelected

            if (binding.telemetryToggle.isSelected) {
                // Включено
                (requireActivity() as MainActivity).toggleService(true)
                Toast.makeText(requireContext(), "Сбор данных запущен", Toast.LENGTH_SHORT).show()
            } else {
                // Выключено
                (requireActivity() as MainActivity).toggleService(false)
                Toast.makeText(requireContext(), "Сбор данных остановлен", Toast.LENGTH_SHORT).show()
            }
        }
        updateButtonState()
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

        view.findViewById<Button>(R.id.go_location).setOnClickListener {
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
}