# Crucial Code Samples (AI Samples Catalog)

This document highlights the **most important code samples** in the repo that demonstrate Gemini / Firebase AI usage patterns. Each section links to the source file and line range for context.

## 1) Basic Gemini Chat (text-only)
**Purpose:** Create a model, start a chat session, and send a user message.



```kotlin
private val generativeModel by lazy {
    Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        "gemini-2.5-flash",
        generationConfig = generationConfig {
            temperature = 0.9f
            topK = 32
            topP = 1f
            maxOutputTokens = 4096
        },
        safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.HATE_SPEECH, HarmBlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, HarmBlockThreshold.MEDIUM_AND_ABOVE),
        ),
        systemInstruction = content {
            text("""You are a friendly assistant. Keep your response short.""")
        },
    )
}

private val chat = generativeModel.startChat()

val response = chat.sendMessage(message)
```

## 2) Multimodal Prompt (image + text)
**Purpose:** Send a `Bitmap` with a text prompt and return the model response.


```kotlin
suspend fun generateText(bitmap: Bitmap, prompt: String): String {
    val multimodalPrompt = content {
        image(bitmap)
        text(prompt)
    }
    val result = generativeModel.generateContent(multimodalPrompt)
    return result.text ?: ""
}
```

## 3) Streaming Responses (video summarization)
**Purpose:** Stream incremental responses from the model while summarizing video.


```kotlin
val generativeModel = Firebase.ai(backend = GenerativeBackend.vertexAI())
    .generativeModel("gemini-2.5-flash")

val requestContent = content {
    fileData(videoSource.toString(), "video/mp4")
    text(promptData)
}

val outputStringBuilder = StringBuilder()
generativeModel.generateContentStream(requestContent).collect { response ->
    outputStringBuilder.append(response.text)
}
```

## 4) Strict JSON Response with Schema
**Purpose:** Force the model to return **JSON-only** using a schema and parse it.


```kotlin
private val thumbnailsSchema = Schema.array(items = Schema.long("thumbnail timestamp in milliseconds"))

private val thumbnailsModel = Firebase.ai(backend = GenerativeBackend.vertexAI())
    .generativeModel(
        modelName = "gemini-2.5-flash",
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = thumbnailsSchema
        },
    )

val response: GenerateContentResponse = thumbnailsModel.generateContent(
    content {
        fileData(videoUri.toString(), "video/mp4")
        text("""
            Get three engaging and visually appealing thumbnails for this video.
            Focus on capturing peak moments that create curiosity.
        """.trimIndent())
    },
)

val thumbnails: List<Long> = Json.decodeFromString(response.text!!)
```

## 5) Gemini Live API + Function Calling
**Purpose:** Configure a live model with function tools and open a live session.


```kotlin
val liveGenerationConfig = liveGenerationConfig {
    speechConfig = SpeechConfig(voice = Voice("FENRIR"))
    responseModality = ResponseModality.AUDIO
}

val addTodo = FunctionDeclaration(
    "addTodo",
    "Add a task to the todo list",
    mapOf("taskDescription" to Schema.string("A succinct string describing the task")),
)

val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
    "gemini-2.5-flash-native-audio-preview-12-2025",
    generationConfig = liveGenerationConfig,
    systemInstruction = systemInstruction,
    tools = listOf(Tool.functionDeclarations(listOf(getTodoList, addTodo, removeTodo, toggleTodoStatus))),
)

session = generativeModel.connect()
```
