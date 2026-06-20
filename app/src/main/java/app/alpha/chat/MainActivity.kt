package app.alpha.chat

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

private val Ink = Color(0xFFF8FAFC)
private val Paper = Color(0xFF09090B)
private val Surface = Color(0xFF18181B)
private val Muted = Color(0xFFA1A1AA)
private val Accent = Color(0xFF3F3F46)
private val UserBubble = Color(0xFF27272A)
private val AssistantBubble = Color(0xFF111113)
private const val PrimaryProviderName = "OpenRouter Free Router"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AlphaTheme { ChatApp() } }
    }
}

private enum class MessageRole { USER, ASSISTANT }

private data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val tokenCount: Int? = null,
)

private data class Conversation(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val providerName: String,
    val messages: List<ChatMessage>,
)

private class ConversationStore(context: Context) {
    private val preferences = context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)

    @Synchronized
    fun loadAll(): List<Conversation> = runCatching {
        val array = JSONArray(preferences.getString("conversations", "[]"))
        buildList {
            for (index in 0 until array.length()) add(array.getJSONObject(index).toConversation())
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    @Synchronized
    fun upsert(conversation: Conversation) {
        if (conversation.messages.isEmpty()) return
        val conversations = loadAll().filterNot { it.id == conversation.id } + conversation
        saveAll(conversations.sortedByDescending { it.updatedAt }.take(100))
    }

    @Synchronized
    fun delete(id: String) {
        saveAll(loadAll().filterNot { it.id == id })
    }

    private fun saveAll(conversations: List<Conversation>) {
        val array = JSONArray()
        conversations.forEach { array.put(it.toJson()) }
        preferences.edit().putString("conversations", array.toString()).apply()
    }
}

private fun Conversation.toJson() = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("updatedAt", updatedAt)
    put("providerName", providerName)
    put("messages", JSONArray().apply {
        messages.forEach { message ->
            put(JSONObject().apply {
                put("id", message.id)
                put("role", message.role.name)
                put("text", message.text)
                message.tokenCount?.let { put("tokenCount", it) }
            })
        }
    })
}

private fun JSONObject.toConversation(): Conversation {
    val messageArray = optJSONArray("messages") ?: JSONArray()
    val messages = buildList {
        for (index in 0 until messageArray.length()) {
            val message = messageArray.getJSONObject(index)
            add(
                ChatMessage(
                    id = message.optLong("id", System.nanoTime()),
                    role = runCatching { MessageRole.valueOf(message.optString("role")) }
                        .getOrDefault(MessageRole.USER),
                    text = message.optString("text"),
                    tokenCount = if (message.has("tokenCount")) message.optInt("tokenCount") else null,
                ),
            )
        }
    }
    return Conversation(
        id = optString("id", UUID.randomUUID().toString()),
        title = optString("title", "New chat"),
        updatedAt = optLong("updatedAt", 0L),
        providerName = optString("providerName", PrimaryProviderName),
        messages = messages,
    )
}

private data class AiProvider(
    val name: String,
    val endpoint: String,
    val model: String,
    val apiKey: String,
)

private data class ChatReply(
    val text: String,
    val providerName: String,
    val tokenCount: Int?,
)

private data class ProviderResponse(val text: String, val tokenCount: Int?)

