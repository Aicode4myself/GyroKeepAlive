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

const val MODE_NORMAL = 0
const val MODE_FAST = 1
const val MODE_ULTRA = 2
const val MODE_EXTREME = 3

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
                btn("Start: NORMAL (like Hyper Gyro)") { start(MODE_NORMAL) }
        btn("Start: FASTEST") { start(MODE_FAST) }
        btn("Start: ULTRA") { start(MODE_ULTRA) }
        btn("Start: EXTREME") { start(MODE_EXTREME) }
        btn("Stop") {
            startService(Intent(this, SensorKeepAliveService::class.java).setAction("STOP"))
        }
        setContentView(layout)
    }

    private fun start(mode: Int) {
        startForegroundService(
            Intent(this, SensorKeepAliveService::class.java).putExtra("mode", mode)
        )
    }
}

class SensorKeepAliveService : Service(), SensorEventListener {
        private lateinit var sm: SensorManager
    private var thread: HandlerThread? = null
    private var wl: PowerManager.WakeLock? = null

    private val baseTypes = listOf(
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GRAVITY
    )

    private val ultraTypes = baseTypes + listOf(
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_LINEAR_ACCELERATION
    )

    private val ultraTypes = baseTypes + listOf(
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_LINEAR_ACCELERATION
    )

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        val mode = intent?.getIntExtra("mode", MODE_NORMAL) ?: MODE_NORMAL
        startForegroundNotification(mode)

        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.unregisterListener(this)
        thread?.quitSafely()
        releaseLock()

        val prio = when (mode) {
            MODE_EXTREME -> Process.THREAD_PRIORITY_URGENT_AUDIO
            MODE_ULTRA -> Process.THREAD_PRIORITY_URGENT_DISPLAY
            else -> Process.THREAD_PRIORITY_DEFAULT
        }
        thread = HandlerThread("sensor-keepalive", prio).also { it.start() }
        val handler = Handler(thread!!.looper)

        if (mode >= MODE_ULTRA) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gyrokeepalive:wl")
                .also { it.acquire() }
        }

        val sensors: List<Sensor> = when (mode) {
            MODE_EXTREME -> sm.getSensorList(Sensor.TYPE_ALL).filter {
                it.reportingMode == Sensor.REPORTING_MODE_CONTINUOUS
            }
            MODE_ULTRA -> ultraTypes.mapNotNull { sm.getDefaultSensor(it) }
            else -> baseTypes.mapNotNull { sm.getDefaultSensor(it) }
        }

        for (s in sensors) {
            try {
                if (mode == MODE_NORMAL) {
                    sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
                } else {
                    sm.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST, 0, handler)
                }
            } catch (e: Exception) {
            }
        }
        return START_STICKY
    }

    private fun modeName(mode: Int) = when (mode) {
        MODE_FAST -> "FASTEST"
        MODE_ULTRA -> "ULTRA"
        MODE_EXTREME -> "EXTREME"
        else -> "NORMAL (Hyper Gyro style)"
    }

    private fun startForegroundNotification(mode: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("ka", "Sensor keep-alive", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "ka")
            .setContentTitle("Gyro keep-alive running")
            .setContentText("Mode: " + modeName(mode))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun releaseLock() {
        wl?.let { if (it.isHeld) it.release() }
        wl = null
    }

    override fun onSensorChanged(e: SensorEvent?) {}
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onDestroy() {
        if (::sm.isInitialized) sm.unregisterListener(this)
        thread?.quitSafely()
        releaseLock()
        super.onDestroy()
    }
}
