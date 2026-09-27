package com.planlist.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.domain.TimeSlot
import com.planlist.app.domain.TodayGroup
import com.planlist.app.ui.components.ProgressRing
import com.planlist.app.ui.components.SlotBadge
import com.planlist.app.ui.theme.Danger
import com.planlist.app.ui.theme.Info
import com.planlist.app.ui.theme.Primary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE")
private val WEEKDAY_CN = mapOf(
    java.time.DayOfWeek.MONDAY to "周一",
    java.time.DayOfWeek.TUESDAY to "周二",
    java.time.DayOfWeek.WEDNESDAY to "周三",
    java.time.DayOfWeek.THURSDAY to "周四",
    java.time.DayOfWeek.FRIDAY to "周五",
    java.time.DayOfWeek.SATURDAY to "周六",
    java.time.DayOfWeek.SUNDAY to "周日",
)

@Composable
fun TodayScreen(
    state: TodayUiState,
    onShiftDate: (Long) -> Unit,
    onGoToday: () -> Unit,
    onToggleItem: (Long, Long, Boolean) -> Unit,
    onFinishGroup: (Long) -> Unit,
    onClearGroup: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DateHeader(state = state, onShiftDate = onShiftDate, onGoToday = onGoToday)

        if (state.groups.isEmpty()) {
            EmptyToday()
            return@Column
        }

        ProgressSummary(state = state)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 24.dp, top = 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = state.groups, key = { it.group.id }) { group ->
                TodayGroupCard(
                    group = group,
                    canEdit = state.canEdit,
                    onToggleItem = onToggleItem,
                    onFinishGroup = onFinishGroup,
                    onClearGroup = onClearGroup,
                )
            }
        }
    }
}

@Composable
private fun DateHeader(state: TodayUiState, onShiftDate: (Long) -> Unit, onGoToday: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShiftDate(-1L) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "前一天")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.date.format(DATE_FORMATTER).let { formatted ->
                    // DateTimeFormatter 的中文星期在某些 Android 版本上会退回英文，这里强制替换
                    if (WEEKDAY_CN[state.date.dayOfWeek] != null) {
                        formatted.substringBeforeLast(" ") + " " + WEEKDAY_CN[state.date.dayOfWeek]
                    } else {
                        formatted
                    }
                },
                style = MaterialTheme.typography.titleLarge,
            )
            if (!state.isToday) {
                Text(
                    text = "点这里回到今天",
                    style = MaterialTheme.typography.labelMedium,
                    color = Info,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .pointerInput(Unit) { detectTapGestures(onTap = { onGoToday() }) },
                )
            }
        }
        IconButton(
            onClick = { onShiftDate(1L) },
            enabled = state.date.isBefore(LocalDate.MAX),
        ) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "后一天")
        }
    }
}

