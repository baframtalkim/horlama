package com.example.horlama

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/** Saate yansıyan bildirim ve test uyarısı için ortak yardımcılar */
object AlertNotifier {
    const val CH_ALERT = "alert3"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CH_ALERT, "Horlama uyarısı", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)      // ses yok
        ch.enableVibration(false)    // telefon titreşimini Vibrator yönetir; saat bildirimle titrer
        nm.createNotificationChannel(ch)
    }

    /** Gerçek alarm bildirimi (kalıcı değil; kaydırılırsa veya dokunulursa alarm susar) */
    fun postAlarm(ctx: Context, count: Int = 1) {
        ensureChannel(ctx)
        val pi = PendingIntent.getService(
            ctx, 2, Intent(ctx, SnoreService::class.java).setAction(SnoreService.ACTION_SILENCE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(if (count > 1) "Horlama algılandı ($count)" else "Horlama algılandı")
            .setContentText("Titreşimi durdurmak için dokun")
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setDeleteIntent(pi)
            .addAction(0, "Durdur", pi)
            .build()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(2) // saat uygulaması her seferinde yeni bildirim görsün
        nm.notify(2, n)
    }

    /** Test bildirimi: gerçek alarmla aynı kanaldan gider */
    fun postTest(ctx: Context) {
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Test uyarısı")
            .setContentText("Saatin titremesi gerekir")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setTimeoutAfter(20_000)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(3, n)
    }

    @Suppress("DEPRECATION")
    fun vibrateOnce(ctx: Context, ms: Long) {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        v.vibrate(VibrationEffect.createOneShot(ms, 200), attrs)
    }
}
