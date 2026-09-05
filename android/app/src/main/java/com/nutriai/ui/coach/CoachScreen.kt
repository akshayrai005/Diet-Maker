package com.nutriai.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutriai.ui.home.ChatViewModel
import com.nutriai.ui.theme.BrandGreen
import com.nutriai.ui.theme.BrandGreenDeep
import com.nutriai.ui.theme.BrandGreenLight
import com.nutriai.ui.theme.HeroGradientTop
import com.nutriai.ui.theme.HeroGradientBottom
import com.nutriai.ui.theme.Radius
import com.nutriai.ui.theme.Spacing
import com.nutriai.ui.theme.kaizenColors

// ---------------------------------------------------------------------------
// Premium "AI Coach" chat screen for NutriAI.
// One self-contained file. Reuses com.nutriai.ui.home.ChatViewModel.
// Design language matches PremiumDashboard (rounded cards, brand gradients).
// ---------------------------------------------------------------------------

private val QuickSuggestions = listOf(
    "How much protein?",
    "Can I eat rice?",
    "Weight-loss pace?",
    "Is this meal healthy?",
)

@Composable
fun CoachScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = state.messages

    // Red-flag safety net - surfaced alongside the coach's normal reply.
    state.redFlagMessage?.let { msg ->
        com.nutriai.ui.safety.RedFlagDialog(message = msg, onDismiss = { viewModel.clearRedFlag() })
    }

    // Keep the newest message in view as the conversation grows.
    LaunchedEffect(messages.size, state.sending) {
        val count = messages.size + if (state.sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = com.nutriai.ui.theme.Spacing.screenHorizontal),
    ) {
        Spacer(Modifier.height(16.dp))
        CoachHeader()
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(messages) { m ->
                ChatBubble(text = m.text, fromUser = m.fromUser)
            }
            if (state.sending) {
                item { TypingBubble() }
            }
        }

        SuggestionChips(
            enabled = !state.sending,
            onPick = { viewModel.send(it) },
        )

        InputBar(
            input = input,
            onInputChange = { input = it },
            sending = state.sending,
            onSend = {
                val text = input.trim()
                if (text.isNotBlank()) {
                    viewModel.send(text)
                    input = ""
                }
            },
        )

        Text(
            "Educational guidance, not medical advice - consult a professional.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Header - brand-gradient avatar + title/subtitle
// ---------------------------------------------------------------------------

@Composable
private fun CoachHeader() {
    val tokens = MaterialTheme.kaizenColors
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(
                Brush.horizontalGradient(listOf(tokens.cardGradientStart, tokens.cardGradientEnd)),
            )
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(BrandGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = BrandGreen, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "🤖 Kaizen Coach",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "💬 Your dietitian, in your pocket",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat bubbles
// ---------------------------------------------------------------------------

@Composable
private fun ChatBubble(text: String, fromUser: Boolean) {
    val tokens = MaterialTheme.kaizenColors
    val bubbleShape = if (fromUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        val bubbleMod = Modifier
            .widthIn(max = 300.dp)
            .clip(bubbleShape)
            .let { m ->
                if (fromUser) m.background(BrandGreen)
                else m.background(Brush.verticalGradient(listOf(tokens.cardGradientStart, tokens.cardGradientEnd)))
                    .border(1.dp, tokens.glassBorder, bubbleShape)
            }
        Box(modifier = bubbleMod) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (fromUser) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Card(
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = BrandGreen,
                )
                Text(
                    "Coach is typing…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Quick-suggestion chips
// ---------------------------------------------------------------------------

@Composable
private fun SuggestionChips(enabled: Boolean, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickSuggestions.forEach { suggestion ->
            AssistChip(
                onClick = { if (enabled) onPick(suggestion) },
                enabled = enabled,
                label = { Text(suggestion) },
                shape = RoundedCornerShape(20.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = BrandGreen.copy(alpha = 0.1f),
                    labelColor = BrandGreen,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Input row
// ---------------------------------------------------------------------------

@Composable
private fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    sending: Boolean,
    onSend: () -> Unit,
) {
    val canSend = !sending && input.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask your coach anything…") },
            singleLine = true,
            shape = RoundedCornerShape(26.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandGreen,
                cursorColor = BrandGreen,
            ),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = canSend, onClick = onSend)
                .semantics { contentDescription = "Send message" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
