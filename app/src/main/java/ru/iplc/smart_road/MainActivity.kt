package ru.iplc.smart_road

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.yandex.mapkit.MapKitFactory
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupWithNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.navigation.NavigationView
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch
import ru.iplc.smart_road.data.local.TokenManager
import ru.iplc.smart_road.databinding.ActivityMainBinding
import ru.iplc.smart_road.service.PotholeDataService
import ru.iplc.smart_road.utils.schedulePotholeUpload
import ru.iplc.smart_road.worker.PotholeUploadWorker
import android.provider.Settings
import android.net.Uri

//import org.koin.android.ext.android.inject
import io.sentry.Sentry



class MainActivity : AppCompatActivity() {
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    private lateinit var navController: NavController
    private lateinit var tokenManager: TokenManager
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    // waiting for view to draw to better represent a captured error with a screenshot
    findViewById<android.view.View>(android.R.id.content).viewTreeObserver.addOnGlobalLayoutListener {
      try {
        throw Exception("This app uses Sentry! :)")
      } catch (e: Exception) {
        Sentry.captureException(e)
      }
    }

        MapKitFactory.initialize(this.applicationContext) // Используем applicationContext


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Запускаем планировщик отправки данных
        schedulePotholeUpload(this, this)
        //для теста разово
        val testRequest = OneTimeWorkRequestBuilder<PotholeUploadWorker>().build()
        WorkManager.getInstance(this).enqueue(testRequest)

        /*val intent = Intent(this, PotholeDataService::class.java)
        startService(intent)*/


        tokenManager = TokenManager(this)
        lifecycleScope.launch {
            setupNavigation()
        }


        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setupWithNavController(navController)


        // Кастомный обработчик для пунктов меню
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    navController.navigate(R.id.nav_settings)
                    binding.drawerLayout.closeDrawer(navView)
                    true
                }
                R.id.nav_profile -> {
                    val app = application as SmartRoadApp

                    lifecycleScope.launch {
                        val isAuth = app.authRepository.isAuthenticated()
                        if (isAuth) {
                            navController.navigate(R.id.nav_profile)
                        } else {
                            //startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                            navController.navigate(R.id.nav_login)
                        }
                        binding.drawerLayout.closeDrawer(navView)
                    }

                    true
                }
                R.id.nav_garage -> {
                    navController.navigate(R.id.nav_garage)
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    true
                }

                R.id.nav_bill -> {
                    navController.navigate(R.id.nav_bill)
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    true
                }
                R.id.nav_exit -> {
                    AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                        .setTitle("Выход")
                        .setMessage("Вы действительно хотите выйти из приложения?")
                        .setPositiveButton("Да") { _, _ ->
                            finishAffinity() // Полностью закрыть приложение
                        }
                        .setNegativeButton("Отмена", null)
                        .show()

                    binding.drawerLayout.closeDrawer(navView)
                    true
                }
                else -> {
                    // Стандартное поведение NavigationUI
                    false
                }
            }
        }
        //checkLocationPermissions()
        checkAndRequestPermissions()
        //requestBatteryOptimizationIgnore()
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
        else{
            startPotholeService()
        }
    }

    private fun requestBatteryOptimizationIgnore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions += Manifest.permission.ACTIVITY_RECOGNITION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions += Manifest.permission.POST_NOTIFICATIONS
        }

        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                notGranted.toTypedArray(),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // все foreground-права уже есть
            checkBackgroundLocationPermission()
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE + 1
                )
            } else {
                startPotholeService()
            }
        } else {
            startPotholeService()
        }
    }


    // обработка результата запроса
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                // после foreground — проверяем background
                checkBackgroundLocationPermission()
            } else {
                Toast.makeText(this, "Для работы приложения нужны разрешения", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE + 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startPotholeService()
            } else {
                Toast.makeText(this, "Фоновая геолокация нужна для работы в фоне", Toast.LENGTH_LONG).show()
            }
        }
    }



    /** Запуск сервиса */
    private fun startPotholeService() {

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isTelemetryOn = prefs.getBoolean("telemetry_enabled", false)

        if (!isTelemetryOn) {
            // Если пользователь выключил телеметрию, не запускаем сервис
            return
        }

        schedulePotholeUpload(this, this)
        val serviceIntent = Intent(this, PotholeDataService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private suspend fun setupNavigation() {
        navController = findNavController(R.id.nav_host_fragment)

        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,  // Домашний экран
                R.id.nav_garage,
                R.id.nav_settings_device
            ),
            binding.drawerLayout
        )

        // Настройка BottomNavigation
        binding.bottomNav.setupWithNavController(navController)

        // Настройка NavigationView (правого меню)
        binding.navView.setupWithNavController(navController)

        // Обработчик пунктов меню
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    navController.navigate(R.id.nav_settings)
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    true
                }
                R.id.nav_garage -> {
                    navController.navigate(R.id.nav_garage)
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    true
                }
                R.id.nav_home -> {
                    navController.navigate(R.id.nav_home)
                    binding.drawerLayout.closeDrawer(GravityCompat.END)
                    true
                }
                /*R.id.nav_profile_activity -> {
                    navigateToLogin()
                    true
                }*/
                else -> false
            }
        }
    }

    //private fun navigateToLogin() {
        //startActivity(Intent(this, LoginActivity::class.java))
        //finish()
    //}

    /*private fun performLogout() {
        lifecycleScope.launch {
            tokenManager.clearTokens()
            navigateToLogin()
        }
    }*/


    override fun onSupportNavigateUp(): Boolean {
        //val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }


    override fun onDestroy() {
        super.onDestroy()
        // Остановить фоновый сервис при уничтожении активности (при смахивании)
        val intent = Intent(this, PotholeDataService::class.java)
        stopService(intent)
    }


    fun openDrawer() {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (!drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.openDrawer(GravityCompat.END)
        }
    }

    fun toggleService(start: Boolean) {
        if (start) {
            checkAndRequestPermissions()
        } else {
            stopPotholeService()
        }
    }
    fun stopPotholeService() {
        val intent = Intent(this, PotholeDataService::class.java)
        stopService(intent)
    }



}

