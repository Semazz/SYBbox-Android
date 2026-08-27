package com.sybbox.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sybbox.ui.theme.SybRadius
import com.sybbox.ui.theme.SybSpacing

@Composable
fun <T> SettingsChoice(
    title: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    summary: String? = null,
    icon: ImageVector? = null,
) {
    val labels = options.map { label(it) }
    val fitsAsSegments = options.size in 2..4 && labels.all { it.length <= SEGMENT_LABEL_MAX }

    if (fitsAsSegments) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SybSpacing.rowH, vertical = SybSpacing.rowV),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconTile(icon)
                    Spacer(Modifier.width(SybSpacing.rowGap))
                }
                RowLabel(title, summary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(SybSpacing.small + SybSpacing.hair))
            SybSegmented(options, labels, selected, onSelect)
        }
        return
    }

    var sheetOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { sheetOpen = true }
            .padding(horizontal = SybSpacing.rowH, vertical = SybSpacing.rowV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon)
            Spacer(Modifier.width(SybSpacing.rowGap))
        }
        RowLabel(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(SybSpacing.medium))
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                .padding(horizontal = SybSpacing.medium, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label(selected),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 132.dp),
            )
            Spacer(Modifier.width(SybSpacing.tight))
            Icon(
                Icons.Rounded.ExpandMore,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }

    if (sheetOpen) {
        ChoiceSheet(
            title = title,
            options = options,
            labels = labels,
            selected = selected,
            onSelect = onSelect,
            onDismiss = { sheetOpen = false },
        )
    }
}

private const val SEGMENT_LABEL_MAX = 9

@Composable
fun <T> SybSegmented(
    options: List<T>,
    labels: List<String>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 0.dp,
    maxLines: Int = 1,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SybRadius.tile))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp)
            .height(IntrinsicSize.Max),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            val fill by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(180),
                label = "segmentFill",
            )
            val content by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(180),
                label = "segmentText",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = minHeight)
                    .clip(RoundedCornerShape(SybRadius.inner))
                    .background(fill)
                    .clickable { onSelect(option) }
                    .padding(horizontal = SybSpacing.small, vertical = SybSpacing.small + 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    labels[index],
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = content,
                    maxLines = maxLines,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceSheet(
    title: String,
    options: List<T>,
    labels: List<String>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = SybSpacing.large,
                end = SybSpacing.large,
                bottom = SybSpacing.medium,
            ),
        )
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SybSpacing.medium, vertical = 3.dp)
                    .clip(RoundedCornerShape(SybRadius.tile))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable {
                        onSelect(option)
                        onDismiss()
                    }
                    .padding(horizontal = SybSpacing.rowH, vertical = SybSpacing.rowV + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    labels[index],
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(SybSpacing.xlarge))
    }
}
