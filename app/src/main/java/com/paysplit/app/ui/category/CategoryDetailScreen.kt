package com.paysplit.app.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.paysplit.app.data.model.CategoryType
import com.paysplit.app.data.model.UiTransaction
import com.paysplit.app.ui.components.AnimatedProgress
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.CategorySettingsDialog
import com.paysplit.app.ui.components.ChipRow
import com.paysplit.app.ui.components.ConfirmDialog
import com.paysplit.app.ui.components.EmptyState
import com.paysplit.app.ui.components.AreaLineChart
import com.paysplit.app.ui.components.DateField
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.MoneyValue
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.SectionHeader
import com.paysplit.app.ui.components.StatTile
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.DateUtils
import com.paysplit.app.util.Formatters

/**
 * Детали счёта — компактный интерфейс.
 */
@Composable
fun CategoryDetailScreen(
    categoryId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { PaySplitApplication.repository(context) }
    val viewModel: CategoryDetailViewModel = viewModel(
        factory = remember(categoryId, repository) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CategoryDetailViewModel(repository, categoryId) as T
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showExpenseDialog by remember { mutableStateOf(false) }
    var showTopUpDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<UiTransaction?>(null) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    Scaffold(
        topBar = {
            PayTopBar(
                title = state.categoryName,
                subtitle = if (state.balance?.category?.type == CategoryType.SAVINGS) "Накопительный" else "Расходный",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { showSettingsDialog = true }) { Text("⚙️", style = MaterialTheme.typography.labelMedium) }
                },
            )
        },
    ) { padding ->
        val balance = state.balance
        if (balance == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Счёт не найден", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                PayCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(icon = state.categoryIcon, size = 32)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Текущий баланс", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoneyValue(
                                value = balance.balance,
                                color = if (balance.balance < 0) PayColors.expense else PayColors.income,
                                currency = state.currency,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        TextButton(onClick = hapticClick { showAdjustDialog = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("⚖️ Коррекция", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AnimatedProgress(progress = balance.monthProgress, color = PayColors.ofStatus(balance.status), height = 5)
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Распр. ${Formatters.money(balance.allocatedThisMonth, state.currency)} из ${Formatters.money(balance.category.monthlyPlan, state.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (balance.isOverspend) "⚠ ${Formatters.money(-balance.balance, state.currency)}" else "Ост. ${Formatters.money(balance.remainingToPlan, state.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (balance.isOverspend) PayColors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatTile(title = "Поступления", value = Formatters.money(state.monthlyStats.lastOrNull()?.income ?: 0.0, state.currency), modifier = Modifier.weight(1f), valueColor = PayColors.income)
                    StatTile(title = "Расходы", value = Formatters.money(state.monthlyStats.lastOrNull()?.expense ?: 0.0, state.currency), modifier = Modifier.weight(1f), valueColor = PayColors.expense)
                }
            }

            item {
                PayCard {
                    SectionHeader(title = "Баланс 6 мес.", subtitle = "На конец месяца")
                    Spacer(Modifier.height(8.dp))
                    if (state.monthlyBalance.any { it.second != 0.0 }) {
                        AreaLineChart(values = state.monthlyBalance.map { it.second }, labels = state.monthLabels, height = 110.dp, lineColor = MaterialTheme.colorScheme.primary)
                    } else {
                        EmptyState(icon = "📈", title = "Нет данных", message = "График появится после операций")
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = hapticClick { showExpenseDialog = true }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Text("➖ Трата", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = hapticClick { showTopUpDialog = true }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Text("➕ Пополнить", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = hapticClick { showAdjustDialog = true }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Text("⚖️ Правка", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            item { SectionHeader(title = "Операции", subtitle = "Всего: ${state.transactions.size}") }

            if (state.transactions.isEmpty()) {
                item { EmptyState(icon = "🧾", title = "Операций нет", message = "Добавьте расход или пополните") }
            } else {
                val grouped = state.transactions.groupBy { DateUtils.formatMonthShort(DateUtils.yearMonth(it.date)) }
                grouped.forEach { (month, transactions) ->
                    item(key = "header-$month") {
                        Text(month, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
                    }
                    items(transactions, key = { "${it.type}-${it.id}-${it.date}" }) { transaction ->
                        TransactionRow(transaction = transaction, currency = state.currency, onDelete = { transactionToDelete = transaction })
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = hapticClick { showDeleteDialog = true },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                ) { Text("🗑️ Удалить «${state.categoryName}»", style = MaterialTheme.typography.labelMedium) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showExpenseDialog) {
        QuickExpenseDialog(onDismiss = { showExpenseDialog = false }, onConfirm = { title, amount -> viewModel.addQuickExpense(title, amount); showExpenseDialog = false })
    }
    if (showTopUpDialog) {
        TopUpDialog(accounts = state.otherCategories, currency = state.currency, onDismiss = { showTopUpDialog = false }, onConfirm = { fromId, amount -> viewModel.topUp(fromId, amount); showTopUpDialog = false })
    }
    if (showAdjustDialog) {
        BalanceCorrectDialog(
            currentBalance = state.balance?.balance ?: 0.0,
            categoryName = state.categoryName,
            icon = state.categoryIcon,
            currency = state.currency,
            onDismiss = { showAdjustDialog = false },
            onConfirmDelta = { delta, comment, date -> viewModel.adjustBalance(delta, comment, date); showAdjustDialog = false },
            onConfirmNewBalance = { newBalance, comment, date -> viewModel.setBalanceTo(newBalance, comment, state.balance?.balance ?: 0.0, date); showAdjustDialog = false },
        )
    }
    val editingCategory = state.balance?.category
    if (showSettingsDialog && editingCategory != null) {
        CategorySettingsDialog(category = editingCategory, currency = state.currency, currentBalance = state.balance?.allTimeBalance ?: 0.0, onDismiss = { showSettingsDialog = false }, onSave = { viewModel.saveCategory(it); showSettingsDialog = false })
    }
    if (showDeleteDialog) {
        ConfirmDialog(
            title = "Удалить счёт",
            text = "Со счётом «${state.categoryName}» удалятся все операции. Можно отключить в настройках вместо удаления.",
            onConfirm = { viewModel.deleteCategory(); showDeleteDialog = false },
            onDismiss = { showDeleteDialog = false },
        )
    }
    transactionToDelete?.let { tx ->
        ConfirmDialog(
            title = "Удалить операцию",
            text = "Удалить «${tx.title}» ${Formatters.money(tx.amount, state.currency)} от ${DateUtils.formatFullDate(tx.date)}?",
            onConfirm = { viewModel.deleteTransaction(tx); transactionToDelete = null },
            onDismiss = { transactionToDelete = null },
        )
    }
}

@Composable
private fun TransactionRow(transaction: UiTransaction, currency: String, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onDelete() }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryAvatar(icon = transaction.icon, size = 28)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(DateUtils.formatShortDate(transaction.date) + (transaction.comment?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        val isIncoming = transaction.isIncoming || (transaction.type == com.paysplit.app.data.model.TxType.ADJUSTMENT && transaction.title.contains("+"))
        val color = when (transaction.type) {
            com.paysplit.app.data.model.TxType.ADJUSTMENT -> if (transaction.title.contains("+")) PayColors.income else PayColors.expense
            else -> if (isIncoming) PayColors.income else PayColors.expense
        }
        Text(
            (if (isIncoming) "+" else "−") + Formatters.money(transaction.amount, currency),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun QuickExpenseDialog(onDismiss: () -> Unit, onConfirm: (String, Double) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➖ Быстрая трата", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyInputField(value = amountText, onValueChange = { amountText = it }, label = "Сумма, ₽")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = com.paysplit.app.ui.components.capitalizeFirst(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { val a = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0; if (a > 0 && title.isNotBlank()) onConfirm(title.trim(), a) },
                enabled = (com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0) > 0 && title.isNotBlank(),
            ) { Text("Добавить", style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun TopUpDialog(accounts: List<Pair<Long, String>>, currency: String, onDismiss: () -> Unit, onConfirm: (Long, Double) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var fromId by remember { mutableStateOf(accounts.firstOrNull()?.first ?: 0L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➕ Пополнить", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Списать со счёта:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChipRow(options = accounts.map { it.first }, selected = fromId, onSelect = { fromId = it }, label = { id -> accounts.firstOrNull { it.first == id }?.second ?: "?" })
                MoneyInputField(value = amountText, onValueChange = { amountText = it }, label = "Сумма, $currency")
            }
        },
        confirmButton = {
            Button(
                onClick = { val a = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0; if (a > 0 && fromId != 0L) onConfirm(fromId, a) },
                enabled = (com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0) > 0 && fromId != 0L,
            ) { Text("Перевести") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun BalanceCorrectDialog(
    currentBalance: Double,
    categoryName: String,
    icon: String,
    currency: String,
    onDismiss: () -> Unit,
    onConfirmDelta: (Double, String?, Long) -> Unit,
    onConfirmNewBalance: (Double, String?, Long) -> Unit,
) {
    var mode by remember { mutableStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var isIncrease by remember { mutableStateOf(true) }
    var newBalanceText by remember { mutableStateOf("") }
    var commentText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚖️ Коррекция баланса", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(icon = icon, size = 28)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(categoryName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Сейчас: ${Formatters.money(currentBalance, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Как это работает:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("• «На сумму» — прибавляет/списывает разницу. • «Установить» — задаёт точный новый баланс.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("➕➖ На сумму", style = MaterialTheme.typography.labelSmall) })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("🎯 Установить баланс", style = MaterialTheme.typography.labelSmall) })
                }
                if (mode == 0) {
                    Text("Укажите сколько прибавить или списать:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = isIncrease, onClick = { isIncrease = true }, label = { Text("＋ Пополнить", style = MaterialTheme.typography.labelSmall) })
                        FilterChip(selected = !isIncrease, onClick = { isIncrease = false }, label = { Text("− Списать", style = MaterialTheme.typography.labelSmall) })
                    }
                    MoneyInputField(value = amountText, onValueChange = { amountText = it }, label = "Сумма коррекции, $currency")
                    val parsed = com.paysplit.app.ui.components.parseMoney(amountText)
                    if (parsed != null && parsed > 0) {
                        val preview = if (isIncrease) currentBalance + parsed else currentBalance - parsed
                        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                            Text("Было ${Formatters.money(currentBalance, currency)} ${if (isIncrease) "+" else "−"} ${Formatters.money(parsed, currency)} → станет ${Formatters.money(preview, currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(8.dp))
                        }
                    } else Text("Пример: было 5 000 ₽ + 1 000 ₽ → станет 6 000 ₽", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Введите какой баланс должен стать:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyInputField(value = newBalanceText, onValueChange = { newBalanceText = it }, label = "Новый баланс, $currency")
                    val parsed = com.paysplit.app.ui.components.parseMoney(newBalanceText)
                    if (parsed != null) {
                        val delta = parsed - currentBalance
                        androidx.compose.material3.Surface(color = if (delta >= 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Text("Было ${Formatters.money(currentBalance, currency)} → станет ${Formatters.money(parsed, currency)}", style = MaterialTheme.typography.labelSmall)
                                Text("Запишется: ${if (delta >= 0) "+" else ""}${Formatters.money(delta, currency)} ${if (delta >= 0) "пополнение" else "списание"}", style = MaterialTheme.typography.labelSmall, color = if (delta >= 0) PayColors.income else PayColors.expense, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else Text("Пример: было 5 000 ₽ → ввели 8 000 ₽ → запишется +3 000 ₽", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = com.paysplit.app.ui.components.capitalizeFirst(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Комментарий", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("Напр.: исправил ошибку, нашёл наличные...", style = MaterialTheme.typography.labelSmall) },
                    textStyle = MaterialTheme.typography.bodySmall,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                )
                DateField(date = date, onDateChange = { date = it }, label = "Дата коррекции")
                Text("Коррекция попадёт в историю — можно удалить.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val canSave = if (mode == 0) (com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0) > 0 else com.paysplit.app.ui.components.parseMoney(newBalanceText) != null
            Button(onClick = {
                if (mode == 0) {
                    val a = com.paysplit.app.ui.components.parseMoney(amountText) ?: 0.0
                    if (a > 0) onConfirmDelta(if (isIncrease) a else -a, commentText, date)
                } else {
                    val nb = com.paysplit.app.ui.components.parseMoney(newBalanceText)
                    if (nb != null) onConfirmNewBalance(nb, commentText, date)
                }
            }, enabled = canSave) { Text("Сохранить", style = MaterialTheme.typography.labelMedium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
