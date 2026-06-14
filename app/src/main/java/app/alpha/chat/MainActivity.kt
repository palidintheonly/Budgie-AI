package app.alpha.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private val Ink = Color(0xFF161719)
private val Paper = Color(0xFFF7F7F5)
private val Surface = Color(0xFFFFFFFF)
private val Muted = Color(0xFF686A70)
private val Accent = Color(0xFF315C4A)
private val UserBubble = Color(0xFFE1EFE8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AlphaTheme { ChatApp() } }
    }
}

private enum class MessageRole { USER, ASSISTANT }

private data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
)

private data class AiProvider(
    val name: String,
    val endpoint: String,
    val model: String,
    val apiKey: String,
)

private data class ChatReply(val text: String, val providerName: String)

private object AiClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val providers: List<AiProvider>
        get() = listOf(
            AiProvider(
                name = "Gemma 4 31B",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                model = BuildConfig.OPENROUTER_MODEL,
                apiKey = BuildConfig.OPENROUTER_API_KEY,
            ),
            AiProvider(
                name = "Hermes 3 405B",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                model = "nousresearch/hermes-3-llama-3.1-405b:free",
                apiKey = BuildConfig.OPENROUTER_API_KEY,
            ),
            AiProvider(
                name = "OpenRouter Free Router",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                model = "openrouter/free",
                apiKey = BuildConfig.OPENROUTER_API_KEY,
            ),
            AiProvider(
                BuildConfig.PROVIDER_2_NAME,
                BuildConfig.PROVIDER_2_ENDPOINT,
                BuildConfig.PROVIDER_2_MODEL,
                BuildConfig.PROVIDER_2_API_KEY,
            ),
            AiProvider(
                BuildConfig.PROVIDER_3_NAME,
                BuildConfig.PROVIDER_3_ENDPOINT,
                BuildConfig.PROVIDER_3_MODEL,
                BuildConfig.PROVIDER_3_API_KEY,
            ),
            AiProvider(
                BuildConfig.PROVIDER_4_NAME,
                BuildConfig.PROVIDER_4_ENDPOINT,
                BuildConfig.PROVIDER_4_MODEL,
                BuildConfig.PROVIDER_4_API_KEY,
            ),
        ).filter { provider ->
            provider.name.isNotBlank() &&
                provider.endpoint.isNotBlank() &&
                provider.model.isNotBlank() &&
                provider.apiKey.isNotBlank()
        }

    fun send(messages: List<ChatMessage>, callback: (Result<ChatReply>) -> Unit) {
        executor.execute {
            val result = runCatching { requestWithFallback(messages) }
            mainHandler.post { callback(result) }
        }
    }

    private fun requestWithFallback(messages: List<ChatMessage>): ChatReply {
        check(providers.isNotEmpty()) { "No AI provider is configured." }
        val failures = mutableListOf<String>()
        providers.forEach { provider ->
            try {
                return ChatReply(request(provider, messages), provider.name)
            } catch (error: Exception) {
                failures += "${provider.name}: ${error.message ?: "request failed"}"
            }
        }
        error("All AI providers failed. ${failures.joinToString(" | ")}")
    }

    private fun request(provider: AiProvider, messages: List<ChatMessage>): String {
        val requestBody = JSONObject().apply {
            put("model", provider.model)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                        put("content", message.text)
                    })
                }
            })
        }.toString()

        val connection = (URL(provider.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
            setRequestProperty("Content-Type", "application/json")
        }

        return try {
            connection.outputStream.bufferedWriter().use { it.write(requestBody) }
            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = JSONObject(responseText.ifBlank { "{}" })
            if (connection.responseCode !in 200..299) {
                val message = response.optJSONObject("error")?.optString("message")
                    ?: "Request failed (${connection.responseCode})."
                error(message)
            }
            response.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .ifBlank { error("The model returned an empty response.") }
        } finally {
            connection.disconnect()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatApp() {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var draft by rememberSaveable { mutableStateOf("") }
    var isWaiting by rememberSaveable { mutableStateOf(false) }
    var activeProvider by rememberSaveable { mutableStateOf("Gemma 4 31B") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, isWaiting) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun startNewChat() {
        messages.clear()
        draft = ""
        isWaiting = false
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty() || isWaiting) return
        val userMessage = ChatMessage(System.nanoTime(), MessageRole.USER, text)
        messages += userMessage
        draft = ""
        isWaiting = true
        AiClient.send(messages.toList()) { result ->
            val reply = result.getOrNull()
            if (reply != null) activeProvider = reply.providerName
            messages += ChatMessage(
                id = System.nanoTime(),
                role = MessageRole.ASSISTANT,
                text = reply?.text
                    ?: result.exceptionOrNull()?.message
                    ?: "Unable to get a response.",
            )
            isWaiting = false
        }
    }

    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Chat", fontWeight = FontWeight.SemiBold)
                        Text("Using $activeProvider", color = Muted, fontSize = 12.sp)
                    }
                },
                actions = {
                    IconButton(onClick = ::startNewChat) {
                        Icon(Icons.Rounded.Add, contentDescription = "New chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
            )
        },
        bottomBar = {
            MessageComposer(
                value = draft,
                isWaiting = isWaiting,
                onValueChange = { draft = it },
                onSend = ::sendMessage,
            )
        },
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyChat(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (isWaiting) {
                    item { WaitingBubble() }
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Start a conversation",
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ask a question or describe what you need help with.",
                color = Muted,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.86f),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) UserBubble else Surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 0.dp else 1.dp),
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                color = Ink,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun WaitingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Surface),
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(14.dp)
                    .size(18.dp),
                color = Accent,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun MessageComposer(
    value: String,
    isWaiting: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Paper)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            maxLines = 5,
            shape = RoundedCornerShape(22.dp),
        )
        Button(
            onClick = onSend,
            enabled = value.isNotBlank() && !isWaiting,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
        }
    }
}

@Composable
private fun AlphaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            background = Paper,
            onBackground = Ink,
            surface = Surface,
            onSurface = Ink,
        ),
        content = content,
    )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChatPreview() {
    AlphaTheme { ChatApp() }
}
