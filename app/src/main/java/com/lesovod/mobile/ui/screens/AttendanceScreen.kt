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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.data.network.dto.AttendanceStatus
import com.lesovod.mobile.ui.bot.AttendanceUiState
import com.lesovod.mobile.ui.bot.AttendanceViewModel
import com.lesovod.mobile.ui.theme.ForestAccent
import com.lesovod.mobile.ui.theme.ForestError
import com.lesovod.mobile.ui.theme.ForestSuccess

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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "Отметка присутствия",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            LatestStatusCard(state = state)

            if (state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            val queuedOffline = state.queuedOffline
            if (queuedOffline != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ForestAccent.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Нет сети — отметка «${queuedOffline.displayName}» сохранена на устройстве и отправится, как только появится связь.",
                        color = ForestAccent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Text(
                "Отметьте, работаете ли вы сегодня — координаты приложатся автоматически, если разрешён доступ к геолокации.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            StatusButton(
                status = AttendanceStatus.WORKING,
                color = ForestSuccess,
                state = state,
                onClick = viewModel::mark,
            )
            StatusButton(
                status = AttendanceStatus.NOT_WORKING,
                color = ForestAccent,
                state = state,
                onClick = viewModel::mark,
            )
            StatusButton(
                status = AttendanceStatus.SICK_LEAVE,
                color = ForestError,
                state = state,
                onClick = viewModel::mark,
            )
        }
    }
}

@Composable
private fun LatestStatusCard(state: AttendanceUiState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Текущий статус",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                state.isLoadingLatest -> Text("Загрузка…", style = MaterialTheme.typography.titleMedium)
                state.latest == null -> Text(
                    "Отметок ещё не было",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                else -> {
                    val status = AttendanceStatus.fromWireValue(state.latest.status)
                    Text(
                        status?.displayName ?: state.latest.status,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
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
}

@Composable
private fun StatusButton(
    status: AttendanceStatus,
    color: Color,
    state: AttendanceUiState,
    onClick: (AttendanceStatus) -> Unit,
) {
    val isThisSubmitting = state.isSubmitting && state.submittingStatus == status

    Button(
        onClick = { onClick(status) },
        enabled = !state.isSubmitting,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        if (isThisSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(status.displayName, style = MaterialTheme.typography.titleMedium)
        }
    }
}
