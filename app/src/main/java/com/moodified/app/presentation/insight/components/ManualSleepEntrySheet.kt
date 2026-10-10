package com.moodified.app.presentation.insight.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moodified.app.core.theme.DeepSage
import com.moodified.app.core.theme.DmSerifDisplay
import com.moodified.app.core.theme.MilkDeep
import com.moodified.app.core.theme.MilkWhite
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextSecondary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.domain.model.sleep.ManualSleepEntry
import com.moodified.app.domain.model.sleep.SleepWindow
import com.moodified.app.domain.usecase.sleep.ManualSleepAnchoring
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MINUTES_PER_DAY = 24 * 60
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSleepEntrySheet(
    date: LocalDate,
    estimatedNight: SleepWindow?,
    estimateReplaced: Boolean,
    existingEntries: List<ManualSleepEntry>,
    onAdd: (startMs: Long, endMs: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
    onRevert: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MilkWhite,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = DmSerifDisplay),
                color = TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text =
                    if (estimatedNight != null) {
                        "Naps add to your total. A session that overlaps the estimated night replaces it."
                    } else {
                        "No sleep was detected for this day. Anything you log here becomes its sleep."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(24.dp))

            if (estimatedNight != null) {
                ManualSleepSectionLabel("Estimated night")
                Spacer(Modifier.height(10.dp))
                EstimatedNightRow(night = estimatedNight, replaced = estimateReplaced)
                Spacer(Modifier.height(20.dp))
            }

            if (existingEntries.isNotEmpty()) {
                ManualSleepSectionLabel("Logged by you")
                Spacer(Modifier.height(10.dp))
                existingEntries.forEach { entry ->
                    ManualSleepEntryRow(entry = entry, onDelete = { onDelete(entry.id) })
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = SageDim.copy(alpha = 0.5f), thickness = 0.5.dp)
                Spacer(Modifier.height(20.dp))
            }

            ManualSleepSectionLabel("Add a session")
            Spacer(Modifier.height(12.dp))
            AddSleepSessionForm(date = date, onAdd = onAdd)

            if (existingEntries.isNotEmpty() && estimatedNight != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onRevert,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = "Revert to estimated sleep",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextTertiary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Private helpers
// ---------------------------------------------------------------------------

@Composable
private fun ManualSleepSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style =
            MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        color = TextTertiary,
    )
}

@Composable
private fun EstimatedNightRow(
    night: SleepWindow,
    replaced: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MilkDeep.copy(alpha = if (replaced) 0.5f else 1f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = "${night.start.format(timeFormatter)}  →  ${night.end.format(timeFormatter)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (replaced) TextTertiary else TextPrimary,
                textDecoration = if (replaced) TextDecoration.LineThrough else null,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text =
                    if (replaced) {
                        "Replaced by the session you logged"
                    } else {
                        "${formatDuration(night.sleepMinutes)} · counted"
                    },
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

@Composable
private fun ManualSleepEntryRow(
    entry: ManualSleepEntry,
    onDelete: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val startLdt = Instant.ofEpochMilli(entry.startTimeMs).atZone(zone).toLocalDateTime()
    val endLdt = Instant.ofEpochMilli(entry.endTimeMs).atZone(zone).toLocalDateTime()
    val durationMinutes = ((entry.endTimeMs - entry.startTimeMs) / 60_000L).toInt()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MilkDeep,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "${startLdt.format(timeFormatter)}  →  ${endLdt.format(timeFormatter)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatDuration(durationMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Delete session",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private enum class SleepTimeField { START, END }

@Composable
private fun AddSleepSessionForm(
    date: LocalDate,
    onAdd: (startMs: Long, endMs: Long) -> Unit,
) {
    var startHour by rememberSaveable { mutableStateOf(23) }
    var startMinute by rememberSaveable { mutableStateOf(0) }
    var endHour by rememberSaveable { mutableStateOf(7) }
    var endMinute by rememberSaveable { mutableStateOf(0) }
    var editing by rememberSaveable { mutableStateOf<SleepTimeField?>(null) }

    val startTime = LocalTime.of(startHour, startMinute)
    val endTime = LocalTime.of(endHour, endMinute)
    val rawMinutes = (endTime.toSecondOfDay() - startTime.toSecondOfDay()) / 60
    val durationMinutes = if (rawMinutes < 0) rawMinutes + MINUTES_PER_DAY else rawMinutes

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeFieldCard(
            modifier = Modifier.weight(1f),
            label = "Fell asleep",
            time = startTime,
            onClick = { editing = SleepTimeField.START },
        )
        TimeFieldCard(
            modifier = Modifier.weight(1f),
            label = "Woke up",
            time = endTime,
            onClick = { editing = SleepTimeField.END },
        )
    }

    Spacer(Modifier.height(14.dp))
    Text(
        text = if (durationMinutes > 0) formatDuration(durationMinutes) else "Pick two different times",
        style = MaterialTheme.typography.titleMedium,
        color = if (durationMinutes > 0) DeepSage else TextTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(18.dp))

    Button(
        onClick = {
            val (startMs, endMs) = ManualSleepAnchoring.toEpochRange(date, startTime, endTime, ZoneId.systemDefault())
            onAdd(startMs, endMs)
        },
        enabled = durationMinutes > 0,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DeepSage, contentColor = MilkWhite),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Text("Add session", style = MaterialTheme.typography.labelLarge)
    }

    when (editing) {
        SleepTimeField.START ->
            SleepTimePickerDialog(
                title = "Fell asleep",
                initialHour = startHour,
                initialMinute = startMinute,
                onConfirm = { h, m ->
                    startHour = h
                    startMinute = m
                    editing = null
                },
                onDismiss = { editing = null },
            )
        SleepTimeField.END ->
            SleepTimePickerDialog(
                title = "Woke up",
                initialHour = endHour,
                initialMinute = endMinute,
                onConfirm = { h, m ->
                    endHour = h
                    endMinute = m
                    editing = null
                },
                onDismiss = { editing = null },
            )
        null -> Unit
    }
}

@Composable
private fun TimeFieldCard(
    modifier: Modifier,
    label: String,
    time: LocalTime,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MilkDeep,
        border = BorderStroke(1.dp, SageDim.copy(alpha = 0.6f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = label.uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                    ),
                color = TextTertiary,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = time.format(timeFormatter),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MilkWhite, tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.align(Alignment.Start),
                )
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextTertiary)
                    }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                        Text("Set", color = DeepSage, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private fun formatDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
