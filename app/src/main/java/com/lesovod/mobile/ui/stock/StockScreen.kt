package com.lesovod.mobile.ui.stock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.DelyankaDto
import com.lesovod.mobile.data.network.dto.PorodaRemainingDto
import com.lesovod.mobile.data.network.dto.RemainingResponseDto
import com.lesovod.mobile.data.network.dto.VolumeBreakdownDto
import com.lesovod.mobile.ui.bot.StockUiState
import com.lesovod.mobile.ui.bot.StockViewModel
import com.lesovod.mobile.ui.components.ScreenTitle
import com.lesovod.mobile.ui.theme.ForestBackground
import com.lesovod.mobile.ui.theme.ForestError
import com.lesovod.mobile.ui.theme.ForestErrorBorder
import com.lesovod.mobile.ui.theme.ForestErrorContainer
import com.lesovod.mobile.ui.theme.ForestTextMuted
import com.lesovod.mobile.ui.theme.LesovodTheme
import com.lesovod.mobile.ui.theme.Manrope

/**
 * Состояние области результата — производится из [StockUiState], сама вью-модель и её запросы
 * не меняются: экран лишь читает уже существующие isLoadingRemaining/remaining/error.
 */
sealed interface StockResultState {
    data object Idle : StockResultState
    data object Loading : StockResultState
    data class Result(val response: RemainingResponseDto) : StockResultState
    data object NotFound : StockResultState
}

private fun StockUiState.toResultState(): StockResultState = when {
    isLoadingRemaining -> StockResultState.Loading
    remaining == null -> StockResultState.Idle
    !remaining.found -> StockResultState.NotFound
    else -> StockResultState.Result(remaining)
}

@Composable
fun StockScreen(viewModel: StockViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    StockScreenContent(
        state = state,
        onKvartalChange = viewModel::onKvartalChange,
        onLoadDelyanki = viewModel::loadDelyanki,
        onVydelChange = viewModel::onVydelChange,
        onLesosekaChange = viewModel::onLesosekaChange,
        onSelectDelyanka = viewModel::selectDelyanka,
        onCheckRemaining = viewModel::loadRemaining,
    )
}

@Composable
private fun StockScreenContent(
    state: StockUiState,
    onKvartalChange: (String) -> Unit,
    onLoadDelyanki: () -> Unit,
    onVydelChange: (String) -> Unit,
    onLesosekaChange: (String) -> Unit,
    onSelectDelyanka: (DelyankaDto) -> Unit,
    onCheckRemaining: () -> Unit,
) {
    val resultState = state.toResultState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForestBackground)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle("Остатки по делянке")

        PlotSearchBlock(
            kvartal = state.kvartal,
            onKvartalChange = onKvartalChange,
            onLoadDelyanki = onLoadDelyanki,
            isLoadingDelyanki = state.isLoadingDelyanki,
            delyanki = state.delyanki,
            vydel = state.vydel,
            onVydelChange = onVydelChange,
            lesoseka = state.lesoseka,
            onLesosekaChange = onLesosekaChange,
            onSelectDelyanka = onSelectDelyanka,
            onCheckRemaining = onCheckRemaining,
            isCheckingRemaining = state.isLoadingRemaining,
        )

        state.error?.let { message ->
            StockErrorCard(
                message = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
        }

        if (resultState !is StockResultState.Idle) {
            Text(
                plotSubtitle(state.kvartal, state.vydel, state.lesoseka),
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                color = ForestTextMuted,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp),
            )
        }

        when (resultState) {
            StockResultState.Idle -> Unit
            StockResultState.Loading -> {
                SkeletonCard(modifier = Modifier.padding(horizontal = 16.dp))
            }
            StockResultState.NotFound -> {
                NotFoundCard(
                    kvartal = state.kvartal,
                    vydel = state.vydel,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            is StockResultState.Result -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    resultState.response.grouped?.forEach { (poroda, group) ->
                        SpeciesCard(species = toSpeciesStock(poroda, group))
                    }
                }
            }
        }

        StockFooterDates(
            lastUpdate = state.remaining?.lastUpdate,
            egaisImportedAt = state.remaining?.egaisImportedAt,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 32.dp),
        )
    }
}

