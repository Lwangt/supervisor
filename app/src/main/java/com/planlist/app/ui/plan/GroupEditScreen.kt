package com.planlist.app.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import com.planlist.app.ui.theme.Danger
import com.planlist.app.ui.theme.Primary

@Composable
fun GroupEditScreen(
    form: GroupForm,
    onFormChange: ((GroupForm) -> GroupForm) -> Unit,
    onItemChange: (Int, (ItemForm) -> ItemForm) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    onSave: () -> Boolean,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ---- 顶栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("取消") }
            Text(
                text = if (form.id == 0L) "新建计划" else "编辑计划",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onSave() },
                enabled = form.isValid,
            ) { Text("保存") }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- 名称 ----
            // nameError 是计算属性（get() 表达式），在 supportingText 的 lambda 里无法智能转换，
            // 必须先取到局部 val，否则 Kotlin 报 "Smart cast to 'String' is impossible"
            val nameError = form.nameError
            OutlinedTextField(
                value = form.name,
                onValueChange = { value -> onFormChange { it.copy(name = value) } },
                label = { Text("计划名称") },
                placeholder = { Text("例如：午餐 / 推日训练") },
                isError = nameError != null,
                supportingText = {
                    if (nameError != null) {
                        Text(nameError, color = Danger)
                    } else {
                        Text("例如：午餐 / 推日训练")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- 类型 ----
            SectionLabel("类型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.kind == PlanKind.MEAL,
                    onClick = { onFormChange { it.copy(kind = PlanKind.MEAL) } },
                    label = { Text("饮食") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.25f)),
                )
                FilterChip(
                    selected = form.kind == PlanKind.WORKOUT,
                    onClick = { onFormChange { it.copy(kind = PlanKind.WORKOUT) } },
                    label = { Text("运动") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.25f)),
                )
            }

            // ---- 时间 ----
            SectionLabel("时间")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeStepper(
                    value = form.hour,
                    label = "时",
                    range = 0..23,
                    onChange = { value -> onFormChange { it.copy(hour = value) } },
                )
                Spacer(modifier = Modifier.width(16.dp))
                TimeStepper(
                    value = form.minute,
                    label = "分",
                    range = 0..59,
                    step = 5,
                    onChange = { value -> onFormChange { it.copy(minute = value) } },
                )
            }

            // ---- 提醒 ----
            SectionLabel("提前提醒")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "准点", 10 to "提前 10 分钟", 30 to "提前 30 分钟").forEach { (minutes, label) ->
                    FilterChip(
                        selected = form.reminderOffsetMinutes == minutes,
                        onClick = { onFormChange { it.copy(reminderOffsetMinutes = minutes) } },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.25f)),
                    )
                }
            }

            // ---- 重复规则 ----
            SectionLabel("重复")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    RecurrenceType.DAILY to "每天",
                    RecurrenceType.EVERY_N_DAYS to "每 N 天",
                    RecurrenceType.WEEKDAYS to "每周",
                    RecurrenceType.MONTH_DAYS to "每月",
                    RecurrenceType.ONCE to "仅一次",
                )
                options.take(3).forEach { (type, label) ->
                    RecurrenceChip(type, label, form, onFormChange)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    RecurrenceType.DAILY to "每天",
                    RecurrenceType.EVERY_N_DAYS to "每 N 天",
                    RecurrenceType.WEEKDAYS to "每周",
                    RecurrenceType.MONTH_DAYS to "每月",
                    RecurrenceType.ONCE to "仅一次",
                )
                options.drop(3).forEach { (type, label) ->
                    RecurrenceChip(type, label, form, onFormChange)
                }
            }

            when (form.recurrenceType) {
                RecurrenceType.DAILY -> Unit

                RecurrenceType.EVERY_N_DAYS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每 ")
                        TimeStepper(
                            value = form.intervalDays,
                            label = "天",
                            range = 1..90,
                            onChange = { value -> onFormChange { it.copy(intervalDays = value) } },
                        )
                        Text(" 天提醒一次，从 " + form.anchorDate + " 开始")
                    }
                }

                RecurrenceType.WEEKDAYS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                            val selected = (form.weekdaysMask shr index) and 1 == 1
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onFormChange { current ->
                                        current.copy(weekdaysMask = current.weekdaysMask xor (1 shl index))
                                    }
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.25f),
                                ),
                            )
                        }
                    }
                }

                RecurrenceType.MONTH_DAYS -> {
                    MonthDaysPicker(
                        raw = form.monthDays,
                        onChange = { value -> onFormChange { it.copy(monthDays = value) } },
                    )
                }

                RecurrenceType.ONCE -> {
                    OutlinedTextField(
                        value = form.anchorDate,
                        onValueChange = { value -> onFormChange { it.copy(anchorDate = value) } },
                        label = { Text("日期 (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            form.recurrenceError?.let { error ->
                Text(error, color = Danger, style = MaterialTheme.typography.labelMedium)
            }

            // ---- 条目 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("条目")
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onAddItem) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加")
                }
            }

            form.items.forEachIndexed { index, item ->
                ItemEditor(
                    index = index,
                    item = item,
                    isWorkout = form.kind == PlanKind.WORKOUT,
                    canRemove = form.items.size > 1,
                    onChange = { transform -> onItemChange(index, transform) },
                    onRemove = { onRemoveItem(index) },
                )
            }

            form.itemsError?.let { error ->
                Text(error, color = Danger, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RecurrenceChip(
    type: RecurrenceType,
    label: String,
    form: GroupForm,
    onFormChange: ((GroupForm) -> GroupForm) -> Unit,
) {
    FilterChip(
        selected = form.recurrenceType == type,
        onClick = { onFormChange { it.copy(recurrenceType = type) } },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.25f)),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 简单可靠的 +/- 步进器，替代实验性的 Material3 TimePicker。 */
@Composable
private fun TimeStepper(
    value: Int,
    label: String,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = {
                val next = if (value - step < range.first) range.last else value - step
                onChange(next)
            },
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "减少" + label)
        }
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(String.format("%02d", value), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(
            onClick = {
                val next = if (value + step > range.last) range.first else value + step
                onChange(next)
            },
        ) {
            Icon(Icons.Filled.Add, contentDescription = "增加" + label)
        }
    }
}

