package ru.iplc.smart_road.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.iplc.smart_road.MainActivity
import ru.iplc.smart_road.data.local.PreferenceStorage
import ru.iplc.smart_road.R
import ru.iplc.smart_road.data.model.UserProfile

class AuthActivity : AppCompatActivity() {
    //private lateinit var prefs: PreferenceStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profile = PreferenceStorage.getProfile()
        if (profile != null) {
            startMain()
        } else {
            setContentView(R.layout.x_fragment_profile)
            // показать форму логина / регистрации
        }
    }

    private fun onLoginSuccess(profile: UserProfile) {
        PreferenceStorage.saveProfile(profile)
        startMain()
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun login(username: String, password: String) {
        // Retrofit-запрос к серверу
        /*api.login(username, password).enqueue(object : Callback<UserProfile> {
            override fun onResponse(call: Call<UserProfile>, response: Response<UserProfile>) {
                if (response.isSuccessful) {
                    val profile = response.body()!!
                    prefs.saveProfile(profile)
                    startMain()
                }
            }
            ...
        })*/
    }

}
