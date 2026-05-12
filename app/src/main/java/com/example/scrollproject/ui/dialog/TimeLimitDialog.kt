package com.example.scrollproject.ui.dialog

import android.app.Dialog
import android.content.Context
import android.widget.Toast
import android.os.Bundle
import android.view.Window
import com.example.scrollproject.databinding.DialogTimeLimitBinding
import com.example.scrollproject.domain.model.MonitoredApp

class TimeLimitDialog(
    context: Context,
    private val app: MonitoredApp,
    private val onSave: (Int) -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogTimeLimitBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogTimeLimitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        setupView()
    }

    private fun setupView() {
        binding.tvAppName.text = app.appName

        val currentMinutes = app.dailyLimitMinutes
        binding.pickerHours.apply {
            minValue = 0
            maxValue = 23
            value = currentMinutes / 60
        }
        binding.pickerMinutes.apply {
            minValue = 0
            maxValue = 59
            value = currentMinutes % 60
        }

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener {
            val totalMinutes = (binding.pickerHours.value * 60) + binding.pickerMinutes.value
            
            if (totalMinutes == 0) {
                Toast.makeText(context, "Minimum daily limit must be 1 minute", Toast.LENGTH_SHORT).show()
                binding.pickerMinutes.value = 1
                return@setOnClickListener
            }
            
            onSave(totalMinutes)
            dismiss()
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }
}
