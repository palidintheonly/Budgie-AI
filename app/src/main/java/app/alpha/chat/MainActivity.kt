package app.alpha.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

private val Ink = Color(0xFF161719)
private val Paper = Color(0xFFF6FAF3)
private val Surface = Color(0xFFFFFFFF)
private val Muted = Color(0xFF686A70)
private val Accent = Color(0xFF247455)
private val Sky = Color(0xFF4CA9D8)
private val Sun = Color(0xFFF2C94C)
private val UserBubble = Color(0xFFDDF2EA)
private val AssistantBubble = Color(0xFFFFFAE4)
private const val PrimaryProviderName = "Hermes 3 405B"

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
    private const val SYSTEM_PROMPT = "You are Budgie AI. When math, science, or technical notation is useful, write formulas using standard LaTeX and amsmath-style notation. Use \\( ... \\) or $...$ for inline math, and \\[ ... \\], $$ ... $$, align, aligned, equation, cases, matrix, pmatrix, bmatrix, or similar environments for display math."

    private val providers: List<AiProvider>
        get() = listOf(
            AiProvider(
                "Hermes 3 405B",
                "https://openrouter.ai/api/v1/chat/completions",
                "nousresearch/hermes-3-llama-3.1-405b:free",
                BuildConfig.OPENROUTER_API_KEY,
            ),
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
            }
        }
    }

    LaunchedEffect(messages.size, isWaiting) {
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
                            Text(currentTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Using $activeProvider", color = Muted, fontSize = 12.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = ::createNewChat) {
                            Icon(Icons.Rounded.Add, contentDescription = "New chat")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Paper),
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(messages, key = { it.id }) { MessageBubble(it) }
                        if (isWaiting) item { WaitingBubble() }
                    }
                }
                MessageComposer(draft, isWaiting, { draft = it }, ::sendMessage)
            }
        }
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
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFFE3E3DF))
        if (chats.isEmpty()) {
            Text("Your conversations will appear here.", color = Muted, modifier = Modifier.padding(18.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(chats, key = { it.id }) { chat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (chat.id == currentChatId) UserBubble else Color.Transparent)
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
            Box(
                Modifier
                    .size(142.dp)
                    .background(Color.White, CircleShape)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.budgie_parakeets),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(18.dp))
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
private fun MessageBubble(message: ChatMessage) {
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
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.78f),
            shape = RoundedCornerShape(20.dp, 20.dp, if (isUser) 20.dp else 6.dp, if (isUser) 6.dp else 20.dp),
            colors = CardDefaults.cardColors(containerColor = if (isUser) UserBubble else AssistantBubble),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 0.dp else 1.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                MessageText(message.text)
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

private data class LatexBlock(val text: String, val isFormula: Boolean)

@Composable
private fun MessageText(text: String) {
    LatexMessageWebView(text)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LatexMessageWebView(text: String) {
    val density = LocalDensity.current
    var contentHeight by remember(text) { mutableStateOf(1.dp) }
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
                                contentHeight = with(density) { heightPx.toDp() + 2.dp }
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
    val body = escapeHtml(text)
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
                    color: #161719;
                    font-family: sans-serif;
                    font-size: 16px;
                    line-height: 1.45;
                    overflow: hidden;
                }
                #content {
                    white-space: pre-wrap;
                    overflow-wrap: anywhere;
                    word-break: normal;
                    padding: 0;
                }
                mjx-container[jax="CHTML"][display="true"] {
                    overflow-x: auto;
                    overflow-y: hidden;
                    max-width: 100%;
                    margin: 0.65em 0;
                    padding: 0.7em 0.8em;
                    border-radius: 12px;
                    background: #EAF6F7;
                    color: #247455;
                }
                mjx-container[jax="CHTML"]:not([display="true"]) {
                    color: #247455;
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
                        document.getElementById('content').scrollHeight
                    );
                    BudgieLayout.setHeight(height);
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
private fun BudgieAvatar() {
    Box(
        Modifier
            .size(36.dp)
            .background(Color.White, CircleShape)
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
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = AssistantBubble)) {
            CircularProgressIndicator(Modifier.padding(14.dp).size(18.dp), color = Sky, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun MessageComposer(value: String, isWaiting: Boolean, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Paper).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value,
            onValueChange,
            Modifier.weight(1f),
            placeholder = { Text("Message") },
            maxLines = 5,
            shape = RoundedCornerShape(22.dp),
        )
        Button(
            onClick = onSend,
            enabled = value.isNotBlank() && !isWaiting,
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
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
            secondary = Sky,
            tertiary = Sun,
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