@Composable
private fun ProgressSummary(state: TodayUiState) {
    val summary = state.summary
    val useMacro = state.useMacroMode && state.settings.showMacros

    val primaryText: String
    val secondaryText: String
    val progress: Float

    if (useMacro) {
        primaryText = state.totalCaloriesEaten.toString()
        secondaryText = "/ " + state.totalCaloriesPlanned + " kcal"
        progress = if (state.totalCaloriesPlanned > 0) {
            state.totalCaloriesEaten.toFloat() / state.totalCaloriesPlanned.toFloat()
        } else {
            0f
        }
    } else {
        primaryText = summary.completedGroups.toString()
        secondaryText = "/ " + summary.totalGroups + " 组"
        progress = if (summary.totalGroups > 0) {
            summary.completedGroups.toFloat() / summary.totalGroups.toFloat()
        } else {
            0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            ProgressRing(
                progress = progress,
                centerPrimary = primaryText,
                centerSecondary = secondaryText,
                modifier = Modifier.fillMaxSize(),
                ringColor = if (progress >= 1f) Primary else MaterialTheme.colorScheme.primary,
            )
        }

        if (useMacro) {
            val planned = state.groups.fold(com.planlist.app.domain.Macros.ZERO) { acc, g -> acc + g.planned }
            val eaten = state.groups.fold(com.planlist.app.domain.Macros.ZERO) { acc, g -> acc + g.eaten }
            Text(
                text = "蛋白质 " + eaten.proteinG.toInt() + "/" + planned.proteinG.toInt() + "g   " +
                    "碳水 " + eaten.carbsG.toInt() + "/" + planned.carbsG.toInt() + "g   " +
                    "脂肪 " + eaten.fatG.toInt() + "/" + planned.fatG.toInt() + "g",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TodayGroupCard(
    group: TodayGroup,
    canEdit: Boolean,
    onToggleItem: (Long, Long, Boolean) -> Unit,
    onFinishGroup: (Long) -> Unit,
    onClearGroup: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(group.slot == TimeSlot.NOW || group.slot == TimeSlot.OVERDUE) }

    val borderColor = when (group.slot) {
        TimeSlot.NOW -> Info
        TimeSlot.OVERDUE -> Danger
        TimeSlot.DONE -> Primary.copy(alpha = 0.5f)
        TimeSlot.UP_NEXT -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (group.slot == TimeSlot.NOW || group.slot == TimeSlot.OVERDUE) 1.5.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.large,
            )
            .pointerInput(group.group.id) {
                detectTapGestures(onTap = { expanded = !expanded })
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (group.slot == TimeSlot.NOW) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (group.group.kind == PlanKind.MEAL) {
                    Icons.Filled.Restaurant
                } else {
                    Icons.Filled.FitnessCenter
                },
                contentDescription = if (group.group.kind == PlanKind.MEAL) "饮食" else "运动",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = group.group.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = String.format("%02d:%02d", group.group.timeOfDay / 60, group.group.timeOfDay % 60) +
                        if (group.totalCount > 0) " · " + group.completedCount + "/" + group.totalCount + " 项" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SlotBadge(slot = group.slot)
        }

        if (expanded && group.items.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                group.items.forEach { item ->
                    val checked = group.statuses[item.id] == com.planlist.app.data.db.entity.LogStatus.COMPLETED
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = canEdit,
                            onCheckedChange = { value ->
                                onToggleItem(group.group.id, item.id, value)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = Primary),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (checked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            val detail = buildString {
                                if (item.amountText.isNotBlank()) append(item.amountText)
                                if (item.calories != null) {
                                    if (isNotEmpty()) append(" · ")
                                    append(item.calories).append(" kcal")
                                }
                                if (item.sets != null) {
                                    if (isNotEmpty()) append(" · ")
                                    append(item.sets).append(" 组")
                                }
                                if (!item.reps.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" × ")
                                    append(item.reps)
                                }
                            }
                            if (detail.isNotBlank()) {
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (canEdit) {
                if (group.isComplete) {
                    Text(
                        text = "长按撤销整组",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 10.dp)
                            .pointerInput(group.group.id) {
                                detectTapGestures(onLongPress = { onClearGroup(group.group.id) })
                            },
                    )
                } else {
                    HoldToFinishBar(onFinish = { onFinishGroup(group.group.id) })
                }
            }
        }
    }
}

/**
 * 长按 500ms 完成整组。
 *
 * 必须长按而不是单击：整组完成会一次勾掉多个条目，单击太容易误触。
 * 按下期间底部进度条填充，松手回弹；完成后给出触感反馈。
 */
@Composable
private fun HoldToFinishBar(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        var job: Job? = scope.launch {
                            val steps = 20
                            repeat(steps) { index ->
                                delay(HOLD_MILLIS / steps)
                                progress = (index + 1).toFloat() / steps.toFloat()
                            }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFinish()
                            progress = 0f
                        }
                        tryAwaitRelease()
                        job.cancel()
                        job = null
                        progress = 0f
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Primary.copy(alpha = 0.35f)),
        )
        Text(
            text = "长按完成整组",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun EmptyToday() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("今天还没有计划", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "到「计划」页创建你的第一条饮食或运动计划",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val HOLD_MILLIS = 500L
