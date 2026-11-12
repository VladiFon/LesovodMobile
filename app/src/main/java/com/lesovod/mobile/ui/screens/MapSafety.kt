package com.lesovod.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.data.location.Place
import com.lesovod.mobile.ui.components.LvBigButton
import com.lesovod.mobile.ui.components.LvSoftButton
import com.lesovod.mobile.ui.map.AzimuthState
import com.lesovod.mobile.ui.map.GuardState
import com.lesovod.mobile.ui.map.LatLon
import com.lesovod.mobile.ui.map.SosPhase
import com.lesovod.mobile.ui.map.SosState
import com.lesovod.mobile.ui.map.bearingDegrees
import com.lesovod.mobile.ui.map.contains
import com.lesovod.mobile.ui.map.crossTrackMeters
import com.lesovod.mobile.ui.map.distanceMeters
import com.lesovod.mobile.ui.map.distanceToBorderMeters
import com.lesovod.mobile.ui.map.formatCoordinates
import com.lesovod.mobile.ui.map.formatDistance
import com.lesovod.mobile.ui.map.rememberKompas
import com.lesovod.mobile.ui.theme.LocalLv
import com.lesovod.mobile.ui.theme.Manrope
import com.lesovod.mobile.ui.theme.Unbounded
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Этап 3 редизайна (12.11.2026): сторож границы, полноэкранный азимут и SOS.
 * Всё работает, пока открыта карта (без фоновой слежки — так договорились).
 */

private val Ink = Color(0xFF1E1D19)

/** Пороги сторожа: ближе NEAR_M к границе — жёлтый экран и вибрация. */
private const val GUARD_NEAR_M = 20.0
private const val GUARD_CLEAR_M = 30.0

// ------------------------------------------------------------------ SOS ---

/** Красная кнопка SOS слева внизу карты. */
@Composable
internal fun SosFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lv = LocalLv.current
    Box(
        modifier = modifier
            .size(68.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(lv.r)
            .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("SOS", color = Color.White, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 18.sp))
    }
}

/** Экран тревоги: удержать 3 секунды — метка SOS уходит мастерам; плюс 112 и СМС с местом. */
@Composable
internal fun SosOverlay(
    sos: SosState,
    me: LatLon?,
    here: Place?,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    val lv = LocalLv.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lv.bg)
            .clickable(enabled = false) {}
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(lv.sf2).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = lv.tx) }
            Spacer(Modifier.width(12.dp))
            Text("Тревога", color = lv.tx, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 20.sp))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when (sos.phase) {
                    SosPhase.HOLD -> "Удерживайте кнопку 3 секунды"
                    SosPhase.SENDING -> "Отправляю тревогу…"
                    SosPhase.SENT -> "Тревога отправлена"
                    SosPhase.FAILED -> "Не получилось отправить"
                },
                color = lv.tx,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
                modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
            )
            if (sos.phase == SosPhase.HOLD || sos.phase == SosPhase.SENDING) {
                Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(color = lv.r.copy(alpha = 0.18f))
                        drawArc(
                            color = lv.r,
                            startAngle = -90f,
                            sweepAngle = 360f * progress.value,
                            useCenter = false,
                            style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(lv.r)
                            .pointerInput(sos.phase) {
                                if (sos.phase != SosPhase.HOLD) return@pointerInput
                                detectTapGestures(onPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val job = scope.launch {
                                        progress.snapTo(0f)
                                        progress.animateTo(1f, tween(3000, easing = LinearEasing))
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSend()
                                    }
                                    tryAwaitRelease()
                                    if (progress.value < 1f) {
                                        job.cancel()
                                        scope.launch { progress.animateTo(0f, tween(250)) }
                                    }
                                })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sos.phase == SosPhase.SENDING) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SOS", color = Color.White, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 44.sp))
                                Text("держите", color = Color.White, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp))
                            }
                        }
                    }
                }
            }
            if (sos.phase == SosPhase.SENT) {
                Text(
                    "Получили: " + sos.sentTo.joinToString(", ") + ". У них придёт уведомление с вашим местом на карте.",
                    color = lv.tx,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 25.sp),
                )
            }
            sos.error?.let {
                Text(it, color = lv.rT, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 25.sp))
            }
            if (sos.phase == SosPhase.SENT || sos.phase == SosPhase.FAILED) {
                Spacer(Modifier.height(20.dp))
                LvBigButton(
                    text = "Позвонить 112",
                    icon = Icons.Rounded.Call,
                    container = lv.r,
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) } },
                )
                Spacer(Modifier.height(10.dp))
                LvSoftButton(
                    text = "СМС с моим местом",
                    icon = Icons.Rounded.Sms,
                    onClick = {
                        val text = "SOS! Нужна помощь. " + (here?.label?.let { "$it. " } ?: "") +
                            (me?.let { "Координаты: ${formatCoordinates(it)} https://maps.google.com/?q=${it.lat},${it.lon}" } ?: "")
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text)
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(lv.sf).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = lv.gT, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (me == null) "GPS ещё не нашёл вас. Выйдите на открытое место."
                else "Мастер получит ваше место: " + (here?.label ?: formatCoordinates(me)),
                color = lv.tx,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp),
            )
        }
    }
}

