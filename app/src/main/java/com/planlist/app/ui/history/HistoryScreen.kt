package com.planlist.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.planlist.app.domain.DaySummary
import com.planlist.app.domain.TimeSlot
import com.planlist.app.ui.components.SlotBadge
import com.planlist.app.ui.theme.Danger
import com.planlist.app.ui.theme.Primary
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToCurrentMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCompleteGroup: (LocalDate, Long) -> Unit,
    onClearGroup: (LocalDate, Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // ---- 月份切换 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                text = state.month.year.toString() + " 年 " + state.month.monthValue + " 月",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
            }
        }

        // ---- 统计 ----
        Text(
            text = "连续打卡 " + state.streak + " 天   ·   完成率 " +
                (state.completionRate * 100).roundToInt() + "%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onGoToCurrentMonth) { Text("回到本月") }

        // ---- 星期表头 ----
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ---- 日历网格 ----
        monthWeeks(state.month).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                summary = state.summaries[date],
                                today = state.today,
                                selected = state.selectedDate == date,
                                onClick = { onSelectDate(date) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- 图例 ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendItem(Primary, "全部完成")
            LegendItem(Primary.copy(alpha = 0.35f), "部分完成")
            LegendItem(Danger.copy(alpha = 0.6f), "未完成")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---- 选中日详情 ----
        val selected = state.selectedDate
        if (selected != null) {
            DayDetail(
                date = selected,
                state = state,
                onCompleteGroup = onCompleteGroup,
                onClearGroup = onClearGroup,
            )
        } else {
            Text(
                "点任意一天查看当天完成情况",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    state: HistoryUiState,
    onCompleteGroup: (LocalDate, Long) -> Unit,
    onClearGroup: (LocalDate, Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = date.monthValue.toString() + " 月 " + date.dayOfMonth + " 日",
            style = MaterialTheme.typography.titleMedium,
        )

        if (state.selectedGroups.isEmpty()) {
            Text(
                "这天没有计划（休息日）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        state.selectedGroups.forEach { group ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(group.group.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = group.completedCount.toString() + "/" + group.totalCount + " 项已完成",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SlotBadge(slot = if (group.isComplete) TimeSlot.DONE else group.slot)

                    if (state.canEditSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                if (group.isComplete) {
                                    onClearGroup(date, group.group.id)
                                } else {
                                    onCompleteGroup(date, group.group.id)
                                }
                            },
                        ) {
                            Text(if (group.isComplete) "撤销" else "补打卡")
                        }
                    }
                }
            }
        }

        if (!state.canEditSelected) {
            Text(
                "超出可补打卡范围（可在设置里调整），仅可查看",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    today: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val isFuture = date.isAfter(today)
    val fill = when {
        summary == null || summary.isRest -> null
        summary.isDone -> Primary
        summary.isPartial -> Primary.copy(alpha = 0.35f)
        summary.isMissed && !isFuture -> Danger.copy(alpha = 0.18f)
        else -> null
    }
    val textColor = when {
        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        summary?.isDone == true -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                } else if (date == today) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(enabled = !isFuture, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 生成月历网格：前置空白补齐到周一，尾部补到整周。 */
private fun monthWeeks(month: YearMonth): List<List<LocalDate?>> {
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val cells = ArrayList<LocalDate?>()
    repeat(leading) { cells.add(null) }
    for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}
