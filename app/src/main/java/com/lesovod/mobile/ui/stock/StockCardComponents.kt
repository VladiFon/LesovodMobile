package com.lesovod.mobile.ui.stock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.data.network.dto.VolumeBreakdownDto
import com.lesovod.mobile.ui.theme.ForestAccent
import com.lesovod.mobile.ui.theme.ForestBackground
import com.lesovod.mobile.ui.theme.ForestError
import com.lesovod.mobile.ui.theme.ForestOnPrimary
import com.lesovod.mobile.ui.theme.ForestOnPrimaryMuted
import com.lesovod.mobile.ui.theme.ForestOutline
import com.lesovod.mobile.ui.theme.ForestPrimary
import com.lesovod.mobile.ui.theme.ForestSurface
import com.lesovod.mobile.ui.theme.ForestTextMuted
import com.lesovod.mobile.ui.theme.LesovodTheme
import com.lesovod.mobile.ui.theme.Manrope

/** Одна категория внутри карточки породы: заголовок с лимитом, панель остатка, два бара, баннер расхождения. */
@Composable
fun WoodSection(title: String, stock: WoodStock, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                title,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
                color = ForestPrimary,
            )
            Row {
                Text(
                    "Лимит ",
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 16.sp),
                    color = ForestTextMuted,
                )
                Text(
                    "${formatVolume(stock.limit)} м³",
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFeatureSettings = "tnum"),
                    color = ForestPrimary,
                )
            }
        }

        RemainderPanel(
            remainderText = formatVolume(stock.remainder),
            percent = stock.percent,
            remainderColor = if (stock.remainder < 0) ForestError else ForestPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SourceBar(
                label = "Наряд",
                valueText = "${formatVolume(stock.naryad)} м³",
                valueColor = ForestPrimary,
                segments = listOf(stock.naryadFraction to ForestPrimary),
            )
            SourceBar(
                label = "ЕГАИС",
                valueText = "${formatVolume(stock.egais)} м³",
                valueColor = if (stock.isDiscrepancy) ForestError else ForestPrimary,
                segments = if (stock.isDiscrepancy) {
                    listOf(
                        stock.naryadFraction to ForestAccent,
                        (stock.egaisFraction - stock.naryadFraction) to ForestError,
                    )
                } else {
                    listOf(stock.egaisFraction to ForestAccent)
                },
            )
        }

        if (stock.isDiscrepancy) {
            val sign = if (stock.difference >= 0) "+" else ""
            DiscrepancyBanner(differenceText = "$sign${formatVolume(stock.difference)} м³")
        }
    }
}

/** Карточка одной породы: заголовок, секции деловой древесины/дров, итоговый подвал. */
@Composable
fun SpeciesCard(species: SpeciesStock, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = ForestSurface,
        border = BorderStroke(1.dp, ForestOutline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                species.poroda,
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 26.sp),
                color = ForestPrimary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 2.dp),
            )

            species.delovaya?.let { WoodSection(title = "Деловая древесина", stock = it) }

            if (species.delovaya != null && species.drova != null) {
                HorizontalDivider(color = ForestOutline, modifier = Modifier.padding(horizontal = 16.dp))
            }

            species.drova?.let { WoodSection(title = "Дрова", stock = it) }

            SpeciesTotalFooter(total = species.totalRemainder)
        }
    }
}

/** Тёмный подвал карточки — суммарный остаток по породе (деловая + дрова). */
@Composable
fun SpeciesTotalFooter(total: Double, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .background(ForestPrimary)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column {
            Text(
                "Итого остаток",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                color = ForestOnPrimary,
            )
            Text(
                "деловая + дрова",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp),
                color = ForestOnPrimaryMuted,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                formatVolume(total),
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 34.sp, fontFeatureSettings = "tnum"),
                color = ForestOnPrimary,
            )
            Text(
                "м³",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
                color = ForestOnPrimaryMuted,
                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp),
            )
        }
    }
}

// Пример данных из ТЗ — используются и в превью, и совпадение чисел проверяется юнит-тестами.
internal val previewSosna = SpeciesStock(
    poroda = "Сосна",
    delovaya = VolumeBreakdownDto(limit = 120.0, faktNaryad = 74.5, faktEgais = 74.5, ostatokSafe = 45.5).toWoodStock(),
    drova = VolumeBreakdownDto(limit = 40.0, faktNaryad = 22.0, faktEgais = 25.4, ostatokSafe = 14.6).toWoodStock(),
)

internal val previewBereza = SpeciesStock(
    poroda = "Берёза",
    delovaya = VolumeBreakdownDto(limit = 30.0, faktNaryad = 12.8, faktEgais = 9.6, ostatokSafe = 17.2).toWoodStock(),
    drova = VolumeBreakdownDto(limit = 55.0, faktNaryad = 31.2, faktEgais = 31.2, ostatokSafe = 23.8).toWoodStock(),
)

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun WoodSectionDiscrepancyPreview() {
    LesovodTheme {
        WoodSection(title = "Дрова", stock = previewSosna.drova!!, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun WoodSectionNoDiscrepancyPreview() {
    LesovodTheme {
        WoodSection(title = "Деловая древесина", stock = previewBereza.delovaya!!, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun SpeciesCardSosnaPreview() {
    LesovodTheme {
        SpeciesCard(species = previewSosna, modifier = Modifier.background(ForestBackground).padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun SpeciesCardBerezaPreview() {
    LesovodTheme {
        SpeciesCard(species = previewBereza, modifier = Modifier.background(ForestBackground).padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A4331)
@Composable
private fun SpeciesTotalFooterPreview() {
    LesovodTheme {
        SpeciesTotalFooter(total = previewSosna.totalRemainder)
    }
}
