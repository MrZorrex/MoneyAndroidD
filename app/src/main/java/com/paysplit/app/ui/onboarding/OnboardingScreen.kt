package com.paysplit.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.domain.DefaultCategories
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.OutlinedNumberField
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.SectionHeader
import com.paysplit.app.util.Formatters

/**
 * ЭКРАН 1 — Онбординг (2 шага):
 * приветствие → шаблон категорий.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            text = "PaySplit",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Распределяй зарплату. Контролируй расходы.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        LinearProgressIndicator(
            progress = { (state.step + 1) / state.totalSteps.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Шаг ${state.step + 1} из ${state.totalSteps}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        when (state.step) {
            0 -> WelcomeStep()
            1 -> TemplateStep(
                useTemplate = state.useTemplate,
                onUseTemplateChange = viewModel::setUseTemplate,
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.step > 0) {
                OutlinedButton(
                    onClick = viewModel::back,
                    modifier = Modifier.weight(1f),
                ) { Text("Назад") }
            }
            Button(
                onClick = {
                    if (state.step == state.totalSteps - 1) viewModel.finish()
                    else viewModel.next()
                },
                enabled = state.canProceed,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = if (state.step == state.totalSteps - 1) "Начать 🚀" else "Далее",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column {
        Text(text = "👋", fontSize = 56.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Как работает PaySplit",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(12.dp))
        listOf(
            "💰" to "Каждая зарплата автоматически распределяется по вашим счетам " +
                "пропорционально плану.",
            "🧾" to "Расходы привязываются к счетам — всегда видно, сколько осталось.",
            "📊" to "Графики и аналитика помогают понять, куда уходят деньги.",
            "🔒" to "Все данные хранятся только на телефоне. Интернет не нужен.",
        ).forEach { (icon, text) ->
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(text = icon, fontSize = 24.sp)
                Spacer(Modifier.padding(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IncomeStep(incomeText: String, onIncomeChange: (String) -> Unit) {
    Column {
        SectionHeader(
            title = "Примерный доход в месяц",
            subtitle = "Нужен для отображения прогресса «получено из плана»",
        )
        Spacer(Modifier.height(16.dp))
        MoneyInputField(
            value = incomeText,
            onValueChange = onIncomeChange,
            label = "Доход в месяц, ₽",
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Это приблизительная оценка. Точные суммы вы вводите при каждом поступлении.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaydayStep(paydayText: String, onPaydayChange: (String) -> Unit) {
    Column {
        SectionHeader(
            title = "Сколько раз приходит зарплата?",
            subtitle = "От 1 до 10 раз в месяц, в разные дни и разными суммами",
        )
        Spacer(Modifier.height(16.dp))
        OutlinedNumberField(
            value = paydayText,
            onValueChange = onPaydayChange,
            label = "Выплат в месяц",
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Приложение будет показывать «поступлений: N из ${paydayText.ifBlank { "?" }}».",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemplateStep(useTemplate: Boolean, onUseTemplateChange: (Boolean) -> Unit) {
    Column {
        SectionHeader(title = "С чего начнём?")
        Spacer(Modifier.height(12.dp))

        PayCard(
            containerColor = if (useTemplate) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📦", fontSize = 32.sp)
                Spacer(Modifier.padding(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Загрузить шаблон категорий",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "12 готовых счетов: жильё, продукты, авто, накопления…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Сумма планов: ${Formatters.money(DefaultCategories.totalPlan)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onUseTemplateChange(true) },
                enabled = !useTemplate,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (useTemplate) "Выбрано ✓" else "Выбрать шаблон") }
        }

        Spacer(Modifier.height(12.dp))

        PayCard(
            containerColor = if (!useTemplate) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "✏️", fontSize = 32.sp)
                Spacer(Modifier.padding(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Создать категории самому",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Настроите счета и планы вручную в разделе «Категории»",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onUseTemplateChange(false) },
                enabled = useTemplate,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (!useTemplate) "Выбрано ✓" else "Создать свои") }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "Шаблон можно изменить или сбросить в любой момент в настройках.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
