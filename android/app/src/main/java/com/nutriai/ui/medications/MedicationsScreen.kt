package com.nutriai.ui.medications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.MedicationDto
import com.nutriai.data.remote.dto.MedicationRequest
import com.nutriai.notifications.MedReminderScheduler
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.components.PrimaryButton
import com.nutriai.ui.components.StatusIndicator
import com.nutriai.ui.components.Status
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.KaizenCoral
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.NutritionColor
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---------------------------------------------------------------------------
// Medications & supplements - track what you take, log a dose ("Taken"), and get
// local daily dose reminders at each time. Not a drug-interaction checker; the
// server disclaimer is surfaced prominently. Mirrors the vitals/cycle features.
// ---------------------------------------------------------------------------

private val Sharp = RoundedCornerShape(8.dp)

private const val DEFAULT_DISCLAIMER =
    "Kaizen doesn't check drug interactions - confirm anything you take with your pharmacist or doctor."

data class MedsUiState(
    val loading: Boolean = true,
    val meds: List<MedicationDto> = emptyList(),
    val disclaimer: String = DEFAULT_DISCLAIMER,
    val error: String? = null,
    val submitting: Boolean = false,
    val toast: String? = null,
)

@HiltViewModel
class MedicationsViewModel @Inject constructor(
    private val repository: AppRepository,
    private val medReminderScheduler: MedReminderScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(MedsUiState())
    val state: StateFlow<MedsUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.medications()
            _state.value = if (r.isSuccess) {
                val env = r.getOrThrow()
                medReminderScheduler.sync(env.medications)
                _state.value.copy(
                    loading = false,
                    meds = env.medications,
                    disclaimer = env.disclaimer.ifBlank { DEFAULT_DISCLAIMER },
                    error = null,
                )
            } else {
                _state.value.copy(loading = false, error = "Couldn't load your medicines.")
            }
        }
    }

    fun setActive(med: MedicationDto, active: Boolean) {
        viewModelScope.launch {
            val r = repository.setMedicationActive(med.id, active)
            if (r.isSuccess) {
                if (active) medReminderScheduler.scheduleMed(med.copy(active = true))
                else medReminderScheduler.cancelMed(med.id)
                load()
            } else {
                _state.value = _state.value.copy(toast = "Couldn't update - try again")
            }
        }
    }

    fun logTaken(med: MedicationDto) {
        viewModelScope.launch {
            val r = repository.logMedication(med.id)
            _state.value = if (r.isSuccess) {
                _state.value.copy(toast = "Logged ${med.name}")
            } else {
                _state.value.copy(toast = "Couldn't log - try again")
            }
            if (r.isSuccess) load()
        }
    }

    fun save(existing: MedicationDto?, req: MedicationRequest) {
        _state.value = _state.value.copy(submitting = true)
        viewModelScope.launch {
            val r = if (existing == null) repository.createMedication(req)
            else repository.updateMedication(existing.id, req)
            if (r.isSuccess) {
                r.getOrNull()?.let { medReminderScheduler.scheduleMed(it) }
                _state.value = _state.value.copy(submitting = false, toast = "Saved")
                load()
            } else {
                _state.value = _state.value.copy(submitting = false, toast = "Couldn't save - try again")
            }
        }
    }

    fun delete(med: MedicationDto) {
        viewModelScope.launch {
            val r = repository.deleteMedication(med.id)
            if (r.isSuccess) {
                medReminderScheduler.cancelMed(med.id)
                _state.value = _state.value.copy(toast = "Removed ${med.name}")
                load()
            } else {
                _state.value = _state.value.copy(toast = "Couldn't remove - try again")
            }
        }
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }
}

