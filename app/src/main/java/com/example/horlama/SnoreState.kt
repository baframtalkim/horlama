package com.example.horlama

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf

/** Servis ile arayüz arasında paylaşılan canlı durum */
object SnoreState {
    val listening = mutableStateOf(false)
    val alarming = mutableStateOf(false)
    val alerts = mutableIntStateOf(0)
    val level = mutableFloatStateOf(0f)
    val startedAt = mutableLongStateOf(0L)
    val logVersion = mutableIntStateOf(0)
}
