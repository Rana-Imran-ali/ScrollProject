package com.example.scrollproject.ui.blockscreen

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.scrollproject.R
import com.example.scrollproject.databinding.ActivityBlockOverlayBinding
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import java.util.Calendar

class BlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_LIMIT_MINUTES = "extra_limit_minutes"
    }

    private lateinit var binding: ActivityBlockOverlayBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on top — overlay behaviour
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "This app"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "limit_reached"
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 60)

        bindUI(appName, reason, limitMinutes)
        blockBackButton()
    }

    private fun bindUI(appName: String, reason: String, limitMinutes: Int) {
        binding.tvBlockedAppName.text = appName
        binding.tvBlockTitle.text = getString(R.string.block_title)
        binding.tvBlockSubtitle.text = when (reason) {
            "focus_mode" -> "Focus mode is active. Stay on track!"
            else -> getString(R.string.block_subtitle)
        }
        binding.tvDailyLimit.text = "Daily limit: ${limitMinutes}m"

        binding.btnAdjustLimit.setOnClickListener {
            // Open main activity to adjust limit
            startActivity(
                android.content.Intent(this, com.example.scrollproject.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        binding.btnDismiss.setOnClickListener {
            finish()
        }

        startCooldownTimer()
    }

    private fun startCooldownTimer() {
        scope.launch {
            while (isActive) {
                val now = Calendar.getInstance()
                val tomorrow = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val diffMillis = tomorrow.timeInMillis - now.timeInMillis
                val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis) % 60

                binding.tvDailyLimit.text = String.format("Resets in: %02d:%02d:%02d", hours, minutes, seconds)
                delay(1000)
            }
        }
    }

    private fun blockBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Redirect to home instead of blocked app
                val home = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(home)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
