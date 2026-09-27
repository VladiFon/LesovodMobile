package com.lesovod.mobile.ui.lesokultury

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing

@Composable
fun InventarizatsiyaScreen(onBack: () -> Unit, viewModel: InventarizatsiyaViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Инвентаризация лесных культур", style = MaterialTheme.typography.titleSmall)
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            val result = state.result
            if (result != null) {
                ServerResultCard(result = result, title = "Инвентаризация сохранена", onNewCard = viewModel::newCard)
                return@Column
            }

            if (state.isLoadingReference) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                return@Column
            }

            UchastokPicker(uchastki = state.uchastki, selected = state.selectedUchastok, onSelect = viewModel::selectUchastok)

            Text("Год обследования", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            GodPicker(god = state.god, onSelect = viewModel::selectGod)

            ProbyList(
                proby = state.proby,
                enabled = !state.isSubmitting,
                onNomerChange = viewModel::onProbaNomerChange,
                onRazmerChange = viewModel::onProbaRazmerChange,
                onRemove = viewModel::removeProba,
                onAdd = viewModel::addProba,
            )

            RezultatySection(
                porody = state.porody,
                rezultaty = state.rezultaty,
                enabled = !state.isSubmitting,
                onTap = viewModel::tapPoroda,
                onUndo = viewModel::undoPoroda,
                onVysazhenoChange = viewModel::onVysazhenoChange,
                onRemove = viewModel::removeRezultat,
            )

            PreviewCard(preview = state.preview)

            if (state.error != null) {
                StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
            }

            PrimaryButton(
                text = "Сохранить",
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.padding(bottom = Spacing.xl),
                icon = if (state.isSubmitting) {
                    { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
                } else null,
            )
        }
    }
}