private object AiClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val SYSTEM_PROMPT = "You are Budgie AI: a realistic, attentive budgie companion translated into a useful assistant. Keep the personality subtle and lifelike: curious, quick, bright, observant, occasionally using short budgie-like phrases such as chirp, tweet, or flock when natural. Do not roleplay as a human, do not overdo bird sounds, and keep answers practical, accurate, and concise. When math, science, or technical notation is useful, write formulas using standard LaTeX and amsmath-style notation. Use \\( ... \\) or $...$ for inline math, and \\[ ... \\], $$ ... $$, align, aligned, equation, cases, matrix, pmatrix, bmatrix, or similar environments for display math."

    private val providers: List<AiProvider>
        get() = listOf(
            AiProvider(
                "OpenRouter Free Router",
                "https://openrouter.ai/api/v1/chat/completions",
                "openrouter/free",
                BuildConfig.OPENROUTER_API_KEY,
            ),
        ).filter { it.name.isNotBlank() && it.endpoint.isNotBlank() && it.model.isNotBlank() && it.apiKey.isNotBlank() }

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
                val response = request(provider, messages)
                return ChatReply(response.text, provider.name, response.tokenCount)
            } catch (error: Exception) {
                failures += "${provider.name}: ${error.message ?: "request failed"}"
            }
        }
        error("All AI providers failed. ${failures.joinToString(" | ")}")
    }

    private fun request(provider: AiProvider, messages: List<ChatMessage>): ProviderResponse {
        val requestBody = JSONObject().apply {
            put("model", provider.model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
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
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = JSONObject(responseText.ifBlank { "{}" })
            if (connection.responseCode !in 200..299) {
                error(response.optJSONObject("error")?.optString("message") ?: "Request failed (${connection.responseCode}).")
            }
            val text = response.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
                .ifBlank { error("The model returned an empty response.") }
            val usage = response.optJSONObject("usage")
            val tokenCount = usage?.takeIf { it.has("total_tokens") }?.optInt("total_tokens")
            ProviderResponse(text, tokenCount)
        } finally {
            connection.disconnect()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatApp() {
    val context = LocalContext.current
    val store = remember { ConversationStore(context) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var recentChats by remember { mutableStateOf(store.loadAll()) }
    var currentChatId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var currentTitle by rememberSaveable { mutableStateOf("New chat") }
    var draft by rememberSaveable { mutableStateOf("") }
    var isWaiting by rememberSaveable { mutableStateOf(false) }
    var activeProvider by rememberSaveable { mutableStateOf(PrimaryProviderName) }
    var typingChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var typingMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var typingText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun currentConversation(): Conversation = Conversation(
        id = currentChatId,
        title = currentTitle,
        updatedAt = System.currentTimeMillis(),
        providerName = activeProvider,
        messages = messages.toList(),
    )

    fun saveCurrent() {
        if (messages.isNotEmpty()) {
            store.upsert(currentConversation())
            recentChats = store.loadAll()
        }
    }

    fun createNewChat() {
        saveCurrent()
        currentChatId = UUID.randomUUID().toString()
        currentTitle = "New chat"
        messages.clear()
        draft = ""
        isWaiting = false
        typingChatId = null
        typingMessageId = null
        typingText = ""
        activeProvider = PrimaryProviderName
        scope.launch { drawerState.close() }
    }

    fun openChat(conversation: Conversation) {
        saveCurrent()
        currentChatId = conversation.id
        currentTitle = conversation.title
        activeProvider = conversation.providerName
        messages.clear()
        messages.addAll(conversation.messages)
        draft = ""
        isWaiting = false
        typingChatId = null
        typingMessageId = null
        typingText = ""
        scope.launch { drawerState.close() }
    }

    fun deleteChat(conversation: Conversation) {
        store.delete(conversation.id)
        recentChats = store.loadAll()
        if (conversation.id == currentChatId) {
            currentChatId = UUID.randomUUID().toString()
            currentTitle = "New chat"
            messages.clear()
            draft = ""
            isWaiting = false
            typingChatId = null
            typingMessageId = null
            typingText = ""
            activeProvider = PrimaryProviderName
        }
    }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty() || isWaiting) return
        val originChatId = currentChatId
        if (messages.isEmpty()) currentTitle = text.replace("\n", " ").take(48)
        messages += ChatMessage(System.nanoTime(), MessageRole.USER, text)
        draft = ""
        isWaiting = true
        saveCurrent()
        val requestMessages = messages.toList()

        AiClient.send(requestMessages) { result ->
            val reply = result.getOrNull()
            val replyMessage = ChatMessage(
                id = System.nanoTime(),
                role = MessageRole.ASSISTANT,
                text = reply?.text ?: result.exceptionOrNull()?.message ?: "Unable to get a response.",
                tokenCount = reply?.tokenCount,
            )
            val savedConversation = store.loadAll().firstOrNull { it.id == originChatId }
            if (savedConversation != null) {
                store.upsert(
                    savedConversation.copy(
                        updatedAt = System.currentTimeMillis(),
                        providerName = reply?.providerName ?: savedConversation.providerName,
                        messages = savedConversation.messages + replyMessage,
                    ),
                )
                recentChats = store.loadAll()
            }
            if (currentChatId == originChatId) {
                if (reply != null) activeProvider = reply.providerName
                messages += replyMessage
                isWaiting = false
                playInAppChirp()
                typingChatId = originChatId
                typingMessageId = replyMessage.id
                typingText = ""
                scope.launch {
                    replyMessage.text.forEachIndexed { index, char ->
                        if (typingChatId != originChatId || typingMessageId != replyMessage.id) return@launch
                        typingText = replyMessage.text.take(index + 1)
                        delay(if (char == '\n') 28L else 12L)
                    }
                    if (typingChatId == originChatId && typingMessageId == replyMessage.id) {
                        typingChatId = null
                        typingMessageId = null
                        typingText = ""
                    }
                }
            }
        }
    }

    LaunchedEffect(messages.size, isWaiting, typingText.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 340.dp),
                drawerContainerColor = Paper,
            ) {
                RecentChatsDrawer(
                    chats = recentChats,
                    currentChatId = currentChatId,
                    onNewChat = ::createNewChat,
                    onOpenChat = ::openChat,
                    onDeleteChat = ::deleteChat,
                )
            }
        },
    ) {
        Scaffold(
            containerColor = Paper,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Recent chats")
                        }
                    },
                    title = {
                        Column {
                            Text(currentTitle, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Using $activeProvider", color = Muted, fontSize = 12.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = ::createNewChat) {
                            Icon(Icons.Rounded.Add, contentDescription = "New chat")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Paper,
                        navigationIconContentColor = Ink,
                        actionIconContentColor = Ink,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                if (messages.isEmpty()) {
                    EmptyChat(Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageBubble(
                                message = message,
                                displayText = if (message.id == typingMessageId) typingText else message.text,
                            )
                        }
                        if (isWaiting) item { WaitingBubble() }
                    }
                }
                MessageComposer(draft, isWaiting, { draft = it }, ::sendMessage)
            }
        }
    }
}

