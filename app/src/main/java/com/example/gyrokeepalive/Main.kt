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
                setContentView(layout)
    }

    private fun start(fast: Boolean) {
        startForegroundService(
            Intent(this, SensorKeepAliveService::class.java).putExtra("fast", fast)
        )
    }
}

class SensorKeepAliveService : Service(), SensorEventListener {
    private lateinit var sm: SensorManager
    private var thread: HandlerThread? = null

    private val types = intArrayOf(
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GRAVITY
    )

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val fast = intent?.getBooleanExtra("fast", false) ?: false
        startForegroundNotification(fast)

        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.unregisterListener(this)
        thread?.quitSafely()
        thread = HandlerThread("sensor-keepalive").also { it.start() }
        val handler = Handler(thread!!.looper)

        for (t in types) {
            val s = sm.getDefaultSensor(t) ?: continue
            if (fast) {
                sm.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST, 0, handler)
            } else {
                sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification(fast: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("ka", "Sensor keep-alive", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "ka")
            .setContentTitle("Gyro keep-alive running")
            .setContentText(if (fast) "Mode: FASTEST" else "Mode: NORMAL (Hyper Gyro style)")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onSensorChanged(e: SensorEvent?) {}
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() {
        if (::sm.isInitialized) sm.unregisterListener(this)
        thread?.quitSafely()
        super.onDestroy()
    }
}
