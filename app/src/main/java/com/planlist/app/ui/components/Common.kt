package com.planlist.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.planlist.app.domain.TimeSlot
import com.planlist.app.ui.theme.Danger
import com.planlist.app.ui.theme.Info
import com.planlist.app.ui.theme.MetricTextStyle
import com.planlist.app.ui.theme.Primary
import com.planlist.app.ui.theme.Warning

/**
 * 进度环。
 *
 * 刻意手动用 Canvas 画而不是用 CircularProgressIndicator：
 * Macro7 观感的关键是"粗描边 + 圆头端点 + 中心大号数字"，后者自由度不够。
 */
@Composable
fun ProgressRing(
    progress: Float,
    centerPrimary: String,
    centerSecondary: String?,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: androidx.compose.ui.unit.Dp = 14.dp,
) {
    val safeProgress = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f)
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (safeProgress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * safeProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerPrimary,
                style = MetricTextStyle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (centerSecondary != null) {
                Text(
                    text = centerSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 语义标签：NOW / OVERDUE / UP NEXT / DONE。 */
@Composable
fun SlotBadge(slot: TimeSlot, modifier: Modifier = Modifier) {
    val (label, color) = when (slot) {
        TimeSlot.NOW -> "NOW" to Info
        TimeSlot.OVERDUE -> "OVERDUE" to Danger
        TimeSlot.UP_NEXT -> "UP NEXT" to MaterialTheme.colorScheme.onSurfaceVariant
        TimeSlot.DONE -> "DONE" to Primary
    }
    Text(
        text = label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 顶部提示条（权限未授予等）。 */
@Composable
fun WarningBanner(text: String, actionLabel: String?, onAction: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Warning.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            color = Warning,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = true),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = Warning,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickableNoRipple(onAction),
            )
        }
    }
}

/** 无涟漪的点击（用于提示条里的文字按钮，避免整条反馈过重）。 */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
