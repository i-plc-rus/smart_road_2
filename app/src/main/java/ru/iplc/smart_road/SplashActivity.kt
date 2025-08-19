package ru.iplc.smart_road

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

import ru.iplc.smart_road.data.local.TokenManager

class SplashActivity : AppCompatActivity() {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        //tokenManager = TokenManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            val destination = Intent(this@SplashActivity, MainActivity::class.java)
            startActivity(destination)
            finish()
        }, 300) // 0.3 секунды задержки
    }


}