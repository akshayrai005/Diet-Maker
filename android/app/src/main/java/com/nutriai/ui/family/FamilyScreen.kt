package com.nutriai.ui.family

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.nutriai.ui.theme.BrandAmber
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.BrandGreenLight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        FamilyHeader(count = state.members.size)

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandGreen)
            }
        }

        if (state.members.isNotEmpty()) {
            Text(
                "Members",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.members.forEachIndexed { i, member ->
                MemberCard(member = member) { viewModel.viewCalc(member.id) }
                if (i != state.members.lastIndex) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            }
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
// Header
// ---------------------------------------------------------------------------

@Composable
private fun FamilyHeader(count: Int) {
    com.nutriai.ui.components.ScreenHeader(
        title = "Family",
        subtitle = if (count == 1) "1 member" else "$count members",
    )
}

// ---------------------------------------------------------------------------
// Member row - not a decorative profile-card grid (Change 02).
// ---------------------------------------------------------------------------

@Composable
private fun MemberCard(member: FamilyMemberDto, onClick: () -> Unit) {
    com.nutriai.ui.components.ListRow(
        title = member.firstName,
        subtitle = member.relation?.takeIf { it.isNotBlank() } ?: "Family member",
        leading = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.firstName.trim().take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreenDeep,
                )
            }
        },
        trailing = {
            Text("View", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = BrandGreen)
        },
        onClick = onClick,
    )
}

// ---------------------------------------------------------------------------
// Calc stat card
// ---------------------------------------------------------------------------

@Composable
private fun CalcStatCard(calc: CalcResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = BrandGreen.copy(alpha = 0.10f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Daily targets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCell("BMI", String.format("%.1f", calc.bmi), BrandGreen, Modifier.weight(1f))
                StatCell("TDEE", "${calc.tdee.toInt()}", BrandGreenDeep, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCell("Daily kcal", "${calc.dailyKcal.toInt()}", BrandAmber, Modifier.weight(1f))
                StatCell("Protein", "${calc.proteinG.toInt()} g", BrandGreenLight, Modifier.weight(1f))
            }
            if (calc.requiresSupervision) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(14.dp),
                ) {
                    Text(
                        "⚠️  Requires professional supervision.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Add a member",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            BrandField(firstName, onFirstName, "First name")
            BrandField(relation, onRelation, "Relation")
            BrandField(height, onHeight, "Height (cm)", number = true)

            Label("Sex")
            SingleChoiceChips(SEX, sex, onSex)

            BrandField(dob, onDob, "Date of birth (YYYY-MM-DD)")
            BrandField(weight, onWeight, "Current weight (kg)", number = true)
            BrandField(target, onTarget, "Target weight (kg)", number = true)

            Label("Activity level")
            SingleChoiceChips(ACTIVITY, activity, onActivity)
            Label("Goal")
            SingleChoiceChips(GOAL, goal, onGoal)
            Label("Diet")
            SingleChoiceChips(DIET, diet, onDiet)

            message?.let {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BrandGreen.copy(alpha = 0.12f))
                        .padding(14.dp),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = BrandGreenDeep,
                    )
                }
            }

            val h = height.toDoubleOrNull()
            val w = weight.toDoubleOrNull()
            val t = target.toDoubleOrNull()
            val valid = firstName.isNotBlank() && dob.isNotBlank() && h != null && w != null && t != null

            Button(
                onClick = {
                    if (h != null && w != null && t != null) onSubmit(h, w, t)
                },
                enabled = valid && !loading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreen),
            ) {
                Text(
                    "Add member",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
        shape = RoundedCornerShape(16.dp),
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
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandGreen,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}
