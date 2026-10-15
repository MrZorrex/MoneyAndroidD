package com.paysplit.app.ui.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.PaySplitApplication
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.ChipRow
import com.paysplit.app.ui.components.DateField
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/**
 * ЭКРАН 4 — Добавление расхода.
 */
@Composable
fun AddExpenseScreen(
    expenseId: Long,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { PaySplitApplication.repository(context) }
    val viewModel: AddExpenseViewModel = viewModel(
        factory = remember(expenseId, repository) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AddExpenseViewModel(repository, expenseId) as T
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            PayTopBar(
                title = if (state.isEditing) "Изменить расход" else "Добавить расход",
                subtitle = DateUtils.formatFullDate(state.date),
                onBack = onDone,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            MoneyInputField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                label = "Сумма расхода, ${state.currency}",
                isError = state.error != null,
                supportingText = state.error,
                imeAction = ImeAction.Next,
            )

            Spacer(Modifier.height(8.dp))

            // --- Выбор счёта ---
            Text(
                text = "Счёт (категория)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            PayCard {
                Column(modifier = Modifier.heightIn(max = 260.dp)) {
                    state.categories.forEach { category ->
                        val selected = category.id == state.categoryId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setCategory(category.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryAvatar(icon = category.icon, size = 38)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    text = "Баланс: " +
                                        Formatters.money(
                                            state.balances
                                                .firstOrNull { it.category.id == category.id }
                                                ?.balance ?: 0.0,
                                            state.currency,
                                        ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (state.isOverBalance) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "⚠️ Расход превышает баланс счёта на " +
                            Formatters.money(state.amount - state.selectedBalance, state.currency) +
                            ". Баланс уйдёт в минус.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Название траты ---
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название траты") },
                placeholder = { Text("Пятёрочка, Стоматолог…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
            )

            if (state.titleSuggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.titleSuggestions.take(10)) { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.setTitle(suggestion) },
                            label = { Text(suggestion) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            DateField(date = state.date, onDateChange = viewModel::setDate)

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.comment,
                onValueChange = viewModel::setComment,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Комментарий (необязательно)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            )

            // --- Быстрые шаблоны ---
            if (state.templates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Быстрые шаблоны",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ChipRow(
                    options = state.templates,
                    selected = "",
                    onSelect = viewModel::applyTemplate,
                    label = { it },
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = hapticClick { viewModel.save(saveAndNew = false) },
                enabled = state.isAmountValid && state.isTitleValid && state.categoryId != 0L,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("✅ Сохранить", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            if (!state.isEditing) {
                OutlinedButton(
                    onClick = hapticClick { viewModel.save(saveAndNew = true) },
                    enabled = state.isAmountValid && state.isTitleValid && state.categoryId != 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("✅ Сохранить и добавить ещё")
                }
                Spacer(Modifier.height(4.dp))
            }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("❌ Отмена")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