private fun playInAppChirp() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
        Handler(Looper.getMainLooper()).postDelayed({
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 70)
        }, 75)
        Handler(Looper.getMainLooper()).postDelayed({
            tone.release()
        }, 220)
    }
}

@Composable
private fun RecentChatsDrawer(
    chats: List<Conversation>,
    currentChatId: String,
    onNewChat: () -> Unit,
    onOpenChat: (Conversation) -> Unit,
    onDeleteChat: (Conversation) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Recent chats", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onNewChat) {
                Icon(Icons.Rounded.Add, contentDescription = "New chat")
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Accent)
        if (chats.isEmpty()) {
            Text("Your conversations will appear here.", color = Muted, modifier = Modifier.padding(18.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(chats, key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (chat.id == currentChatId) Surface else Color.Transparent)
                            .clickable { onOpenChat(chat) }
                            .padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text("${chat.messages.size} messages", color = Muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { onDeleteChat(chat) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete ${chat.title}", tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BudgieAvatar(Modifier.size(54.dp))
            Spacer(Modifier.height(14.dp))
            Text("Budgie AI", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ask a question or describe what you need help with.",
                color = Muted,
                textAlign = TextAlign.Center,
                lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, displayText: String = message.text) {
    val isUser = message.role == MessageRole.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            BudgieAvatar()
            Spacer(Modifier.size(8.dp))
        }
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.90f else 0.86f),
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = if (isUser) UserBubble else AssistantBubble),
            border = BorderStroke(1.dp, Accent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                MessageText(displayText)
                if (!isUser) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message.tokenCount?.let { "$it tokens used" } ?: "Tokens unavailable",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageText(text: String) {
    LatexMessageWebView(text)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LatexMessageWebView(text: String) {
    var contentHeight by remember(text) { mutableStateOf(24.dp) }
    val html = remember(text) { latexHtmlDocument(text) }

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(contentHeight),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun setHeight(heightPx: Float) {
                            Handler(Looper.getMainLooper()).post {
                                contentHeight = heightPx.dp + 10.dp
                            }
                        }
                    },
                    "BudgieLayout",
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript("window.budgieTypeset && window.budgieTypeset();", null)
                    }
                }
                loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
            }
        },
        update = { view ->
            if (view.tag != html) {
                view.tag = html
                view.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
            }
        },
    )
}

