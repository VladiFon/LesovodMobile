package com.lesovod.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lesovod.mobile.ui.auth.AuthViewModel
import com.lesovod.mobile.ui.auth.LoginUiState
import com.lesovod.mobile.ui.components.ChipTone
import com.lesovod.mobile.ui.components.PrimaryButton
import com.lesovod.mobile.ui.components.StatusChip
import com.lesovod.mobile.ui.theme.Spacing

/** Экран 1 редизайна «Поляна» (docs/SCREENS.md) — «Вход и регистрация» (форма входа). */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    var login by remember { mutableStateOf(viewModel.rememberedLogin ?: "") }
    var pin by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
            viewModel.resetState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ЛЕСОВОД", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Вход в систему",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.xxl),
        )

        OutlinedTextField(
            value = login,
            onValueChange = { login = it },
            label = { Text("Логин") },
            singleLine = true,
            enabled = uiState !is LoginUiState.Loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter { c -> c.isDigit() } },
            label = { Text("PIN-код") },
            singleLine = true,
            enabled = uiState !is LoginUiState.Loading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.m),
        )
        Text(
            "Забыли PIN? Попросите лесничего сбросить его на сайте: Сотрудники → Сбросить PIN.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.xs),
        )

        if (uiState is LoginUiState.Error) {
            StatusChip(
                text = (uiState as LoginUiState.Error).message,
                tone = ChipTone.ERROR,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.m),
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = { rememberMe = it },
                enabled = uiState !is LoginUiState.Loading,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Text("Запомнить меня", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        PrimaryButton(
            text = "Войти",
            onClick = { viewModel.login(login, pin, rememberMe) },
            enabled = uiState !is LoginUiState.Loading,
            modifier = Modifier.padding(top = Spacing.xl),
            icon = if (uiState is LoginUiState.Loading) {
                { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else null,
        )

        TextButton(onClick = onRegisterClick, modifier = Modifier.padding(top = Spacing.s)) {
            Text("Нет аккаунта? Как получить доступ")
        }
    }
}
