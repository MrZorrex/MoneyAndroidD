package com.paysplit.app.ui.categories

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Dialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.paysplit.app.data.model.CategoryType
import com.paysplit.app.domain.BalanceCalculator
import com.paysplit.app.ui.LocalViewModelFactory
import com.paysplit.app.ui.components.AnimatedProgress
import com.paysplit.app.ui.components.CategoryAvatar
import com.paysplit.app.ui.components.ConfirmDialog
import com.paysplit.app.ui.components.DateField
import com.paysplit.app.ui.components.OptionalDateField
import com.paysplit.app.ui.components.EmptyState
import com.paysplit.app.ui.components.ExtendedFab
import com.paysplit.app.ui.components.MoneyInputField
import com.paysplit.app.ui.components.PayCard
import com.paysplit.app.ui.components.PayTopBar
import com.paysplit.app.ui.components.SectionHeader
import com.paysplit.app.ui.components.hapticClick
import com.paysplit.app.ui.theme.PayColors
import com.paysplit.app.util.Formatters
import kotlin.math.roundToInt

/** Доступные иконки для счёта. */
private val AvailableIcons = listOf(
    "💰", "🏠", "💡", "🛒", "🚗", "✈️", "💊", "👶",
    "📱", "🎓", "🐱", "🎁", "🏦", "💼", "☕", "🎮",
    "🛠️", "🧾", "💳", "📈",
)

