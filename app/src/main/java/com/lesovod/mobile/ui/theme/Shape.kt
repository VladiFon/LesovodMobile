package com.lesovod.mobile.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val RadiusChip = 10.dp
val RadiusField = 16.dp
val RadiusCard = 20.dp
val RadiusSheet = 28.dp

val PolyanaShapes = Shapes(
    extraSmall = RoundedCornerShape(RadiusChip),
    small = RoundedCornerShape(RadiusField),
    medium = RoundedCornerShape(RadiusCard),
    large = RoundedCornerShape(RadiusSheet),
    extraLarge = RoundedCornerShape(RadiusSheet),
)

val PillShape = CircleShape

/** Верхние углы скруглены, нижние — острые: для bottom sheet и hero-карточек у нижней панели. */
fun topRoundedShape(radius: Dp = RadiusSheet) = RoundedCornerShape(
    topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp,
)
