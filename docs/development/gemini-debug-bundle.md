# Gemini Debug Bundle (Billate MVP)

This file contains the exact current contents of the key files involved in Gemini API calls and UI triggers, plus the module Gradle file with AI SDK dependencies.

---

## app/src/main/java/com/billate/app/data/remote/GeminiRemoteDataSource.kt

```kotlin
package com.billate.app.data.remote

import android.graphics.Bitmap
import com.billate.app.data.local.ApiKeyManager
import com.billate.app.model.GeminiReceiptResponse
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRemoteDataSource @Inject constructor(
    private val apiKeyManager: ApiKeyManager,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createModel(): GenerativeModel {
        val apiKey = apiKeyManager.getApiKey()
        require(apiKey.isNotBlank()) { "API key not set. Please add your Gemini API key in Settings." }
        return GenerativeModel(
            modelName = "gemini-2.0-flash",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.2f
                topK = 32
                topP = 1f
                maxOutputTokens = 4096
            },
            systemInstruction = content {
                text("You are an OCR assistant. Extract data from receipt images and return only JSON following the exact schema provided. Do not include any extra text or markdown.")
            },
        )
    }

    suspend fun extractReceipt(bitmap: Bitmap): GeminiReceiptResponse {
        val model = createModel()
        val prompt = buildPrompt()
        val inputContent = content {
            image(bitmap)
            text(prompt)
        }
        val response = model.generateContent(inputContent)
        val rawText = response.text
            ?: throw IllegalStateException("Empty response from Gemini")

        // Strip markdown code fences if present
        val jsonText = rawText
            .replace(Regex("```json\\s*"), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        return json.decodeFromString<GeminiReceiptResponse>(jsonText)
    }

    private fun buildPrompt(): String = """
Extract the receipt details and return ONLY valid JSON following this schema:

{
  "merchant_name": "string",
  "transaction_date": "YYYY-MM-DD",
  "transaction_date_raw": "string (raw date text as seen on the receipt)",
  "currency": "VND",
  "total_amount_vnd": integer (exact VND, no decimals),
  "total_amount_raw": "string (raw text from receipt)",
  "category": "one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other",
  "line_items": [
    {
      "description": "string",
      "qty": integer,
      "amount_vnd": integer (exact VND, no decimals),
      "amount_raw": "string"
    }
  ],
  "notes": "string"
}

Rules:
1) Output only JSON. No markdown, no extra text.
2) If unsure, use empty string or 0, and explain briefly in notes.
3) Normalize amounts into VND integers (no decimals).
4) Preserve the raw amount text exactly as printed.
5) If quantity is missing, use 1.
6) For transaction_date, make a best guess in YYYY-MM-DD, and always include the exact transaction_date_raw string.
7) Category must be exactly one of: Groceries, Dining, Shopping, Transport, Utilities, Health, Entertainment, Education, Other.
    """.trimIndent()
}
```

---

## app/src/main/java/com/billate/app/domain/ProcessBillUseCase.kt

```kotlin
package com.billate.app.domain

import android.net.Uri
import com.billate.app.data.BillRepository
import com.billate.app.model.BillProcessingOutcome
import javax.inject.Inject

class ProcessBillUseCase @Inject constructor(
    private val repository: BillRepository,
) {
    suspend operator fun invoke(imageUri: Uri): BillProcessingOutcome =
        repository.processBill(imageUri)
}
```

---

## app/src/main/java/com/billate/app/viewmodel/HomeViewModel.kt

```kotlin
package com.billate.app.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.billate.app.data.BillRepository
import com.billate.app.data.local.ApiKeyManager
import com.billate.app.domain.ProcessBillUseCase
import com.billate.app.model.BillProcessingOutcome
import com.billate.app.model.BillTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    data object Initial : HomeUiState()
    data object Processing : HomeUiState()
    data class AutoSaved(val bill: BillTransaction) : HomeUiState()
    data class ReviewNeeded(val bill: BillTransaction, val reason: String) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: BillRepository,
    private val processBill: ProcessBillUseCase,
    private val apiKeyManager: ApiKeyManager,
) : ViewModel() {

    val bills: StateFlow<List<BillTransaction>> = repository.getBills()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Initial)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun hasApiKey(): Boolean = apiKeyManager.hasApiKey()

    fun onImageSelected(uri: Uri) {
        _uiState.value = HomeUiState.Processing
        viewModelScope.launch {
            when (val result = processBill(uri)) {
                is BillProcessingOutcome.AutoSaved -> {
                    _uiState.value = HomeUiState.AutoSaved(result.bill)
                }
                is BillProcessingOutcome.RequiresReview -> {
                    _uiState.value = HomeUiState.ReviewNeeded(result.bill, result.reason)
                }
                is BillProcessingOutcome.Failed -> {
                    _uiState.value = HomeUiState.Error(result.message)
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = HomeUiState.Initial
    }
}
```

---

## app/src/main/java/com/billate/app/ui/screens/HomeScreen.kt

```kotlin
package com.billate.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.billate.app.model.BillTransaction
import com.billate.app.viewmodel.HomeUiState
import com.billate.app.viewmodel.HomeViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToReview: (BillTransaction) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val bills by viewModel.bills.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPickerDialog by remember { mutableStateOf(false) }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { viewModel.onImageSelected(it) }
    }

    // React to processing outcome
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is HomeUiState.AutoSaved -> {
                Toast.makeText(context, "Bill saved automatically!", Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            is HomeUiState.ReviewNeeded -> {
                onNavigateToReview(state.bill)
                viewModel.resetState()
            }
            is HomeUiState.Error -> {
                Toast.makeText(context, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Billate") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (viewModel.hasApiKey()) {
                        showPickerDialog = true
                    } else {
                        Toast.makeText(context, "Please add your API key in Settings first", Toast.LENGTH_LONG).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add bill")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (uiState is HomeUiState.Processing) {
                // Processing overlay
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing receipt…", style = MaterialTheme.typography.bodyLarge)
                }
            } else if (bills.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No bills yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Tap + to scan a receipt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    items(bills, key = { it.id }) { bill ->
                        BillCard(bill = bill)
                    }
                }
            }
        }
    }

    // Image source picker dialog
    if (showPickerDialog) {
        AlertDialog(
            onDismissRequest = { showPickerDialog = false },
            title = { Text("Add a Bill") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPickerDialog = false
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Choose from Gallery", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPickerDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
```

---

## app/build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.billate.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.billate.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Google Generative AI SDK (direct API key, no Firebase needed)
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
```
