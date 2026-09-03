package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DictionarySheet
import com.example.ui.components.StatusCard
import com.example.ui.components.TextInputCard
import com.example.ui.components.TextOutputCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeCount by viewModel.dictionaryCount.collectAsStateWithLifecycle()
    val allSynonyms by viewModel.allSynonyms.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // System File Picker for CSV
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importCsvUri(uri)
        }
    }

    val triggerCsvPick: () -> Unit = {
        // Use */* with fallback to text/* so users can pick any .csv regardless of device MIME mapping
        csvPickerLauncher.launch("*/*")
    }

    // React to notifications from ViewModel
    LaunchedEffect(uiState.notification) {
        uiState.notification?.let {
            snackbarHostState.showSnackbar(
                message = it.message,
                duration = SnackbarDuration.Short
            )
            viewModel.dismissNotification()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Synonym Replacer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "مستبدل المرادفات",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = triggerCsvPick,
                        modifier = Modifier.testTag("top_upload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Upload CSV"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setDictionarySheetVisible(true) },
                        modifier = Modifier.testTag("top_database_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeCount > 0) {
                                    Badge {
                                        Text(text = if (activeCount > 99) "99+" else activeCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Manage Database / إدارة قاعدة البيانات"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Status Card
                StatusCard(
                    activeCount = activeCount,
                    isLoadingCsv = uiState.isLoadingCsv,
                    onUploadClick = triggerCsvPick,
                    onClearClick = { viewModel.clearDictionary() },
                    onLoadSampleClick = { viewModel.loadSampleDictionary() },
                    onViewPairsClick = { viewModel.setDictionarySheetVisible(true) }
                )

                // 2. Input Area
                TextInputCard(
                    inputText = uiState.inputText,
                    onTextChanged = { viewModel.onInputTextChanged(it) },
                    onClearClick = { viewModel.clearInputText() },
                    onPasteClick = { viewModel.pasteInputText(it) },
                    onLoadSampleText = { viewModel.loadSampleText() }
                )

                // 3. Action Button: "Process / معالجة"
                Button(
                    onClick = { viewModel.processText() },
                    enabled = !uiState.isProcessing && activeCount > 0 && uiState.inputText.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("process_button")
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Processing... / جاري المعالجة...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Process / معالجة",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 4. Output Area
                TextOutputCard(
                    outputText = uiState.outputText,
                    processResult = uiState.lastProcessResult,
                    onClearOutput = { viewModel.clearOutputText() }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal Bottom Sheet for Database Management
    if (uiState.showDictionarySheet) {
        DictionarySheet(
            pairs = allSynonyms,
            searchQuery = uiState.searchQuery,
            onSearchChanged = { viewModel.onSearchQueryChanged(it) },
            onDismiss = { viewModel.setDictionarySheetVisible(false) },
            onUploadCsv = {
                viewModel.setDictionarySheetVisible(false)
                triggerCsvPick()
            },
            onLoadSample = { viewModel.loadSampleDictionary() },
            onClearAll = { viewModel.clearDictionary() }
        )
    }
}
