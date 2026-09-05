package com.nutriai.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import com.nutriai.data.remote.dto.CalcResult
import com.nutriai.data.remote.dto.FamilyMemberDto
import com.nutriai.data.remote.dto.FamilyMemberRequest
import com.nutriai.data.remote.dto.SensitiveData
import com.nutriai.ui.components.MetricBlock
import com.nutriai.ui.components.PrimaryButton
import com.nutriai.ui.components.EmptyState
import com.nutriai.ui.components.StatusIndicator
import com.nutriai.ui.components.Status
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.KaizenBlue
import com.nutriai.ui.theme.KaizenLavender
import com.nutriai.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Sharp = RoundedCornerShape(8.dp)

private val SEX = listOf("male", "female")
private val ACTIVITY = listOf("sedentary", "light", "moderate", "active", "veryactive")
private val GOAL = listOf("lose", "maintain", "gain")
private val DIET = listOf("veg", "eggetarian", "nonveg", "vegan", "jain", "keto", "highprotein")

data class FamilyState(
    val members: List<FamilyMemberDto> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
    val selectedCalc: CalcResult? = null,
)

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FamilyState())
    val state: StateFlow<FamilyState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val r = repository.family()
            _state.value = if (r.isSuccess) {
                _state.value.copy(loading = false, members = r.getOrDefault(emptyList()))
            } else {
                _state.value.copy(loading = false, message = r.exceptionOrNull()?.message ?: "Failed to load family")
            }
        }
    }

    fun addMember(req: FamilyMemberRequest) {
        _state.value = _state.value.copy(loading = true, message = null)
        viewModelScope.launch {
            val r = repository.addFamilyMember(req)
            if (r.isSuccess) {
                _state.value = _state.value.copy(message = "Added ${req.firstName}")
                reload()
            } else {
                _state.value = _state.value.copy(
                    loading = false,
                    message = r.exceptionOrNull()?.message ?: "Could not add member",
                )
            }
        }
    }

    fun viewCalc(id: String) {
        viewModelScope.launch {
            val r = repository.familyCalc(id)
            _state.value = _state.value.copy(selectedCalc = r.getOrNull())
        }
    }
}

@Composable
fun FamilyScreen(
    modifier: Modifier = Modifier,
    viewModel: FamilyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var firstName by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("male") }
    var activity by remember { mutableStateOf("moderate") }
    var goal by remember { mutableStateOf("lose") }
    var diet by remember { mutableStateOf("nonveg") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(4.dp))

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGreen)
            }
        }

        if (state.members.isNotEmpty()) {
            Text("👥 Members", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            state.members.forEach { member ->
                Card(
                    shape = Sharp,
                    elevation = CardDefaults.cardElevation(2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                member.firstName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                member.relation?.takeIf { it.isNotBlank() } ?: "Family member",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "View",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = BrandGreen,
                            modifier = Modifier.clickable { viewModel.viewCalc(member.id) },
                        )
                    }
                }
            }
        } else if (!state.loading) {
            EmptyState(
                title = "No family members yet",
                emoji = "👨‍👩‍👧",
                message = "Add your family to track their nutrition too!",
            )
        }

        state.selectedCalc?.let { calc -> CalcStatCard(calc) }

        AddMemberCard(
            firstName = firstName, onFirstName = { firstName = it },
            relation = relation, onRelation = { relation = it },
            height = height, onHeight = { height = it },
            dob = dob, onDob = { dob = it },
            weight = weight, onWeight = { weight = it },
            target = target, onTarget = { target = it },
            sex = sex, onSex = { sex = it },
            activity = activity, onActivity = { activity = it },
            goal = goal, onGoal = { goal = it },
            diet = diet, onDiet = { diet = it },
            message = state.message,
            loading = state.loading,
            onSubmit = { h, w, t ->
                viewModel.addMember(
                    FamilyMemberRequest(
                        firstName = firstName.trim(),
                        relation = relation.trim().ifBlank { null },
                        heightCm = h,
                        activityLevel = activity,
                        goal = goal,
                        dietType = diet,
                        sensitive = SensitiveData(
                            sex = sex,
                            dob = dob.trim(),
                            currentWeightKg = w,
                            targetWeightKg = t,
                            conditions = emptyList(),
                            allergies = emptyList(),
                        ),
                    ),
                )
            },
        )

        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------------
// Calc stat card
// ---------------------------------------------------------------------------

@Composable
private fun CalcStatCard(calc: CalcResult) {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("📊 Daily Targets", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                MetricBlock(label = "BMI", value = String.format("%.1f", calc.bmi), color = KaizenBlue, modifier = Modifier.weight(1f))
                MetricBlock(label = "TDEE", value = "${calc.tdee.toInt()}", color = BrandGreenDeep, modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                MetricBlock(label = "Daily kcal", value = "${calc.dailyKcal.toInt()}", color = BrandAmber, modifier = Modifier.weight(1f))
                MetricBlock(label = "Protein", value = "${calc.proteinG.toInt()} g", color = KaizenLavender, modifier = Modifier.weight(1f))
            }
            if (calc.requiresSupervision) {
                StatusIndicator(text = "Requires professional supervision", status = Status.Critical)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add member card
// ---------------------------------------------------------------------------

@Composable
private fun AddMemberCard(
    firstName: String, onFirstName: (String) -> Unit,
    relation: String, onRelation: (String) -> Unit,
    height: String, onHeight: (String) -> Unit,
    dob: String, onDob: (String) -> Unit,
    weight: String, onWeight: (String) -> Unit,
    target: String, onTarget: (String) -> Unit,
    sex: String, onSex: (String) -> Unit,
    activity: String, onActivity: (String) -> Unit,
    goal: String, onGoal: (String) -> Unit,
    diet: String, onDiet: (String) -> Unit,
    message: String?,
    loading: Boolean,
    onSubmit: (Double, Double, Double) -> Unit,
) {
    Card(
        shape = Sharp,
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("+ Add a Member", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

            Text("📝 Basic Info", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            BrandField(firstName, onFirstName, "First name")
            BrandField(relation, onRelation, "Relation")
            BrandField(height, onHeight, "Height (cm)", number = true)

            Text("👤 Sex", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            SingleChoiceChips(SEX, sex, onSex)

            Text("⚖️ Body Details", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            BrandField(dob, onDob, "Date of birth (YYYY-MM-DD)")
            BrandField(weight, onWeight, "Current weight (kg)", number = true)
            BrandField(target, onTarget, "Target weight (kg)", number = true)

            Text("🏃 Activity Level", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            SingleChoiceChips(ACTIVITY, activity, onActivity)

            Text("🎯 Goal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            SingleChoiceChips(GOAL, goal, onGoal)

            Text("🍲 Diet", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            SingleChoiceChips(DIET, diet, onDiet)

            message?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = BrandGreenDeep,
                )
            }

            val h = height.toDoubleOrNull()
            val w = weight.toDoubleOrNull()
            val t = target.toDoubleOrNull()
            val valid = firstName.isNotBlank() && dob.isNotBlank() && h != null && w != null && t != null

            PrimaryButton(
                text = "Add Member",
                onClick = {
                    if (h != null && w != null && t != null) onSubmit(h, w, t)
                },
                enabled = valid && !loading,
                modifier = Modifier.fillMaxWidth(),
                containerColor = BrandGreen,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

@Composable
private fun BrandField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    number: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = Sharp,
        keyboardOptions = if (number) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandGreen,
            focusedLabelColor = BrandGreen,
            cursorColor = BrandGreen,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SingleChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(opt) },
                shape = Sharp,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandGreen,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}
