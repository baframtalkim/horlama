package com.example.horlama

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import java.util.ArrayDeque

class SnoreService : Service() {

    companion object {
        const val EXTRA_SENSITIVITY = "sensitivity"
        const val ACTION_STOP = "stop"        // dinlemeyi bitir
        const val ACTION_SILENCE = "silence"  // titreşimi durdur
        @Volatile var running = false
        private const val CH_LISTEN = "listen"
        private const val SAMPLE_RATE = 16000
    }

    private var worker: Thread? = null
    @Volatile private var active = false
    @Volatile private var alarming = false
    @Volatile private var silencedAt = 0L
    private lateinit var detector: SnoreDetector
    private val main = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    // Saate giden bildirimi alarm sürdükçe ayarlardaki aralıkla (1-10 sn) yeniler.
    // Her bildirim saatte ayrı bir titreşim verir.
    private var watchCount = 0
    private var alarmStartedAt = 0L
    private val watchTick = object : Runnable {
        override fun run() {
            if (alarming) {
                watchCount += 1
                AlertNotifier.postAlarm(this@SnoreService, watchCount)
                val sec = getSharedPreferences("p", Context.MODE_PRIVATE)
                    .getInt("watch_interval", 3).coerceIn(1, 10)
                main.postDelayed(this, sec * 1000L)
            }
        }
    }
    // 20 sn sonra titreşimi güçlendirir
    private val escalate = Runnable { if (alarming) startVibration(255, 1200, 400) }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_SILENCE -> { stopAlarm(); return START_NOT_STICKY }
        }
        if (active) return START_NOT_STICKY

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY }

        createChannels()
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, SnoreService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif = NotificationCompat.Builder(this, CH_LISTEN)
            .setContentTitle("Sessiz Gece")
            .setContentText("Dinleniyor…")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(0, "Dinlemeyi bitir", stopPi)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else startForeground(1, notif)

        // Ekran kapalıyken de dinlemeyi sürdürmek için
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sessizgece:listen")
            .apply { acquire(12 * 60 * 60 * 1000L) }

        detector = SnoreDetector(SAMPLE_RATE, intent?.getFloatExtra(EXTRA_SENSITIVITY, 0.8f) ?: 0.8f)
        active = true
        running = true
        SnoreState.alerts.intValue = 0
        SnoreState.startedAt.longValue = System.currentTimeMillis()
        SnoreState.listening.value = true
        worker = Thread { listenLoop() }.also { it.start() }
        return START_NOT_STICKY
    }

    private fun listenLoop() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) { stopSelf(); return }

        val frame = 4096 // ~0.25 sn
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, frame * 4))
        val buf = ShortArray(frame)
        val events = ArrayDeque<Long>()
        var lastEventAt = 0L
        val sp = getSharedPreferences("p", Context.MODE_PRIVATE)

        rec.startRecording()
        try {
            while (active) {
                val n = rec.read(buf, 0, frame)
                if (n <= 0) continue
                val now = SystemClock.elapsedRealtime()

                // Hassasiyet ayarı dinleme sırasında da anında uygulanır
                detector.sensitivity = sp.getFloat("sens2", detector.sensitivity)
                val needed = if (detector.sensitivity >= 0.6f) 3 else 4
                val snore = detector.isSnoreLike(buf, n)
                SnoreState.level.floatValue = (detector.lastRms * 10).toFloat().coerceIn(0f, 1f)

                // Alarm sürerken veya durdurduktan sonraki 30 sn boyunca olay sayma
                if (alarming || now - silencedAt < 30_000) { events.clear(); continue }

                if (snore && now - lastEventAt > 1200) {
                    events.addLast(now)
                    lastEventAt = now
                }
                while (true) {
                    val first = events.peekFirst() ?: break
                    if (now - first > 25_000) events.removeFirst() else break
                }
                if (events.size >= needed) {
                    events.clear()
                    startAlarm()
                }
            }
        } finally {
            rec.stop(); rec.release()
        }
    }

    /** Alarm: kullanıcı durdurana kadar titreşim sürer, uyarı kayda geçer. */
    private fun startAlarm() {
        alarming = true
        watchCount = 0
        alarmStartedAt = SystemClock.elapsedRealtime()
        SnoreState.alarming.value = true
        SnoreState.alerts.intValue += 1
        AlertLog.add(this, System.currentTimeMillis())
        SnoreState.logVersion.intValue += 1
        // Uyarı şekli (ayarlardan anlık okunur): 0 = telefon + saat, 1 = sadece telefon, 2 = sadece saat
        val mode = getSharedPreferences("p", Context.MODE_PRIVATE).getInt("mode", 0)
        main.removeCallbacks(escalate)
        main.removeCallbacks(watchTick)
        if (mode != 2) {
            startVibration(150, 800, 600)
            main.postDelayed(escalate, 20_000)
        }
        if (mode != 1) main.post(watchTick)
    }

    private fun stopAlarm() {
        main.removeCallbacks(escalate)
        main.removeCallbacks(watchTick)
        if (alarming) {
            alarming = false
            SnoreState.alarming.value = false
            silencedAt = SystemClock.elapsedRealtime()
        }
        vibrator().cancel()
        getSystemService(NotificationManager::class.java).cancel(2)
    }

    @Suppress("DEPRECATION")
    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31)
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    /** Sürekli tekrarlayan titreşim (cancel edilene kadar) */
    @Suppress("DEPRECATION")
    private fun startVibration(amplitude: Int, onMs: Long, offMs: Long) {
        val effect = VibrationEffect.createWaveform(
            longArrayOf(0, onMs, offMs), intArrayOf(0, amplitude, 0), 0)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        vibrator().vibrate(effect, attrs)
    }

    /** Sessiz (ses yok) bildirim: Huawei Health üzerinden saatte titreşim verir */
    private fun notifyWatch() = AlertNotifier.postAlarm(this, watchCount)

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LISTEN, "Dinleme", NotificationManager.IMPORTANCE_LOW))
        AlertNotifier.ensureChannel(this)
    }

    override fun onDestroy() {
        active = false
        running = false
        stopAlarm()
        SnoreState.listening.value = false
        SnoreState.level.floatValue = 0f
        worker?.join(500)
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
