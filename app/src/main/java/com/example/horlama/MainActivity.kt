package com.example.horlama

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Bg1 = Color(0xFF080B1E)
private val Bg2 = Color(0xFF1B1347)
private val Gold = Color(0xFFF5C86B)
private val Violet = Color(0xFF7C6CFF)
private val TextMain = Color(0xFFEDE9FF)
private val TextMuted = Color(0xFF9A96C4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Gold)) { Screen() }
        }
    }

    @Composable
    private fun Screen() {
        val ctx = this@MainActivity
        val prefs = remember { getSharedPreferences("p", Context.MODE_PRIVATE) }
        var tab by remember { mutableIntStateOf(0) }
        var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("sens2", 0.8f)) }
        var mode by remember { mutableIntStateOf(prefs.getInt("mode", 0)) }
        var watchInterval by remember { mutableIntStateOf(prefs.getInt("watch_interval", 3)) }
        val listening by SnoreState.listening
        val alarming by SnoreState.alarming
        var elapsed by remember { mutableLongStateOf(0L) }
        var confirmClear by remember { mutableStateOf(false) }

        val logVersion = SnoreState.logVersion.intValue
        val nights = remember(logVersion) { AlertLog.nights(ctx) }
        val tonight = nights
            .firstOrNull { it.date == AlertLog.nightOf(System.currentTimeMillis()) }?.count ?: 0

        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { res ->
            if (res[Manifest.permission.RECORD_AUDIO] == true) {
                startForegroundService(
                    Intent(ctx, SnoreService::class.java)
                        .putExtra(SnoreService.EXTRA_SENSITIVITY, sensitivity)
                )
            }
        }

        fun runTest() {
            val m = prefs.getInt("mode", 0)
            if (m != 2) AlertNotifier.vibrateOnce(ctx, 1500)
            if (m != 1) AlertNotifier.postTest(ctx)
        }

        val testPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> if (granted || Build.VERSION.SDK_INT < 33) runTest() }

        fun startTest() {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) testPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else runTest()
        }

        fun send(action: String) {
            startService(Intent(ctx, SnoreService::class.java).setAction(action))
        }

        fun toggle() {
            when {
                SnoreState.alarming.value -> send(SnoreService.ACTION_SILENCE)
                SnoreState.listening.value -> send(SnoreService.ACTION_STOP)
                else -> {
                    val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                    permLauncher.launch(perms.toTypedArray())
                }
            }
        }

        LaunchedEffect(listening) {
            while (listening) {
                elapsed = (System.currentTimeMillis() - SnoreState.startedAt.longValue) / 1000
                delay(1000)
            }
        }

        val pulseTransition = rememberInfiniteTransition(label = "pulse")
        val pulse by pulseTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (alarming) 1200 else 2800, easing = LinearEasing)),
            label = "pulse"
        )
        val levelAnim by animateFloatAsState(SnoreState.level.floatValue, label = "level")

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Bg1, Bg2)))
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppHeader()
                    Spacer(Modifier.height(22.dp))

                    when (tab) {
                        // ---------------- DİNLE ----------------
                        0 -> {
                            Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val base = size.minDimension / 2
                                    if (listening) {
                                        for (k in 0..1) {
                                            val t = (pulse + k * 0.5f) % 1f
                                            drawCircle(
                                                color = Gold.copy(alpha = (1f - t) * 0.4f),
                                                radius = base * (0.6f + 0.4f * t),
                                                center = center,
                                                style = Stroke(width = 2.5.dp.toPx())
                                            )
                                        }
                                    }
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf((if (listening) Gold else Violet).copy(alpha = 0.30f), Color.Transparent),
                                            center = center,
                                            radius = base
                                        ),
                                        radius = base,
                                        center = center
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(158.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                if (listening) listOf(Color(0xFF5B4BD6), Color(0xFF2A1F6B))
                                                else listOf(Color(0xFF2B2370), Color(0xFF120D38))
                                            )
                                        )
                                        .border(
                                            1.5.dp,
                                            if (listening) Gold else Color.White.copy(alpha = 0.18f),
                                            CircleShape
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { toggle() }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(180.dp))
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            Text(
                                when {
                                    alarming -> "Horlama algılandı"
                                    listening -> "Dinleniyor"
                                    else -> "Başlatmak için dokun"
                                },
                                color = if (alarming) Gold else TextMain,
                                fontSize = 20.sp, fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "%02d:%02d:%02d".format(elapsed / 3600, elapsed % 3600 / 60, elapsed % 60),
                                color = if (listening) Gold else TextMuted, fontSize = 15.sp
                            )
                            if (listening && !alarming) {
                                Spacer(Modifier.height(4.dp))
                                Text("Bitirmek için butona tekrar dokun", color = TextMuted, fontSize = 11.sp)
                            }

                            if (alarming) {
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Gold)
                                        .clickable { send(SnoreService.ACTION_SILENCE) }
                                        .padding(vertical = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Titreşimi durdur", color = Bg1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Glass(Modifier.weight(1f)) {
                                    Text("Bu gece uyarı", color = TextMuted, fontSize = 12.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Text("$tonight", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Glass(Modifier.weight(1f)) {
                                    Text("Ortam sesi", color = TextMuted, fontSize = 12.sp)
                                    Spacer(Modifier.height(18.dp))
                                    LinearProgressIndicator(
                                        progress = { levelAnim },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Gold,
                                        trackColor = Color.White.copy(alpha = 0.12f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(18.dp))
                            Text(
                                "Telefonu yastığın yanına koy, mikrofon açıkta kalsın. Horlama algılanınca " +
                                "uyarı sen durdurana kadar sürer.",
                                color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center
                            )
                        }

                        // ---------------- GEÇMİŞ ----------------
                        1 -> {
                            Glass(Modifier.fillMaxWidth()) {
                                Text("Uyarı geçmişi", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Toplam ${nights.sumOf { it.count }} uyarı, ${nights.size} gece",
                                    color = TextMuted, fontSize = 12.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                if (nights.isEmpty()) {
                                    Text("Henüz kayıt yok", color = TextMuted, fontSize = 13.sp)
                                } else {
                                    nights.take(30).forEach { n ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(AlertLog.dayLabel(n.date), color = TextMain, fontSize = 14.sp)
                                                Text(
                                                    "${AlertLog.timeLabel(n.first)} – ${AlertLog.timeLabel(n.last)}",
                                                    color = TextMuted, fontSize = 11.sp
                                                )
                                            }
                                            Text("${n.count} uyarı", color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        if (confirmClear) "Silmek için tekrar dokun" else "Kaydı temizle",
                                        color = TextMuted, fontSize = 12.sp,
                                        modifier = Modifier.clickable {
                                            if (confirmClear) {
                                                AlertLog.clear(ctx)
                                                SnoreState.logVersion.intValue += 1
                                                confirmClear = false
                                            } else confirmClear = true
                                        }
                                    )
                                }
                            }
                        }

                        // ---------------- AYARLAR ----------------
                        else -> {
                            Glass(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Hassasiyet", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text("${(sensitivity * 100).toInt()}%", color = Gold, fontSize = 15.sp)
                                }
                                Slider(
                                    value = sensitivity,
                                    onValueChange = { sensitivity = it },
                                    onValueChangeFinished = {
                                        prefs.edit().putFloat("sens2", sensitivity).apply()
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Gold,
                                        activeTrackColor = Gold,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Düşük", color = TextMuted, fontSize = 11.sp)
                                    Text("Yüksek", color = TextMuted, fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Yüksek hassasiyet uzaktaki kısık horlamayı da yakalar, ama yanlış alarm artabilir. Dinleme sırasında da değiştirebilirsin.",
                                    color = TextMuted, fontSize = 11.sp
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Glass(Modifier.fillMaxWidth()) {
                                Text("Uyarı şekli", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(10.dp))
                                val labels = listOf("Telefon + saat", "Sadece telefon", "Sadece saat")
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    labels.forEachIndexed { i, label ->
                                        val sel = mode == i
                                        Box(
                                            Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (sel) Gold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                                .border(1.dp, if (sel) Gold else Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                                                .clickable {
                                                    mode = i
                                                    prefs.edit().putInt("mode", i).apply()
                                                }
                                                .padding(vertical = 12.dp, horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                label,
                                                color = if (sel) Gold else TextMain,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center,
                                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    listOf(
                                        "Telefon titrer, saate de bildirim gider",
                                        "Telefon titrer, saate bildirim gitmez",
                                        "Telefon sessiz kalır, yalnızca saat titrer"
                                    )[mode],
                                    color = TextMuted, fontSize = 11.sp
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Glass(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Saat bildirim sıklığı", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text("$watchInterval sn", color = Gold, fontSize = 15.sp)
                                }
                                Slider(
                                    value = watchInterval.toFloat(),
                                    onValueChange = { watchInterval = it.toInt().coerceIn(1, 10) },
                                    onValueChangeFinished = {
                                        prefs.edit().putInt("watch_interval", watchInterval).apply()
                                    },
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Gold,
                                        activeTrackColor = Gold,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("1 sn (sık)", color = TextMuted, fontSize = 11.sp)
                                    Text("10 sn (seyrek)", color = TextMuted, fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Horlama sürdükçe saate bu aralıkla yeni bildirim gider, her biri saatte bir titreşim verir. Sık aralık daha güçlü uyandırır ama saatin pilini hızlı tüketir. Değişiklik bir sonraki alarmda geçerli olur.",
                                    color = TextMuted, fontSize = 11.sp
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            Glass(Modifier.fillMaxWidth()) {
                                Text("Test uyarısı", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Horlama taklidi yapmadan, seçili uyarı şekliyle telefonu ve saati dener.",
                                    color = TextMuted, fontSize = 11.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Gold.copy(alpha = 0.18f))
                                        .border(1.dp, Gold, RoundedCornerShape(14.dp))
                                        .clickable { startTest() }
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Test uyarısı gönder", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            Glass(Modifier.fillMaxWidth()) {
                                Text("Saat bildirimi kurulumu", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "1. Huawei Health → Cihazlar → saatin → Bildirimler bölümünü aç.\n" +
                                    "2. Sessiz Gece uygulamasını listeden aç (ilk uyarıdan sonra görünür).\n" +
                                    "3. Saatte Rahatsız Etme / uyku modunu kapat.\n" +
                                    "4. Telefonda pil optimizasyonunu bu uygulama ve Huawei Health için kapat.",
                                    color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                BottomBar(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF0B1030), Color(0xFF3A2A9A))))
                .border(1.dp, Gold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(84.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Sessiz Gece", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("Akıllı horlama koruması", color = Gold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("Dinle", "Geçmiş", "Ayarlar")
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0C0F2B))
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 12.dp)
    ) {
        items.forEachIndexed { i, label ->
            val sel = selected == i
            Column(
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(i) }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .width(28.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (sel) Gold else Color.Transparent)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    color = if (sel) Gold else TextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun Glass(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(16.dp),
        content = content
    )
}