@Composable
fun MedicationsScreen(
    modifier: Modifier = Modifier,
    viewModel: MedicationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MedicationDto?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<MedicationDto?>(null) }

    if (showAdd || editing != null) {
        MedicationDialog(
            existing = editing,
            submitting = state.submitting,
            onDismiss = { showAdd = false; editing = null },
            onSave = { req ->
                viewModel.save(editing, req)
                showAdd = false; editing = null
            },
        )
    }

    pendingDelete?.let { med ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${med.name}?") },
            text = { Text("This deletes it and cancels its reminders. You can add it again later.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(med); pendingDelete = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    val meds = state.meds
    val medsList = meds.filter { it.type != "supplement" }
    val supplements = meds.filter { it.type == "supplement" }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(vertical = Spacing.md),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = { showAdd = true },
                    label = { Text("+ Add") },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add a medicine or supplement" },
                )
            }
        }

        // Disclaimer
        item {
            Card(
                shape = Sharp,
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Warning", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        state.disclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.toast?.let { msg ->
            item {
                Card(
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = NutritionColor,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
                LaunchedEffect(msg) { delay(2500); viewModel.clearToast() }
            }
        }

        if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandGreen) } }
        }

        state.error?.let { err ->
            item {
                Card(
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(err, style = MaterialTheme.typography.bodyMedium, color = KaizenCoral, modifier = Modifier.padding(Spacing.lg))
                }
            }
        }

        if (!state.loading && state.error == null && meds.isEmpty()) {
            item {
                EmptyState(
                    title = "Nothing added yet",
                    emoji = "💊",
                    message = "Add a medicine or supplement to track doses and get gentle reminders.",
                    action = {
                        PrimaryButton(
                            text = "+ Add First Medicine",
                            onClick = { showAdd = true },
                            containerColor = BrandGreen,
                        )
                    },
                )
            }
        }

        if (medsList.isNotEmpty()) {
            item { Text("💊 Medicines", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            items(medsList.size) { i ->
                MedRow(
                    med = medsList[i],
                    onToggleActive = { viewModel.setActive(medsList[i], it) },
                    onTaken = { viewModel.logTaken(medsList[i]) },
                    onEdit = { editing = medsList[i] },
                    onDelete = { pendingDelete = medsList[i] },
                )
            }
        }

        if (supplements.isNotEmpty()) {
            item { Text("🌿 Supplements", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
            items(supplements.size) { i ->
                MedRow(
                    med = supplements[i],
                    onToggleActive = { viewModel.setActive(supplements[i], it) },
                    onTaken = { viewModel.logTaken(supplements[i]) },
                    onEdit = { editing = supplements[i] },
                    onDelete = { pendingDelete = supplements[i] },
                )
            }
        }
    }
}

@Composable
private fun MedRow(
    med: MedicationDto,
    onToggleActive: (Boolean) -> Unit,
    onTaken: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val timesText = med.times.joinToString(" · ").ifBlank { "No set times" }

    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.semantics {
            contentDescription = "${med.name}${med.dose?.let { ", $it" } ?: ""}, ${if (med.active) "reminders on" else "reminders off"}, taken ${med.takenToday} today"
        },
    ) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(med.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            med.dose?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(timesText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            med.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Switch(
                        checked = med.active,
                        onCheckedChange = onToggleActive,
                        modifier = Modifier.semantics { contentDescription = "Dose reminders for ${med.name}" },
                    )
                    StatusIndicator(
                        text = if (med.active) "On" else "Off",
                        status = if (med.active) Status.Positive else Status.Information,
                    )
                }

                if (med.takenToday > 0) {
                    StatusIndicator(text = "Taken ${med.takenToday}x", status = Status.Positive)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AssistChip(
                    onClick = onTaken,
                    label = { Text("Taken${if (med.takenToday > 0) " (${med.takenToday})" else ""}") },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Log a dose of ${med.name} as taken" },
                )
                TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Edit ${med.name}" }) { Text("Edit") }
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Remove ${med.name}" }) { Text("Remove") }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add / edit dialog
// ---------------------------------------------------------------------------

@Composable
private fun MedicationDialog(
    existing: MedicationDto?,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (MedicationRequest) -> Unit,
) {
    var type by remember { mutableStateOf(existing?.type?.takeIf { it == "supplement" } ?: "med") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dose by remember { mutableStateOf(existing?.dose ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    val times = remember { mutableStateListOf<String>().apply { existing?.times?.let { addAll(it) } } }
    var showTimePicker by remember { mutableStateOf(false) }

    val valid = name.isNotBlank()

    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { hhmm ->
                if (!times.contains(hhmm)) { times.add(hhmm); times.sort() }
                showTimePicker = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (existing == null) "Add" else "Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == "med", onClick = { type = "med" }, label = { Text("Medicine") })
                    FilterChip(selected = type == "supplement", onClick = { type = "supplement" }, label = { Text("Supplement") })
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Name" },
                )
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it.take(40) },
                    label = { Text("Dose (optional) e.g. 500 mg") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Dose" },
                )

                Text("Reminder times", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (times.isEmpty()) {
                    Text("No times yet - add one to get a daily reminder.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    times.forEach { t ->
                        AssistChip(
                            onClick = { times.remove(t) },
                            label = { Text("$t x") },
                            modifier = Modifier.semantics { contentDescription = "Remove time $t" },
                        )
                    }
                }
                TextButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Add a reminder time" },
                ) { Text("+ Add time") }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(120) },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Notes" },
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Dose reminders", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it }, modifier = Modifier.semantics { contentDescription = "Enable dose reminders" })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid && !submitting,
                onClick = {
                    onSave(
                        MedicationRequest(
                            type = type,
                            name = name.trim(),
                            dose = dose.trim().ifBlank { null },
                            times = times.toList(),
                            notes = notes.trim().ifBlank { null },
                            active = active,
                        ),
                    )
                },
            ) { Text(if (submitting) "Saving..." else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") } },
    )
}

/** Wraps Material3's [TimePicker] in a dialog and returns the picked time as "HH:mm". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a time") },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = {
                val hhmm = "%02d:%02d".format(pickerState.hour, pickerState.minute)
                onConfirm(hhmm)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
