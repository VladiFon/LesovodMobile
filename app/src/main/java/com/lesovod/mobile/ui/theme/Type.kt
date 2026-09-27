package com.lesovod.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.R

/*
 * Типографика редизайна «Поляна» (docs/DESIGN_TOKENS.md): пара Unbounded (заголовки) +
 * Manrope (интерфейс). Файлы шрифта Unbounded нужно скачать с Google Fonts (SIL OFL) и
 * положить в res/font/ — из этой облачной среды это сделать нельзя (сеть на
 * fonts.google.com закрыта политикой окружения). До появления файлов заголовочные роли
 * временно используют Manrope ExtraBold — визуально отличается от финального макета, но
 * ничего не ломает; как только шрифты появятся в res/font/, замените FontFamily на Unbounded
 * в displayLarge/titleLarge/titleMedium ниже.
 */
@OptIn(ExperimentalTextApi::class)
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/** TODO(шрифт): заменить на настоящий Unbounded, когда файлы появятся в res/font/. */
val UnboundedFallback = Manrope

/** tabular figures — для всех мест, где цифры стоят в столбик (объёмы, время, счётчики). */
val TabularNums = TextStyle(fontFeatureSettings = "tnum")

val PolyanaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = UnboundedFallback, fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.005).em,
    ),
    titleLarge = TextStyle(
        fontFamily = UnboundedFallback, fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.005).em,
    ),
    titleMedium = TextStyle(
        fontFamily = UnboundedFallback, fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp, lineHeight = 26.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 17.sp, lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 24.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 11.5.sp, lineHeight = 16.sp, letterSpacing = 0.07.em,
    ),
)