private fun plotSubtitle(kvartal: String, vydel: String, lesoseka: String): String {
    val base = "Кв. $kvartal · выд. $vydel"
    return if (lesoseka.isNotBlank()) "$base · лес. $lesoseka" else base
}

/** Существующая обработка настоящей сетевой ошибки (загрузка делянок или остатка) — логика та же, стиль под новую палитру. */
@Composable
private fun StockErrorCard(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(ForestErrorContainer, RoundedCornerShape(12.dp))
            .border(1.dp, ForestErrorBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            message,
            style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp),
            color = ForestError,
        )
    }
}

@Composable
private fun StockFooterDates(lastUpdate: String?, egaisImportedAt: String?, modifier: Modifier = Modifier) {
    if (lastUpdate == null && egaisImportedAt == null) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lastUpdate?.let {
            Text(
                "Наряды обновлены: ${formatServerDateTime(it)}",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp),
                color = ForestTextMuted,
            )
        }
        egaisImportedAt?.let {
            Text(
                "ЕГАИС загружен: ${formatServerDateTime(it)}",
                style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 13.sp),
                color = ForestTextMuted,
            )
        }
    }
}

private val previewFoundResponse = RemainingResponseDto(
    found = true,
    grouped = linkedMapOf(
        "Сосна" to PorodaRemainingDto(
            delovaya = VolumeBreakdownDto(limit = 120.0, faktNaryad = 74.5, faktEgais = 74.5, ostatokSafe = 45.5),
            drova = VolumeBreakdownDto(limit = 40.0, faktNaryad = 22.0, faktEgais = 25.4, ostatokSafe = 14.6),
        ),
        "Берёза" to PorodaRemainingDto(
            delovaya = VolumeBreakdownDto(limit = 30.0, faktNaryad = 12.8, faktEgais = 9.6, ostatokSafe = 17.2),
            drova = VolumeBreakdownDto(limit = 55.0, faktNaryad = 31.2, faktEgais = 31.2, ostatokSafe = 23.8),
        ),
    ),
    lastUpdate = "2026-09-22T17:40:00",
    egaisImportedAt = "2026-09-23T06:15:00",
)

private val previewChips = listOf(
    DelyankaDto(vydel = "12", lesosekaNomer = "4"),
    DelyankaDto(vydel = "12", lesosekaNomer = "5"),
)

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2, heightDp = 1400)
@Composable
private fun StockScreenResultPreview() {
    LesovodTheme {
        StockScreenContent(
            state = StockUiState(
                kvartal = "47",
                vydel = "12",
                lesoseka = "4",
                delyanki = previewChips,
                remaining = previewFoundResponse,
            ),
            onKvartalChange = {},
            onLoadDelyanki = {},
            onVydelChange = {},
            onLesosekaChange = {},
            onSelectDelyanka = {},
            onCheckRemaining = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2, heightDp = 900)
@Composable
private fun StockScreenLoadingPreview() {
    LesovodTheme {
        StockScreenContent(
            state = StockUiState(
                kvartal = "47",
                vydel = "12",
                lesoseka = "4",
                delyanki = previewChips,
                isLoadingRemaining = true,
            ),
            onKvartalChange = {},
            onLoadDelyanki = {},
            onVydelChange = {},
            onLesosekaChange = {},
            onSelectDelyanka = {},
            onCheckRemaining = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCF9F2, heightDp = 900)
@Composable
private fun StockScreenNotFoundPreview() {
    LesovodTheme {
        StockScreenContent(
            state = StockUiState(
                kvartal = "47",
                vydel = "99",
                lesoseka = "",
                delyanki = previewChips,
                remaining = RemainingResponseDto(found = false),
            ),
            onKvartalChange = {},
            onLoadDelyanki = {},
            onVydelChange = {},
            onLesosekaChange = {},
            onSelectDelyanka = {},
            onCheckRemaining = {},
        )
    }
}
