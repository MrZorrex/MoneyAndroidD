package com.paysplit.app.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.PaySplitApplication
import com.paysplit.app.data.repository.PaySplitRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.security.MessageDigest

/** Состояние экрана блокировки. */
data class LockUiState(
    val pinHash: String? = null,
    val biometricEnabled: Boolean = false,
)

/** Проверяет PIN-код и состояние биометрии. */
class LockViewModel(private val repository: PaySplitRepository) : ViewModel() {

    val state: StateFlow<LockUiState> = repository.data.map { data ->
        LockUiState(
            pinHash = data.settings.pinHash,
            biometricEnabled = data.settings.biometricEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LockUiState())

    fun verify(pin: String, hash: String?): Boolean =
        hash != null && hashPin(pin) == hash

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("PaySplit::$pin").toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private const val MAX_PIN_LENGTH = 6
private const val MAX_ATTEMPTS = 5

/**
 * ЭКРАН 10 — Экран блокировки: ввод PIN-кода или отпечатка пальца.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { PaySplitApplication.repository(context) }
    val viewModel: LockViewModel = viewModel(
        factory = remember(repository) {
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LockViewModel(repository) as T
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempts by remember { mutableIntStateOf(0) }
    var shaking by remember { mutableStateOf(false) }

    fun submit(value: String) {
        if (value.length < 4) return
        if (viewModel.verify(value, state.pinHash)) {
            onUnlocked()
        } else {
            attempts += 1
            error = if (attempts >= MAX_ATTEMPTS) {
                "Неверный PIN. Попыток осталось: 0. Попробуйте позже."
            } else {
                "Неверный PIN-код. Осталось попыток: ${MAX_ATTEMPTS - attempts}"
            }
            pin = ""
            shaking = true
        }
    }

    LaunchedEffect(shaking) {
        if (shaking) {
            kotlinx.coroutines.delay(400)
            shaking = false
        }
    }

    // Автоматический запрос отпечатка пальца при появлении экрана
    LaunchedEffect(Unit) {
        if (state.biometricEnabled && state.pinHash != null) {
            showBiometricPrompt(
                context = context,
                onSuccess = onUnlocked,
                onError = { error = "Отпечаток пальца не распознан, введите PIN" },
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "🔐", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "PaySplit",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Введите PIN-код для входа",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            // Индикатор введённых цифр
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (shaking) 0.3f else 1f,
                label = "shake",
            )
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(indicatorAlpha),
            ) {
                repeat(MAX_PIN_LENGTH) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (index < pin.length) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(14.dp),
                        ) {}
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))

            // --- Клавиатура ---
            val keys = listOf(
                "1", "2", "3",
                "4", "5", "6",
                "7", "8", "9",
                "", "0", "⌫",
            )
            keys.chunked(3).forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowKeys.forEach { key ->
                        when (key) {
                            "" -> Spacer(Modifier.size(72.dp))
                            "⌫" -> KeyButton(
                                text = key,
                                onClick = {
                                    error = null
                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                },
                            )

                            else -> KeyButton(
                                text = key,
                                onClick = {
                                    error = null
                                    if (pin.length < MAX_PIN_LENGTH) {
                                        pin += key
                                        if (pin.length >= 4) submit(pin)
                                    }
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (state.biometricEnabled) {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        showBiometricPrompt(
                            context = context,
                            onSuccess = onUnlocked,
                            onError = { error = "Не удалось распознать отпечаток пальца" },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("👆 Войти по отпечатку пальца")
                }
            }
        }
    }
}

@Composable
private fun KeyButton(text: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(72.dp)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Системный запрос отпечатка пальца. */
private fun showBiometricPrompt(
    context: Context,
    onSuccess: () -> Unit,
    onError: () -> Unit,
) {
    val activity = context as? FragmentActivity ?: run {
        onError()
        return
    }
    val manager = BiometricManager.from(context)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
    if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onError()
        return
    }
    val executor = ContextCompat.getMainExecutor(context)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }

        override fun onAuthenticationError(code: Int, message: CharSequence) {
            onError()
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("PaySplit")
            .setSubtitle("Вход по отпечатку пальца")
            .setNegativeButtonText("Ввести PIN")
            .setAllowedAuthenticators(authenticators)
            .build(),
    )
}
