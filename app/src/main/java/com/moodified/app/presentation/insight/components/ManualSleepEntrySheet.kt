package com.moodified.app.presentation.insight.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moodified.app.core.theme.SageDim
import com.moodified.app.core.theme.TextPrimary
import com.moodified.app.core.theme.TextTertiary
import com.moodified.app.domain.model.sleep.ManualSleepEntry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSleepEntrySheet(
    date: LocalDate,
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
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            androidx.compose.material3.Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Spacer(Modifier.height(16.dp))

            if (existingEntries.isNotEmpty()) {
                androidx.compose.material3.Text(
                    text = "Logged sessions",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(8.dp))
                existingEntries.forEach { entry ->
                    ManualSleepEntryRow(entry = entry, onDelete = { onDelete(entry.id) })
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = SageDim.copy(alpha = 0.4f), thickness = 0.5.dp)
                Spacer(Modifier.height(12.dp))
            }

            AddSleepSessionRow(date = date, onAdd = onAdd)

            if (existingEntries.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onRevert,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    androidx.compose.material3.Text("Revert to estimated", color = TextTertiary)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ManualSleepEntryRow(
    entry: ManualSleepEntry,
    onDelete: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("h:mm a")
    val startLdt = Instant.ofEpochMilli(entry.startTimeMs).atZone(zone).toLocalDateTime()
    val endLdt = Instant.ofEpochMilli(entry.endTimeMs).atZone(zone).toLocalDateTime()
    val label = "${startLdt.format(fmt)} → ${endLdt.format(fmt)}"
    val durationMin = ((entry.endTimeMs - entry.startTimeMs) / 60_000L).toInt()
    val hours = durationMin / 60
    val mins = durationMin % 60
    val duration = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            androidx.compose.material3.Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            androidx.compose.material3.Text(duration, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = TextTertiary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSleepSessionRow(
    date: LocalDate,
    onAdd: (startMs: Long, endMs: Long) -> Unit,
) {
    val startState = rememberTimePickerState(initialHour = 23, initialMinute = 0, is24Hour = false)
    val endState = rememberTimePickerState(initialHour = 7, initialMinute = 0, is24Hour = false)

    androidx.compose.material3.Text(
        text = "Add session",
        style = MaterialTheme.typography.labelSmall,
        color = TextTertiary,
    )
    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text("Start", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            TimeInput(state = startState)
        }
        androidx.compose.material3.Text("→", style = MaterialTheme.typography.bodyLarge, color = TextTertiary)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text("End", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            TimeInput(state = endState)
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            val zone = ZoneId.systemDefault()
            val startLt = LocalTime.of(startState.hour, startState.minute)
            val endLt = LocalTime.of(endState.hour, endState.minute)
            val startMs = date.atTime(startLt).atZone(zone).toInstant().toEpochMilli()
            val endDate = if (endLt < startLt) date.plusDays(1) else date
            val endMs = endDate.atTime(endLt).atZone(zone).toInstant().toEpochMilli()
            onAdd(startMs, endMs)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.material3.Text("Add")
    }
}