/**
 * ЭКРАН 6 — Управление категориями (счетами).
 */
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = LocalViewModelFactory.current),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editorState.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<BalanceCalculator.CategoryBalance?>(null) }

    Scaffold(
        topBar = { PayTopBar(title = "Категории", subtitle = "Счета и планы", onBack = onBack) },
        floatingActionButton = {
            ExtendedFab(
                text = "Добавить счёт",
                icon = Icons.Filled.Add,
                onClick = hapticClick { viewModel.openNew() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // --- Итоги по плану ---
            PayCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Сумма планов",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Formatters.money(state.totalPlan, state.currency),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Доход в месяц",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Formatters.money(state.expectedIncome, state.currency),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                AnimatedProgress(
                    progress = if (state.totalPlan > 0) {
                        (state.expectedIncome / state.totalPlan).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    height = 8,
                    color = if (state.difference >= 0) PayColors.income else PayColors.expense,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.difference >= 0) {
                        "✅ Свободно " + Formatters.money(state.difference, state.currency) +
                            " после распределения"
                    } else {
                        "⚠️ Не хватает " +
                            Formatters.money(-state.difference, state.currency) +
                            " — планы превышают доход"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.difference >= 0) PayColors.income else PayColors.expense,
                )
            }

            if (state.items.isEmpty()) {
                EmptyState(
                    icon = "🗂️",
                    title = "Категорий нет",
                    message = "Нажмите «Добавить счёт» или загрузите шаблон",
                )
            } else {
                DraggableCategoryList(
                    items = state.items,
                    currency = state.currency,
                    onEdit = { viewModel.openEdit(it.category) },
                    onDelete = { categoryToDelete = it },
                    onOrderApplied = { ids -> viewModel.applyOrder(ids) },
                )
            }

            OutlinedButton(
                onClick = hapticClick { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("📦 Сбросить по шаблону")
            }
        }
    }

    if (editor.visible) {
        CategoryEditorDialog(
            editor = editor,
            onDismiss = viewModel::dismissEditor,
            onNameChange = { viewModel.updateEditor(name = it) },
            onIconChange = { viewModel.updateEditor(icon = it) },
            onPlanChange = { viewModel.updateEditor(planText = it) },
            onTypeChange = { viewModel.updateEditor(type = it) },
            onTargetChange = { viewModel.updateEditor(targetText = it) },
            onTargetDateChange = { viewModel.updateEditor(targetDate = it) },
            onActiveChange = { viewModel.updateEditor(isActive = it) },
            onSave = viewModel::saveEditor,
        )
    }

    if (showResetDialog) {
        ConfirmDialog(
            title = "Сброс по шаблону",
            text = "Все текущие категории и планы будут удалены и заменены шаблоном. " +
                "История операций сохранится, но будет привязана к новым счетам только " +
                "при совпадении идентификаторов. Продолжить?",
            confirmText = "Сбросить",
            onConfirm = {
                viewModel.resetToTemplate()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
        )
    }

    categoryToDelete?.let { item ->
        ConfirmDialog(
            title = "Удаление счёта",
            text = "Удалить счёт «${item.category.name}»? Все операции этого счёта " +
                "будут удалены безвозвратно.",
            confirmText = "Удалить",
            onConfirm = {
                viewModel.delete(item.category)
                categoryToDelete = null
            },
            onDismiss = { categoryToDelete = null },
        )
    }
}

/** Список категорий с перетаскиванием для изменения порядка. */
@Composable
private fun DraggableCategoryList(
    items: List<BalanceCalculator.CategoryBalance>,
    currency: String,
    onEdit: (BalanceCalculator.CategoryBalance) -> Unit,
    onDelete: (BalanceCalculator.CategoryBalance) -> Unit,
    onOrderApplied: (List<Long>) -> Unit,
) {
    val localOrder = remember { mutableStateListOf<BalanceCalculator.CategoryBalance>() }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val isDragging = draggedIndex >= 0

    LaunchedEffect(items, isDragging) {
        if (!isDragging) {
            localOrder.clear()
            localOrder.addAll(items)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = localOrder,
            key = { _, item -> item.category.id },
        ) { index, item ->
            val isDragged = index == draggedIndex
            Card(
                modifier = Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .offset {
                        if (isDragged) IntOffset(0, dragOffset.roundToInt()) else IntOffset.Zero
                    }
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isDragged) 8.dp else 1.dp,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .pointerInput(item.category.id) {
                                val itemId = item.category.id
                                detectVerticalDragGestures(
                                    // Индекс берётся из текущего порядка, а не из
                                    // замыкания: порядок меняется во время перетаскивания.
                                    onDragStart = {
                                        draggedIndex = localOrder.indexOfFirst { entry ->
                                            entry.category.id == itemId
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggedIndex >= 0) {
                                            onOrderApplied(localOrder.map { it.category.id })
                                        }
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggedIndex = -1
                                        dragOffset = 0f
                                    },
                                    onVerticalDrag = { _, amount ->
                                        dragOffset += amount
                                        val threshold = 76.dp.toPx()
                                        when {
                                            dragOffset > threshold &&
                                                draggedIndex < localOrder.lastIndex -> {
                                                localOrder.add(
                                                    draggedIndex + 1,
                                                    localOrder.removeAt(draggedIndex),
                                                )
                                                dragOffset -= threshold
                                            }

                                            dragOffset < -threshold && draggedIndex > 0 -> {
                                                localOrder.add(
                                                    draggedIndex - 1,
                                                    localOrder.removeAt(draggedIndex),
                                                )
                                                dragOffset += threshold
                                            }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "≡",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    CategoryAvatar(
                        icon = item.category.icon,
                        size = 40,
                        modifier = Modifier.clickable { onEdit(item) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEdit(item) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.category.name,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (!item.category.isActive) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "выкл.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.category.type == CategoryType.SAVINGS) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "🏦",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Text(
                            text = "План " +
                                Formatters.money(item.category.monthlyPlan, currency) +
                                " · осталось " +
                                Formatters.money(item.remainingToPlan, currency),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        AnimatedProgress(
                            progress = item.monthProgress,
                            color = PayColors.ofStatus(item.status),
                            height = 4,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = Formatters.money(item.balance, currency),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (item.balance < 0) PayColors.expense else PayColors.income,
                        )
                        TextButton(onClick = { onEdit(item) }) {
                            Text("✏️")
                        }
                        TextButton(onClick = { onDelete(item) }) {
                            Text("🗑️")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** Полноэкранный редактор счёта. */
@Composable
private fun CategoryEditorDialog(
    editor: CategoryEditorState,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onPlanChange: (String) -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onTargetChange: (String) -> Unit,
    onTargetDateChange: (Long?) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = if (editor.isEditing) "Редактирование счёта"
                        else "Новый счёт",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }

                item {
                    OutlinedTextField(
                        value = editor.name,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название") },
                        placeholder = { Text("Продукты, Авто…") },
                        singleLine = true,
                    )
                }

                item {
                    Text(
                        text = "Иконка",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AvailableIcons.size) { index ->
                            val icon = AvailableIcons[index]
                            FilterChip(
                                selected = editor.icon == icon,
                                onClick = { onIconChange(icon) },
                                label = { Text(icon) },
                            )
                        }
                    }
                }

                item {
                    MoneyInputField(
                        value = editor.planText,
                        onValueChange = onPlanChange,
                        label = "План на месяц, ₽",
                    )
                }

                item {
                    Text(
                        text = "Тип счёта",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editor.type == CategoryType.INSTANT_PAY,
                            onClick = { onTypeChange(CategoryType.INSTANT_PAY) },
                            label = { Text("💸 Оплата сразу") },
                        )
                        FilterChip(
                            selected = editor.type == CategoryType.SAVINGS,
                            onClick = { onTypeChange(CategoryType.SAVINGS) },
                            label = { Text("🏦 Накопительный") },
                        )
                    }
                    Text(
                        text = if (editor.type == CategoryType.SAVINGS) {
                            "Деньги копятся на счёте до целевой суммы. Остаток переносится."
                        } else {
                            "Средства тратятся сразу. Неиспользованный остаток переносится " +
                                "на следующий месяц."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (editor.type == CategoryType.SAVINGS) {
                    item {
                        MoneyInputField(
                            value = editor.targetText,
                            onValueChange = onTargetChange,
                            label = "Целевая сумма, ₽",
                        )
                    }
                    item {
                        OptionalDateField(
                            date = editor.targetDate,
                            onDateChange = onTargetDateChange,
                            label = "Дата цели",
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Активный счёт",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "Неактивные счета не участвуют в распределении зарплаты",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = editor.isActive,
                            onCheckedChange = onActiveChange,
                        )
                    }
                }

                item {
                    Button(
                        onClick = onSave,
                        enabled = editor.name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("💾 Сохранить", fontWeight = FontWeight.Bold)
                    }
                }
                item {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Отмена") }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
