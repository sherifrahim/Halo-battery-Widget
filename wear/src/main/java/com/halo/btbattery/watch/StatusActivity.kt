package com.halo.btbattery.watch

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/** Opened from the watch's app list: confirms the companion is installed. Nothing to configure. */
class StatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Halo is installed\n\nThis watch's battery now shows in Halo on your phone."
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        })
    }
}
