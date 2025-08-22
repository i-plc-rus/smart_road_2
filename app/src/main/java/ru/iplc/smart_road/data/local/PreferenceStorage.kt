package ru.iplc.smart_road.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.iplc.smart_road.data.model.Car
import ru.iplc.smart_road.data.model.InstallationMethod
import ru.iplc.smart_road.data.model.UserProfile

object PreferenceStorage {
    private lateinit var userPrefs: SharedPreferences
    private lateinit var garagePrefs: SharedPreferences
    private lateinit var devicePrefs: SharedPreferences
    private val gson = Gson()

    // Ключи для хранения данных
    private const val CARS_KEY = "user_cars"
    private const val DEFAULT_CAR_KEY = "default_car"
    private const val INSTALLATION_METHOD_KEY = "installation_method"

    fun init(context: Context) {
        userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        garagePrefs = context.getSharedPreferences("garage_prefs", Context.MODE_PRIVATE)
        devicePrefs = context.getSharedPreferences("device_prefs", Context.MODE_PRIVATE)
    }

    // ========== Установка устройства ==========
    fun saveInstallationMethod(method: InstallationMethod) {
        devicePrefs.edit()
            .putString(INSTALLATION_METHOD_KEY, method.name)
            .apply()
    }

    fun getInstallationMethod(): InstallationMethod? {
        val methodName = devicePrefs.getString(INSTALLATION_METHOD_KEY, null)
        return methodName?.let {
            try {
                InstallationMethod.valueOf(it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    fun clearInstallationMethod() {
        devicePrefs.edit()
            .remove(INSTALLATION_METHOD_KEY)
            .apply()
    }

    // ========== Гараж ==========
    fun saveCars(cars: List<Car>) {
        val json = gson.toJson(cars)
        garagePrefs.edit()
            .putString(CARS_KEY, json)
            .apply()
    }

    fun getCars(): List<Car> {
        val json = garagePrefs.getString(CARS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<List<Car>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun saveDefaultCar(car: Car?) {
        if (car == null) {
            garagePrefs.edit()
                .remove(DEFAULT_CAR_KEY)
                .apply()
        } else {
            val json = gson.toJson(car)
            garagePrefs.edit()
                .putString(DEFAULT_CAR_KEY, json)
                .apply()
        }
    }

    fun getDefaultCar(): Car? {
        val json = garagePrefs.getString(DEFAULT_CAR_KEY, null)
        return if (json != null) {
            gson.fromJson(json, Car::class.java)
        } else {
            null
        }
    }

    fun clearGarage() {
        garagePrefs.edit()
            .clear()
            .apply()
    }

    // ========== Профиль пользователя ==========
    fun saveProfile(profile: UserProfile) {
        userPrefs.edit()
            .putString("username", profile.username)
            .putString("fio", profile.fio)
            .putString("phone", profile.phone)
            .putString("avatarUrl", profile.avatarUrl)
            .apply()
    }

    fun getProfile(): UserProfile? {
        val username = userPrefs.getString("username", null) ?: return null
        return UserProfile(
            username = username,
            fio = userPrefs.getString("fio", "") ?: "",
            phone = userPrefs.getString("phone", "") ?: "",
            email = userPrefs.getString("email", "") ?: "",
            password = userPrefs.getString("password", "") ?: "",
            avatarUrl = userPrefs.getString("avatarUrl", "") ?: ""
        )
    }

    fun clearUserData() {
        userPrefs.edit()
            .clear()
            .apply()
    }

    // ========== Полная очистка ==========
    fun clearAll() {
        clearUserData()
        clearGarage()
        clearInstallationMethod()
    }
}