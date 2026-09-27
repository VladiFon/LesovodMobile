package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lesovod.mobile.ui.components.SecondaryButton
import com.lesovod.mobile.ui.theme.Spacing
import com.lesovod.mobile.ui.theme.softCard

/** Часть экрана 1 редизайна «Поляна» (docs/SCREENS.md) — «Как получить доступ». */
@Composable
fun RegistrationScreen(onBackToLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        Text(
            text = "Как получить доступ",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.l),
        )

        Text(
            text = "Учётные записи для рабочих создаёт мастер или лесничий вашего " +
                "лесничества — самостоятельная регистрация в приложении не предусмотрена.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .softCard()
                .padding(Spacing.l)
                .padding(top = Spacing.xl),
        ) {
            Text("Чтобы начать работать в приложении:", style = MaterialTheme.typography.titleSmall)
            Text(
                "1. Обратитесь к мастеру или лесничему.\n" +
                    "2. Сообщите свои ФИО и должность.\n" +
                    "3. Вам выдадут логин и PIN-код для входа.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }

        SecondaryButton(
            text = "Вернуться ко входу",
            onClick = onBackToLogin,
            modifier = Modifier.padding(top = Spacing.xl),
        )
    }
}
