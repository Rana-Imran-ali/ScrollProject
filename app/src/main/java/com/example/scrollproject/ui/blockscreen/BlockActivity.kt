package com.example.scrollproject.ui.blockscreen

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.scrollproject.R
import com.example.scrollproject.databinding.ActivityBlockOverlayBinding
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * BlockActivity — full-screen restriction overlay.
 *
 * Bypass-prevention checklist:
 *  ✅ FLAG_KEEP_SCREEN_ON + FLAG_SHOW_WHEN_LOCKED keeps overlay on lock-screen.
 *  ✅ Back button redirects to Home (never pops to blocked app).
 *  ✅ launchMode="singleTask" in manifest prevents stacking duplicate overlays.
 *  ✅ excludeFromRecents="true" in manifest hides it from the Recents list.
 *  ✅ "OK, I'll wait" dismiss button also goes to Home — not finish().
 *  ✅ onResume re-arms the back-button callback in case the activity is
 *     brought back to front from Recents.
 *  ✅ "Adjust My Limit" opens MainActivity with FLAG_ACTIVITY_CLEAR_TOP so
 *     the blocked app's task is not resumed.
 */
class BlockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME  = "extra_package_name"
        const val EXTRA_APP_NAME      = "extra_app_name"
        const val EXTRA_REASON        = "extra_reason"
        const val EXTRA_LIMIT_MINUTES = "extra_limit_minutes"
    }

    private lateinit var binding: ActivityBlockOverlayBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep overlay visible even on lock-screen.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val appName      = intent.getStringExtra(EXTRA_APP_NAME)      ?: "This app"
        val reason       = intent.getStringExtra(EXTRA_REASON)        ?: "limit_reached"
        val limitMinutes = intent.getIntExtra(EXTRA_LIMIT_MINUTES, 60)

        bindUI(appName, reason, limitMinutes)
        armBackButton()
    }

    override fun onResume() {
        super.onResume()
        // Re-arm the callback every time the activity surfaces, so bringing
        // it back from the Recents switcher still intercepts the back gesture.
        armBackButton()
    }

    // ─── UI binding ───────────────────────────────────────────────────────────

    private fun bindUI(appName: String, reason: String, limitMinutes: Int) {
        binding.tvBlockedAppName.text = appName
        binding.tvBlockTitle.text     = getString(R.string.block_title)

        binding.tvBlockSubtitle.text = when (reason) {
            "focus_mode" -> "Focus mode is active. Stay on track!"
            else         -> "Daily limit reached. Come back tomorrow."
        }

        // Resets-in countdown (shown in the daily-limit field).
        startResetCountdown()

        // "Adjust My Limit" → open MainActivity and clear the app task stack.
        binding.btnAdjustLimit.setOnClickListener {
            goHome()
            startActivity(
                Intent(this, com.example.scrollproject.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        // "OK, I'll wait" → send user to Home; do NOT finish() here so the
        // AccessibilityService can still see BlockActivity in the foreground.
        binding.btnDismiss.setOnClickListener {
            goHome()
        }
    }

    // ─── Reset countdown ─────────────────────────────────────────────────────

    private fun startResetCountdown() {
        scope.launch {
            while (isActive) {
                val now      = Calendar.getInstance()
                val midnight = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diff    = midnight.timeInMillis - now.timeInMillis
                val hours   = TimeUnit.MILLISECONDS.toHours(diff)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60

                binding.tvDailyLimit.text =
                    String.format("Resets in: %02d:%02d:%02d", hours, minutes, seconds)
                delay(1_000L)
            }
        }
    }

    // ─── Back / navigation interception ──────────────────────────────────────

    private fun armBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
    }

    /** Always navigate to the Launcher — never allow returning to a blocked app. */
    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    // ─── Teardown ─────────────────────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
