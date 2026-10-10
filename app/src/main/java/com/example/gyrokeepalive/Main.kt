package com.example.gyrokeepalive

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.*
import android.os.*
import android.widget.Button
import android.widget.LinearLayout

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        fun btn(label: String, action: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { action() }
            layout.addView(this)
        }
        btn("Start: NORMAL (like Hyper Gyro)") { start(false) }
        btn("Start: FASTEST") { start(true) }
        btn("Stop") {
            startService(Intent(this, SensorKeepAliveService::class.java).setAction("STOP"))
        }
