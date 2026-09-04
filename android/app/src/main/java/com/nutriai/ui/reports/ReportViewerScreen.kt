package com.nutriai.ui.reports

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutriai.data.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportViewerState(
    val range: String = "weekly", // weekly | monthly
    val count: Int = 1,
    val html: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ReportViewerViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportViewerState())
    val state: StateFlow<ReportViewerState> = _state.asStateFlow()

    init { load() }

    fun setRange(range: String) { _state.value = _state.value.copy(range = range); load() }
    fun setCount(count: Int) { _state.value = _state.value.copy(count = count); load() }

    fun load() {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val r = repository.reportHtml(s.range, s.count)
            _state.value = if (r.isSuccess) {
                _state.value.copy(loading = false, html = r.getOrDefault(""))
            } else {
                _state.value.copy(loading = false, error = r.exceptionOrNull()?.message ?: "Couldn't load report")
            }
        }
    }
}

/** Renders the currently-loaded report WebView to a PDF via Android's print system. */
private fun printReport(context: Context, web: WebView) {
    val jobName = "Kaizen Health Report"
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = web.createPrintDocumentAdapter(jobName)
    pm.print(jobName, adapter, PrintAttributes.Builder().build())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportViewerScreen(
    onClose: () -> Unit,
    autoPrint: Boolean = false,
    viewModel: ReportViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var printed by remember { mutableStateOf(false) }

    // Download mode: once the beautiful report has rendered, open the Save-as-PDF sheet.
    LaunchedEffect(autoPrint, pageReady, state.html) {
        if (autoPrint && pageReady && !printed && state.html.isNotEmpty()) {
            printed = true
            webRef?.let { printReport(context, it) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Controls bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // (Back is provided by the parent "← Me" header - no duplicate here.)
            // Range dropdown: Weekly / Monthly
            var rangeOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = rangeOpen, onExpandedChange = { rangeOpen = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = if (state.range == "monthly") "Monthly" else "Weekly",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Period") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rangeOpen) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = rangeOpen, onDismissRequest = { rangeOpen = false }) {
                    DropdownMenuItem(text = { Text("Weekly") }, onClick = { viewModel.setRange("weekly"); rangeOpen = false })
                    DropdownMenuItem(text = { Text("Monthly") }, onClick = { viewModel.setRange("monthly"); rangeOpen = false })
                }
            }

            // Count dropdown: how many weeks / months
            var countOpen by remember { mutableStateOf(false) }
            val unit = if (state.range == "monthly") "mo" else "wk"
            ExposedDropdownMenuBox(expanded = countOpen, onExpandedChange = { countOpen = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = "${state.count} $unit",
                    onValueChange = {}, readOnly = true,
                    label = { Text("How many") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(countOpen) },
                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = countOpen, onDismissRequest = { countOpen = false }) {
                    listOf(1, 2, 3, 4, 6).forEach { c ->
                        DropdownMenuItem(text = { Text("$c $unit") }, onClick = { viewModel.setCount(c); countOpen = false })
                    }
                }
            }

            TextButton(onClick = { webRef?.let { printReport(context, it) } }, enabled = state.html.isNotEmpty()) { Text("Save PDF") }
        }

        Box(Modifier.fillMaxSize()) {
            if (state.error != null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewModel.load() }) { Text("Retry") }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = false
                            settings.textZoom = 100
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) { pageReady = true }
                            }
                            webRef = this
                        }
                    },
                    update = { web ->
                        if (state.html.isNotEmpty()) {
                            web.loadDataWithBaseURL(null, state.html, "text/html", "utf-8", null)
                        }
                    },
                )
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
