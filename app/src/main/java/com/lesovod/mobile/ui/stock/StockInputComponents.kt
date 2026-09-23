package com.lesovod.mobile.ui.stock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.ui.theme.ForestBackground
import com.lesovod.mobile.ui.theme.ForestInputOutline
import com.lesovod.mobile.ui.theme.ForestOnPrimary
import com.lesovod.mobile.ui.theme.ForestPrimary
import com.lesovod.mobile.ui.theme.ForestSurface
import com.lesovod.mobile.ui.theme.ForestTextMuted
import com.lesovod.mobile.ui.theme.ForestTonalButton
import com.lesovod.mobile.ui.theme.LesovodTheme
import com.lesovod.mobile.ui.theme.Manrope

/** Подписанное поле ввода: подпись всегда сверху, значение крупно снизу, пусто = «—». */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
    keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .background(ForestSurface, RoundedCornerShape(16.dp))
            .border(1.5.dp, ForestInputOutline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            color = ForestTextMuted,
        )
        Box {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = ForestPrimary),
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(ForestPrimary),
            )
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                    color = ForestTextMuted,
                )
            }
        }
    }
}

/** Горизонтальная прокрутка чипов найденных делянок — тап подставляет выдел/лесосеку и запускает проверку. */
@Composable
fun PlotChipsRow(
    delyanki: List<DelyankaDto>,
    selectedVydel: String,
    selectedLesoseka: String,
    onSelect: (DelyankaDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(delyanki, key = { "${it.vydel}|${it.lesosekaNomer.orEmpty()}" }) { delyanka ->
            val label = if (delyanka.lesosekaNomer.isNullOrBlank()) {
                "выд. ${delyanka.vydel}"
            } else {
                "выд. ${delyanka.vydel} / лес. ${delyanka.lesosekaNomer}"
            }
            val selected = delyanka.vydel == selectedVydel && delyanka.lesosekaNomer.orEmpty() == selectedLesoseka
            PlotChip(label = label, selected = selected, onClick = { onSelect(delyanka) })
        }
    }
}

@Composable
private fun PlotChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) ForestPrimary else ForestSurface,
        border = if (selected) null else BorderStroke(1.5.dp, ForestInputOutline),
        modifier = Modifier.height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = ForestOnPrimary, modifier = Modifier.size(20.dp))
            }
            Text(
                label,
                style = TextStyle(
                    fontFamily = Manrope,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 17.sp,
                ),
                color = if (selected) ForestOnPrimary else ForestPrimary,
            )
        }
    }
}

/** Блок поиска делянки: квартал + список делянок → чипы → выдел/лесосека → «Проверить остаток». */
@Composable
fun PlotSearchBlock(
    kvartal: String,
    onKvartalChange: (String) -> Unit,
    onLoadDelyanki: () -> Unit,
    isLoadingDelyanki: Boolean,
    delyanki: List<DelyankaDto>,
    vydel: String,
    onVydelChange: (String) -> Unit,
    lesoseka: String,
    onLesosekaChange: (String) -> Unit,
    onSelectDelyanka: (DelyankaDto) -> Unit,
    onCheckRemaining: () -> Unit,
    isCheckingRemaining: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            LabeledField(
                label = "Квартал",
                value = kvartal,
                onValueChange = onKvartalChange,
                modifier = Modifier.weight(1f),
            )
            DelyankiButton(onClick = onLoadDelyanki, loading = isLoadingDelyanki)
        }

        if (delyanki.isNotEmpty()) {
            PlotChipsRow(
                delyanki = delyanki,
                selectedVydel = vydel,
                selectedLesoseka = lesoseka,
                onSelect = onSelectDelyanka,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            LabeledField(
                label = "Выдел",
                value = vydel,
                onValueChange = onVydelChange,
                modifier = Modifier.width(130.dp),
            )
            LabeledField(
                label = "Лесосека (необязательно)",
                value = lesoseka,
                onValueChange = onLesosekaChange,
                modifier = Modifier.weight(1f),
            )
        }

        CheckRemainingButton(
            onClick = onCheckRemaining,
            loading = isCheckingRemaining,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun DelyankiButton(onClick: () -> Unit, loading: Boolean) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ForestTonalButton,
        modifier = Modifier
            .width(132.dp)
            .height(64.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(color = ForestPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Filled.Place, contentDescription = null, tint = ForestPrimary, modifier = Modifier.size(22.dp))
                Text(
                    "Делянки",
                    style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    color = ForestPrimary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun CheckRemainingButton(onClick: () -> Unit, loading: Boolean, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(16.dp),
        color = ForestPrimary,
        modifier = modifier.height(60.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = ForestOnPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 10.dp),
                )
            }
            Text(
                if (loading) "Проверяем остаток…" else "Проверить остаток",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
                color = ForestOnPrimary,
            )
        }
    }
}

private val previewDelyanki = listOf(
    DelyankaDto(vydel = "12", lesosekaNomer = "4"),
    DelyankaDto(vydel = "12", lesosekaNomer = "5"),
)

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun LabeledFieldPreview() {
    LesovodTheme {
        var value by remember { mutableStateOf("47") }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField(label = "Квартал", value = value, onValueChange = { value = it })
            LabeledField(label = "Лесосека (необязательно)", value = "", onValueChange = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun PlotChipsRowPreview() {
    LesovodTheme {
        PlotChipsRow(
            delyanki = previewDelyanki,
            selectedVydel = "12",
            selectedLesoseka = "4",
            onSelect = {},
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2)
@Composable
private fun PlotSearchBlockPreview() {
    LesovodTheme {
        var kvartal by remember { mutableStateOf("47") }
        var vydel by remember { mutableStateOf("12") }
        var lesoseka by remember { mutableStateOf("4") }
        Column(modifier = Modifier.background(ForestBackground).padding(vertical = 16.dp)) {
            PlotSearchBlock(
                kvartal = kvartal,
                onKvartalChange = { kvartal = it },
                onLoadDelyanki = {},
                isLoadingDelyanki = false,
                delyanki = previewDelyanki,
                vydel = vydel,
                onVydelChange = { vydel = it },
                lesoseka = lesoseka,
                onLesosekaChange = { lesoseka = it },
                onSelectDelyanka = {},
                onCheckRemaining = {},
                isCheckingRemaining = false,
            )
        }
    }
}