@Composable
private fun MonthDaysPicker(raw: String, onChange: (String) -> Unit) {
    val selected = com.planlist.app.data.db.entity.parseMonthDays(raw)
    val rows = (1..31).chunked(7)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { day ->
                    FilterChip(
                        selected = day in selected,
                        onClick = {
                            val next = selected.toMutableSet()
                            if (day in next) next.remove(day) else next.add(day)
                            onChange(next.sorted().joinToString(","))
                        },
                        label = { Text(day.toString()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary.copy(alpha = 0.25f),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemEditor(
    index: Int,
    item: ItemForm,
    isWorkout: Boolean,
    canRemove: Boolean,
    onChange: ((ItemForm) -> ItemForm) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "条目 " + (index + 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "删除条目",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = item.name,
                onValueChange = { value -> onChange { it.copy(name = value) } },
                label = { Text(if (isWorkout) "动作名称" else "食物名称") },
                isError = item.name.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = item.amountText,
                onValueChange = { value -> onChange { it.copy(amountText = value) } },
                label = { Text(if (isWorkout) "重量 / 说明（可选）" else "份量（可选）") },
                placeholder = { Text(if (isWorkout) "60 kg" else "150 g") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isWorkout) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = item.sets,
                        label = "组数",
                        onValueChange = { value -> onChange { it.copy(sets = value) } },
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = item.reps,
                        label = "次数",
                        isDecimal = false,
                        onValueChange = { value -> onChange { it.copy(reps = value) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                NumberField(
                    value = item.calories,
                    label = "热量 kcal",
                    onValueChange = { value -> onChange { it.copy(calories = value) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = item.proteinG,
                        label = "蛋白 g",
                        isDecimal = true,
                        onValueChange = { value -> onChange { it.copy(proteinG = value) } },
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = item.carbsG,
                        label = "碳水 g",
                        isDecimal = true,
                        onValueChange = { value -> onChange { it.copy(carbsG = value) } },
                        modifier = Modifier.weight(1f),
                    )
                    NumberField(
                        value = item.fatG,
                        label = "脂肪 g",
                        isDecimal = true,
                        onValueChange = { value -> onChange { it.copy(fatG = value) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "宏量可不填。只要有一项填了，今日页就会用宏量进度环；全部不填则显示完成组数。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    isDecimal: Boolean = false,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            // 只允许合法的数字中间态，避免用户输入出 NaN
            val filtered = if (isDecimal) {
                input.filter { it.isDigit() || it == '.' }
            } else {
                input.filter { it.isDigit() }
            }
            onValueChange(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}
