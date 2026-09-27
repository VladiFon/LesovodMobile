package com.lesovod.mobile.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.AttendanceStatus
import com.lesovod.mobile.ui.bot.AttendanceUiState
import com.lesovod.mobile.ui.bot.AttendanceViewModel
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.DestructiveButton
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/** Экран 3 редизайна «Поляна» (docs/SCREENS.md) — «Отметка присутствия». */
@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    viewModel: AttendanceViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* mark() сам мягко переживёт отказ — геометка просто не приложится */ }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Spacing.xs)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Отметка присутствия", style = MaterialTheme.typography.titleSmall)
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        ) {
            LatestStatusCard(state = state)

            if (state.error != null) {
                StatusChip(text = state.error.orEmpty(), tone = ChipTone.ERROR)
            }

            state.queuedOffline?.let { queued ->
                StatusChip(
                    text = "Нет сети — «${queued.displayName}» отправится сама",
                    tone = ChipTone.WARN,
                )
            }

            Text(
                "Отметьте, работаете ли вы сегодня — координаты приложатся автоматически, если разрешён доступ к геолокации.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StatusButton(status = AttendanceStatus.WORKING, state = state, onClick = viewModel::mark)
            StatusButton(status = AttendanceStatus.NOT_WORKING, state = state, onClick = viewModel::mark)
            StatusButton(status = AttendanceStatus.SICK_LEAVE, state = state, onClick = viewModel::mark)
        }
    }
}

@Composable
private fun LatestStatusCard(state: AttendanceUiState) {
    Column(modifier = Modifier.fillMaxWidth().softCard().padding(Spacing.l)) {
        Text("Текущий статус", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            state.isLoadingLatest -> Text("Загрузка…", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = Spacing.xs))
            state.latest == null -> Text(
                "Отметок ещё не было",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            else -> {
                val status = AttendanceStatus.fromWireValue(state.latest.status)
                Text(
                    status?.displayName ?: state.latest.status,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Text(
                    state.latest.createdAt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusButton(
    status: AttendanceStatus,
    state: AttendanceUiState,
    onClick: (AttendanceStatus) -> Unit,
) {
    val isThisSubmitting = state.isSubmitting && state.submittingStatus == status
    val enabled = !state.isSubmitting
    val progressIcon: (@Composable () -> Unit)? = if (isThisSubmitting) {
        { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) }
    } else null

    when (status) {
        AttendanceStatus.WORKING -> PrimaryButton(text = status.displayName, onClick = { onClick(status) }, enabled = enabled, icon = progressIcon)
        AttendanceStatus.NOT_WORKING -> SecondaryButton(text = status.displayName, onClick = { onClick(status) }, enabled = enabled, icon = progressIcon)
        AttendanceStatus.SICK_LEAVE -> DestructiveButton(text = status.displayName, onClick = { onClick(status) }, enabled = enabled)
    }
}