private fun latexHtmlDocument(text: String): String {
    val body = markdownToHtml(text)
    return """
        <!doctype html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <script>
                window.MathJax = {
                    tex: {
                        inlineMath: [['\\(', '\\)'], ['${'$'}', '${'$'}']],
                        displayMath: [['\\[', '\\]'], ['${'$'}${'$'}', '${'$'}${'$'}']],
                        processEscapes: true,
                        packages: {'[+]': ['ams', 'mathtools', 'bbox', 'color', 'textmacros']}
                    },
                    loader: {load: ['[tex]/ams', '[tex]/mathtools', '[tex]/bbox', '[tex]/color', '[tex]/textmacros']},
                    options: {
                        skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
                    },
                    startup: {typeset: false}
                };
            </script>
            <script defer src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-chtml.js"></script>
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    background: transparent;
                    color: #F8FAFC;
                    font-family: Inter, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                    font-size: 15px;
                    line-height: 1.45;
                    overflow: visible;
                }
                #content {
                    overflow-wrap: anywhere;
                    word-break: normal;
                    padding: 0;
                }
                .p {
                    margin: 0 0 0.55em 0;
                }
                .p:last-child {
                    margin-bottom: 0;
                }
                strong {
                    font-weight: 750;
                    color: #F8FAFC;
                }
                em {
                    font-style: italic;
                    color: #F8FAFC;
                }
                h3 {
                    margin: 0.8em 0 0.35em;
                    font-size: 16px;
                    line-height: 1.25;
                    font-weight: 750;
                    color: #F8FAFC;
                }
                .bullet {
                    display: grid;
                    grid-template-columns: 14px 1fr;
                    gap: 4px;
                    margin: 0 0 0.4em 0;
                }
                .bullet::before {
                    content: "-";
                    color: #A1A1AA;
                }
                hr {
                    border: 0;
                    border-top: 1px solid #3F3F46;
                    margin: 0.8em 0;
                }
                mjx-container[jax="CHTML"][display="true"] {
                    overflow-x: auto;
                    overflow-y: visible;
                    max-width: 100%;
                    margin: 0.7em 0;
                    padding: 0.65em 0.7em;
                    border-radius: 6px;
                    border: 1px solid #3F3F46;
                    background: #18181B;
                    color: #F8FAFC;
                }
                mjx-container[jax="CHTML"]:not([display="true"]) {
                    color: #F8FAFC;
                }
            </style>
        </head>
        <body>
            <div id="content">$body</div>
            <script>
                function budgieResize() {
                    const height = Math.max(
                        document.body.scrollHeight,
                        document.documentElement.scrollHeight,
                        document.getElementById('content').scrollHeight,
                        document.getElementById('content').getBoundingClientRect().height
                    );
                    BudgieLayout.setHeight(Math.ceil(height));
                }
                window.budgieTypeset = function() {
                    if (window.MathJax && MathJax.typesetPromise) {
                        MathJax.typesetPromise([document.getElementById('content')])
                            .then(budgieResize)
                            .catch(budgieResize);
                    } else {
                        budgieResize();
                        setTimeout(window.budgieTypeset, 100);
                    }
                };
                window.addEventListener('load', window.budgieTypeset);
                window.addEventListener('resize', budgieResize);
                setTimeout(window.budgieTypeset, 300);
                setTimeout(budgieResize, 1200);
            </script>
        </body>
        </html>
    """.trimIndent()
}

private fun markdownToHtml(value: String): String = value
    .lines()
    .joinToString("") { rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> """<div class="p"></div>"""
            line.trim() == "---" -> "<hr>"
            line.startsWith("### ") -> "<h3>${inlineMarkdown(line.removePrefix("### ").trim())}</h3>"
            line.startsWith("## ") -> "<h3>${inlineMarkdown(line.removePrefix("## ").trim())}</h3>"
            line.startsWith("# ") -> "<h3>${inlineMarkdown(line.removePrefix("# ").trim())}</h3>"
            line.trimStart().startsWith("- ") -> """<div class="bullet">${inlineMarkdown(line.trimStart().removePrefix("- ").trim())}</div>"""
            else -> """<div class="p">${inlineMarkdown(line)}</div>"""
        }
    }

private fun inlineMarkdown(value: String): String = escapeHtml(value)
    .replace(Regex("""\*\*([^*]+)\*\*"""), "<strong>$1</strong>")
    .replace(Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)"""), "<em>$1</em>")

private fun escapeHtml(value: String): String = buildString {
    value.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}

@Composable
private fun BudgieAvatar(modifier: Modifier = Modifier.size(28.dp)) {
    Box(
        modifier
            .background(Surface, RoundedCornerShape(6.dp))
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.budgie_parakeets),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun WaitingBubble() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        BudgieAvatar()
        Spacer(Modifier.size(8.dp))
        Card(
            shape = RoundedCornerShape(6.dp),
            colors = CardDefaults.cardColors(containerColor = AssistantBubble),
            border = BorderStroke(1.dp, Accent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            CircularProgressIndicator(Modifier.padding(12.dp).size(16.dp), color = Ink, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun MessageComposer(value: String, isWaiting: Boolean, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Paper).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value,
            onValueChange,
            Modifier.weight(1f),
            placeholder = { Text("Message") },
            maxLines = 5,
            shape = RoundedCornerShape(6.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Ink,
                unfocusedTextColor = Ink,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Accent,
                focusedContainerColor = Paper,
                unfocusedContainerColor = Paper,
                cursorColor = Ink,
                focusedPlaceholderColor = Muted,
                unfocusedPlaceholderColor = Muted,
            ),
        )
        Button(
            onClick = onSend,
            enabled = value.isNotBlank() && !isWaiting,
            modifier = Modifier.size(52.dp),
            shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Ink,
                contentColor = Paper,
                disabledContainerColor = Surface,
                disabledContentColor = Muted,
            ),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
        }
    }
}

@Composable
private fun AlphaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Accent,
            onPrimary = Ink,
            secondary = Accent,
            tertiary = Accent,
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