// --------------------------------------------------------------- азимут ---

/** Полноэкранный азимут: стрелка по компасу телефона, сколько осталось, насколько ушли в сторону. */
@Composable
internal fun AzimuthOverlay(az: AzimuthState, me: LatLon?, onStop: () -> Unit) {
    val lv = LocalLv.current
    val heading by rememberKompas(me)
    val bearing = me?.let { bearingDegrees(it, az.target) }
    val dist = me?.let { distanceMeters(it, az.target) }
    val totalDist = az.start?.let { distanceMeters(it, az.target) }
    val arrow = if (bearing != null) (bearing - (heading ?: 0.0)).toFloat() else 0f
    val xt = if (me != null && az.start != null && (totalDist ?: 0.0) > 15) crossTrackMeters(az.start, az.target, me) else 0.0
    val arrived = dist != null && dist < 10
    Column(
        Modifier.fillMaxSize().background(lv.bg).clickable(enabled = false) {}.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(lv.sf2).clickable(onClick = onStop),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = lv.tx) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Азимут ${bearing?.roundToInt() ?: "—"}°" + (totalDist?.let { " · ${formatDistance(it)}" } ?: ""),
                    color = lv.tx,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp),
                )
                Text(az.label, color = lv.tx2, maxLines = 1, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(260.dp).border(3.dp, lv.sf3, CircleShape), contentAlignment = Alignment.Center) {
            if (heading != null) {
                Box(Modifier.size(260.dp).rotate(-(heading ?: 0.0).toFloat()), contentAlignment = Alignment.TopCenter) {
                    Text(
                        "С",
                        color = lv.rT,
                        style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Canvas(Modifier.size(170.dp).rotate(arrow)) {
                val w = size.width
                val h = size.height
                val path = Path().apply {
                    moveTo(w / 2, 0f)
                    lineTo(w * 0.9f, h)
                    lineTo(w / 2, h * 0.72f)
                    lineTo(w * 0.1f, h)
                    close()
                }
                drawPath(path, color = if (arrived) lv.a else lv.gT)
            }
        }
        if (heading == null) {
            Text(
                "Компаса в телефоне нет: стрелка показывает на север сверху",
                color = lv.tx2,
                textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            if (arrived) "вы на месте" else "осталось",
            color = lv.tx2,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            dist?.let { formatDistance(it) } ?: "жду GPS…",
            color = lv.tx,
            style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 62.sp),
        )
        if (!arrived && abs(xt) >= 3) {
            val left = xt > 0
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(18.dp)).background(lv.aS)
                    .border(2.dp, lv.a, RoundedCornerShape(18.dp)).padding(14.dp),
            ) {
                Text(
                    "Ушли ${if (left) "влево" else "вправо"} на ${abs(xt).roundToInt()} м",
                    color = lv.aT,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp),
                )
                Text(
                    "Возьмите чуть ${if (left) "правее" else "левее"}",
                    color = lv.tx,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        LvBigButton(text = "Стоп", icon = Icons.Rounded.Stop, container = lv.r, onClick = onStop)
    }
}

/** Кнопка «Азимут» рядом с плашкой «Вести сюда». */
@Composable
internal fun AzimuthChip(onClick: () -> Unit) {
    val lv = LocalLv.current
    Row(
        Modifier.height(44.dp).shadow(4.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(lv.ov)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Explore, contentDescription = null, tint = lv.gT, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text("Азимут", color = lv.tx, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp))
    }
}

// ------------------------------------------------------ сторож границы ---

/**
 * Сторож границы поверх карты: вдали от границы — маленькая зелёная плашка, ближе 20 м —
 * жёлтый экран с крупными метрами и вибрацией, за границей — красный.
 */
@Composable
internal fun GuardLayer(guard: GuardState, me: LatLon?, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val lv = LocalLv.current
    val haptic = LocalHapticFeedback.current
    val inside = me?.let { guard.shape.contains(it.lat, it.lon) }
    val d = me?.let { guard.shape.distanceToBorderMeters(it) }
    val alarm = me != null && (inside == false || (d ?: 99.0) <= GUARD_NEAR_M)
    // «Свернуть» прячет жёлтый экран, пока не отойдёте от границы и не подойдёте снова
    var collapsed by remember(guard) { mutableStateOf(false) }
    val clear = !alarm && (d ?: 0.0) > GUARD_CLEAR_M && inside == true
    LaunchedEffect(clear) { if (clear) collapsed = false }
    LaunchedEffect(alarm, inside) {
        if (alarm) repeat(3) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(350)
        }
    }

    if (alarm && !collapsed) {
        val out = inside == false
        val bg = if (out) lv.r else lv.a
        val fg = if (out) Color.White else Ink
        Column(Modifier.fillMaxSize().background(bg).clickable(enabled = false) {}.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(fg.copy(alpha = 0.12f)).clickable { collapsed = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, contentDescription = "Свернуть", tint = fg) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Сторож границы", color = fg, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 18.sp))
                    Text(guard.title, color = fg.copy(alpha = 0.8f), style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 28.dp)) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = fg, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (out) "Вы за границей" else "Граница рядом",
                    color = fg,
                    style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 24.sp),
                )
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "${(d ?: 0.0).roundToInt()}",
                    color = fg,
                    style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Black, fontSize = 120.sp, lineHeight = 124.sp),
                )
                Text(" м", color = fg, style = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 36.sp), modifier = Modifier.padding(bottom = 18.dp))
            }
            Text(
                if (out) "уже за границей, вернитесь назад" else "до границы, не идите дальше",
                color = fg,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
            )
            Spacer(Modifier.weight(1f))
            LvBigButton(text = "Понятно, свернуть", onClick = { collapsed = true }, container = fg.copy(alpha = 0.14f), content = fg)
            Spacer(Modifier.height(10.dp))
            LvBigButton(text = "Выключить сторожа", onClick = onStop, container = Color.Transparent, content = fg)
        }
    } else {
        // маленькая плашка на карте: сторож работает
        Row(
            modifier
                .shadow(4.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(if (alarm) (if (inside == false) lv.r else lv.a) else lv.g)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val fg = if (alarm && inside != false) Ink else Color.White
            Icon(Icons.Rounded.Shield, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Text(
                when {
                    me == null -> "Сторож: жду GPS…"
                    inside == false -> "Сторож: вы за границей, ${(d ?: 0.0).roundToInt()} м"
                    else -> "Сторож: до границы ${(d ?: 0.0).roundToInt()} м"
                },
                color = fg,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
            )
            Box(Modifier.size(44.dp).clickable(onClick = onStop), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, contentDescription = "Выключить сторожа", tint = fg, modifier = Modifier.size(20.dp))
            }
        }
    }
}
