package com.example.horlama

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Horlama algılayıcı (uzaktan, ~50 cm hassasiyet için ayarlı).
 * - Ortam gürültü tabanını kendisi öğrenir; ses tabanın belirli bir katını aşarsa dikkate alır.
 * - Düşük frekans (80-500 Hz) enerjisi yüksek frekansa (1-3 kHz) göre baskınsa horlama sayar.
 * sensitivity: 0..1 (yüksek = daha hassas)
 */
class SnoreDetector(
    private val sampleRate: Int = 16000,
    @Volatile var sensitivity: Float = 0.8f
) {
    @Volatile var lastRms = 0.0
    private var floor = 0.003
    private var calib = 0

    fun isSnoreLike(buf: ShortArray, n: Int): Boolean {
        if (n <= 0) return false
        var sum = 0.0
        for (i in 0 until n) {
            val s = buf[i] / 32768.0
            sum += s * s
        }
        val rms = sqrt(sum / n)
        lastRms = rms

        // İlk ~8 saniye ortam sesini öğren
        if (calib < 32) {
            calib++
            floor = if (calib == 1) rms else floor * 0.9 + rms * 0.1
            return false
        }
        // Sessizken taban hızlı iner, yukarı çok yavaş çıkar
        floor = if (rms < floor) floor * 0.8 + rms * 0.2 else floor * 1.003
        floor = floor.coerceIn(0.0004, 0.05)

        val s = sensitivity.toDouble()
        val ratio = 1.4 + (1.0 - s) * 3.0           // 1.0 -> 1.4x, 0.8 -> 2.0x, 0.5 -> 2.9x
        val absMin = 0.0010 + (1.0 - s) * 0.010
        if (rms < maxOf(floor * ratio, absMin)) return false

        val low = bandEnergy(buf, n, 80, 500, 40)
        val high = bandEnergy(buf, n, 1000, 3000, 400)
        return low > high * (1.2 + (1.0 - s) * 1.0)
    }

    private fun bandEnergy(buf: ShortArray, n: Int, from: Int, to: Int, step: Int): Double {
        var total = 0.0
        var count = 0
        var f = from
        while (f <= to) {
            total += goertzel(buf, n, f.toDouble())
            count++
            f += step
        }
        return if (count == 0) 0.0 else total / count
    }

    private fun goertzel(buf: ShortArray, n: Int, freq: Double): Double {
        val w = 2.0 * PI * freq / sampleRate
        val coeff = 2.0 * cos(w)
        var s1 = 0.0
        var s2 = 0.0
        for (i in 0 until n) {
            val s0 = buf[i] / 32768.0 + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }
}
