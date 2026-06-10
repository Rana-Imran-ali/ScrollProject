package com.example.scrollproject.ui.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window

/**
 * TimeLimitDialog — retained as a stub for build compatibility.
 *
 * Daily time limits no longer exist in the app. This dialog is never shown.
 * The stub prevents compile errors from any lingering references.
 */
class TimeLimitDialog(
    context: Context,
    @Suppress("UNUSED_PARAMETER") appName: String,
    @Suppress("UNUSED_PARAMETER") onSave: (Int) -> Unit
) : Dialog(context) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
    }
}
