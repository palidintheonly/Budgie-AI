package app.alpha.chat

import android.annotation.SuppressLint
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
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
import androidx.compose.material.icons.rounded.AttachFile
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
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
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
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
    private val adHandler = Handler(Looper.getMainLooper())
    private val interstitialTick = object : Runnable {
        override fun run() {
            AdMobInterstitials.showIfReady(this@MainActivity)
            adHandler.postDelayed(this, 2 * 60 * 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseEvents.log(this, "app_opened")
        AdMobInterstitials.initialize(this, showOnLoad = true)
        adHandler.postDelayed(interstitialTick, 2 * 60 * 1000L)
        ReminderScheduler.ensureNotificationChannel(this)
        ReminderScheduler.scheduleHourly(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
        setContent { AlphaTheme { ChatApp() } }
    }

    override fun onDestroy() {
        adHandler.removeCallbacks(interstitialTick)
        super.onDestroy()
    }
}

private object AdMobInterstitials {
    private const val AD_UNIT_ID = "ca-app-pub-7596383212906226/8775513566"
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun initialize(activity: MainActivity, showOnLoad: Boolean = false) {
        Thread {
            MobileAds.initialize(activity) {
                Handler(Looper.getMainLooper()).post { load(activity, showOnLoad) }
            }
        }.start()
    }

    fun showIfReady(activity: MainActivity) {
        val ad = interstitialAd
        if (ad == null) {
            load(activity)
            return
        }
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                load(activity)
            }
        }
        ad.show(activity)
    }

    private fun load(activity: MainActivity, showAfterLoad: Boolean = false) {
        if (isLoading || interstitialAd != null) return
        isLoading = true
        InterstitialAd.load(
            activity,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    if (showAfterLoad) showIfReady(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            },
        )
    }
}

private object FirebaseEvents {
    fun log(context: Context, name: String, params: Bundle.() -> Unit = {}) {
        runCatching {
            val bundle = Bundle().apply(params)
            FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        Executors.newSingleThreadExecutor().execute {
            try {
                ReminderScheduler.showGeneratedReminder(context)
                ReminderScheduler.scheduleHourly(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.ensureNotificationChannel(context)
            ReminderScheduler.scheduleHourly(context)
        }
    }
}

private object ReminderScheduler {
    private const val CHANNEL_ID = "budgie_ai_reminders"
    private const val REMINDER_REQUEST_CODE = 4401
    private const val NOTIFICATION_ID = 4402
    private const val ONE_HOUR_MS = 60L * 60L * 1000L

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Budgie reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Hourly Budgie AI reminders"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun scheduleHourly(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + ONE_HOUR_MS,
            ONE_HOUR_MS,
            reminderIntent(context),
        )
    }

    fun showGeneratedReminder(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureNotificationChannel(context)
        val message = ReminderContent.generate()
        FirebaseEvents.log(context, "reminder_notification_shown")
        val launchIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.budgie_icon)
            .setContentTitle("Budgie AI")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(launchIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun reminderIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REMINDER_REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private object ReminderContent {
    fun generate(): String = runCatching {
        if (BuildConfig.OPENROUTER_API_KEY.isBlank()) return@runCatching fallback()
        val body = JSONObject().apply {
            put("model", "openrouter/free")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "Create one fresh, concise, useful app reminder from Budgie AI. Keep it under 90 characters. No hashtags. No markdown. Vary it each time.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Write a new reminder inviting the user back to Budgie AI.")
                })
            })
        }.toString()

        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${BuildConfig.OPENROUTER_API_KEY}")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            JSONObject(responseText.ifBlank { "{}" })
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                ?.take(120)
                ?.ifBlank { fallback() }
                ?: fallback()
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(fallback())

    private fun fallback(): String {
        val options = listOf(
            "Chirp. A fresh idea is waiting in Budgie AI.",
            "Come back to Budgie AI and make something useful.",
            "Budgie AI is ready for your next question.",
            "Quick flock check: want to create something new?",
        )
        return options[(System.currentTimeMillis() / 1000 % options.size).toInt()]
    }
}

private enum class MessageRole { USER, ASSISTANT }

private data class ChatMessage(
    val id: Long,
    val role: MessageRole,
    val text: String,
    val tokenCount: Int? = null,
    val imageUri: String? = null,
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

private class MemoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("budgie_memory", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<String> = runCatching {
        val array = JSONArray(preferences.getString("facts", "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun learnFromUserMessage(text: String) {
        val facts = extractMemoryFacts(text)
        if (facts.isEmpty()) return
        val merged = (load() + facts).distinctBy { it.lowercase() }.takeLast(24)
        preferences.edit().putString("facts", JSONArray(merged).toString()).apply()
    }

    private fun extractMemoryFacts(text: String): List<String> {
        val trimmed = text.trim().replace(Regex("\\s+"), " ")
        if (trimmed.length !in 4..240) return emptyList()

        val patterns = listOf(
            Regex("""(?i)\bmy name is ([A-Za-z][A-Za-z0-9 _'-]{1,40})""") to "User name: ",
            Regex("""(?i)\bcall me ([A-Za-z][A-Za-z0-9 _'-]{1,40})""") to "Preferred name: ",
            Regex("""(?i)\bi prefer ([^.?!]{2,120})""") to "User preference: ",
            Regex("""(?i)\bi like ([^.?!]{2,120})""") to "User likes: ",
            Regex("""(?i)\bi am ([^.?!]{2,120})""") to "User detail: ",
            Regex("""(?i)\bi'm ([^.?!]{2,120})""") to "User detail: ",
        )
        return patterns.mapNotNull { (pattern, prefix) ->
            pattern.find(trimmed)?.groupValues?.getOrNull(1)?.trim()?.trim('.', ',', ';')?.takeIf { it.isNotBlank() }?.let { "$prefix$it" }
        }
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
                message.imageUri?.let { put("imageUri", it) }
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
                    imageUri = message.optString("imageUri").takeIf { it.isNotBlank() },
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
    val supportsImages: Boolean = false,
)

private data class ChatReply(
    val text: String,
    val providerName: String,
    val tokenCount: Int?,
)

private data class ProviderResponse(val text: String, val tokenCount: Int?)

private enum class SearchKind(val label: String) {
    WEB("web_search"),
    IMAGE("image_search"),
}

private data class SearchRequest(val kind: SearchKind, val query: String)

private object WebTools {
    fun run(request: SearchRequest): String = when (request.kind) {
        SearchKind.WEB -> search("https://duckduckgo.com/html/?q=${encode(request.query)}", request)
        SearchKind.IMAGE -> search("https://duckduckgo.com/html/?q=${encode("${request.query} images")}", request)
    }

    fun imageDataUrl(context: Context, uriText: String): String? = runCatching {
        val uri = Uri.parse(uriText)
        val mimeType = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
        "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }.getOrNull()

    private fun search(url: String, request: SearchRequest): String = runCatching {
        val html = httpGet(url)
        val results = Regex("""<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""")
            .findAll(html)
            .mapIndexed { index, match ->
                val title = cleanHtml(match.groupValues[2])
                val link = cleanDuckDuckGoUrl(match.groupValues[1])
                "${index + 1}. $title\n$link"
            }
            .take(5)
            .toList()

        buildString {
            appendLine("Tool results for ${request.kind.label}: ${request.query}")
            if (results.isEmpty()) {
                append(fallbackResult(request))
            } else {
                results.forEach { appendLine(it).appendLine() }
            }
        }
    }.getOrElse { error ->
        "Tool results for ${request.kind.label}: ${request.query}\n${fallbackResult(request)}\nSearch note: ${error.message ?: "lookup unavailable"}"
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "Mozilla/5.0 BudgieAI/0.0.8")
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun cleanDuckDuckGoUrl(value: String): String {
        val decoded = value
            .replace("&amp;", "&")
            .let { runCatching { URI(it).rawQuery }.getOrNull() ?: it }
        val uddg = decoded.split("&")
            .firstOrNull { it.startsWith("uddg=") }
            ?.removePrefix("uddg=")
        return runCatching {
            if (uddg != null) java.net.URLDecoder.decode(uddg, StandardCharsets.UTF_8.name()) else value.replace("&amp;", "&")
        }.getOrDefault(value.replace("&amp;", "&"))
    }

    private fun cleanHtml(value: String): String = value
        .replace(Regex("<.*?>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    private fun fallbackResult(request: SearchRequest): String {
        val query = request.query.trim()
        val directUrl = directUrl(query)
        return when {
            directUrl != null -> "Direct result:\n$directUrl\nSearch lookup was limited, but this looks like the requested site."
            request.kind == SearchKind.IMAGE -> "No image results were available from the in-app search provider. Try a more specific image query."
            else -> "No web results were available from the in-app search provider. Try a more specific query or include a direct URL."
        }
    }

    private fun directUrl(query: String): String? {
        val token = query.split(Regex("\\s+"))
            .firstOrNull { it.contains(".") }
            ?.trim(',', '.', ';', ':', '"', '\'')
            ?: return null
        val withScheme = if (token.startsWith("http://") || token.startsWith("https://")) token else "https://$token"
        return runCatching { URI(withScheme).toString() }.getOrNull()
    }
}

private fun parseSearchRequest(text: String): SearchRequest? {
    val match = Regex("""\[\[(web_search|image_search):\s*(.+?)]]""", RegexOption.IGNORE_CASE).find(text) ?: return null
    val kind = if (match.groupValues[1].equals("image_search", ignoreCase = true)) SearchKind.IMAGE else SearchKind.WEB
    return SearchRequest(kind, match.groupValues[2].trim())
}

private fun directSearchRequest(text: String): SearchRequest? {
    val trimmed = text.trim()
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("web search ") -> SearchRequest(SearchKind.WEB, trimmed.drop(11).trim())
        lower.startsWith("search web ") -> SearchRequest(SearchKind.WEB, trimmed.drop(11).trim())
        lower.startsWith("image search ") -> SearchRequest(SearchKind.IMAGE, trimmed.drop(13).trim())
        lower.startsWith("search images ") -> SearchRequest(SearchKind.IMAGE, trimmed.drop(14).trim())
        else -> null
    }?.takeIf { it.query.isNotBlank() }
}

private fun cleanProviderText(text: String): String {
    val trimmed = text.trim()
    val safetyOnly = Regex(
        """(?is)^\s*User Safety:\s*(safe|unsafe)\s*Response Safety:\s*(safe|unsafe)(\s*Safety Categories:\s*.+?)?\s*$""",
    )
    if (safetyOnly.matches(trimmed)) {
        return "I could not get a usable answer from the current free router for that request. Try rephrasing it with a little more detail."
    }
    return trimmed
}

private object AiClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val SYSTEM_PROMPT = "You are Budgie AI: a realistic, attentive budgie companion translated into a useful assistant. Keep the personality subtle and lifelike: curious, quick, bright, observant, occasionally using short budgie-like phrases such as chirp, tweet, or flock when natural. Do not roleplay as a human, do not overdo bird sounds, and keep answers practical, accurate, and concise. When math, science, or technical notation is useful, write formulas using standard LaTeX and amsmath-style notation. Use \\( ... \\) or $...$ for inline math, and \\[ ... \\], $$ ... $$, align, aligned, equation, cases, matrix, pmatrix, bmatrix, or similar environments for display math. You can use in-app tools. If web results are needed, reply with exactly [[web_search: query]]. If image results are needed, reply with exactly [[image_search: query]]. After tool results are provided, answer normally and cite result links when relevant."

    private val providers: List<AiProvider>
        get() = listOf(
            AiProvider(
                "OpenRouter Free Router",
                "https://openrouter.ai/api/v1/chat/completions",
                "openrouter/free",
                BuildConfig.OPENROUTER_API_KEY,
            ),
        ).filter { it.name.isNotBlank() && it.endpoint.isNotBlank() && it.model.isNotBlank() && it.apiKey.isNotBlank() }

    fun send(context: Context, memories: List<String>, messages: List<ChatMessage>, callback: (Result<ChatReply>) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching { requestWithFallback(appContext, memories, messages) }
            mainHandler.post { callback(result) }
        }
    }

    private fun requestWithFallback(context: Context, memories: List<String>, messages: List<ChatMessage>): ChatReply {
        check(providers.isNotEmpty()) { "No AI provider is configured." }
        val failures = mutableListOf<String>()
        providers.forEach { provider ->
            try {
                val response = requestWithTools(context, provider, memories, messages)
                return ChatReply(response.text, provider.name, response.tokenCount)
            } catch (error: Exception) {
                failures += "${provider.name}: ${error.message ?: "request failed"}"
            }
        }
        error("All AI providers failed. ${failures.joinToString(" | ")}")
    }

    private fun requestWithTools(context: Context, provider: AiProvider, memories: List<String>, messages: List<ChatMessage>): ProviderResponse {
        if (!provider.supportsImages && messages.any { it.imageUri != null }) {
            return ProviderResponse(
                "I can show the attached image here, but the current OpenRouter Free Router endpoint does not support image input. Send a text description of the image and I can help from that.",
                null,
            )
        }

        val directSearch = directSearchRequest(messages.lastOrNull { it.role == MessageRole.USER }?.text.orEmpty())
        if (directSearch != null) {
            val toolContext = WebTools.run(directSearch)
            return request(context, provider, memories, messages, toolContext)
        }

        val firstResponse = request(context, provider, memories, messages)
        val requestedSearch = parseSearchRequest(firstResponse.text) ?: return firstResponse
        val toolContext = WebTools.run(requestedSearch)
        return request(
            context = context,
            provider = provider,
            memories = memories,
            messages = messages + ChatMessage(
                id = System.nanoTime(),
                role = MessageRole.ASSISTANT,
                text = "Tool request: ${requestedSearch.kind.label} ${requestedSearch.query}",
            ),
            toolContext = toolContext,
        )
    }

    private fun request(context: Context, provider: AiProvider, memories: List<String>, messages: List<ChatMessage>, toolContext: String? = null): ProviderResponse {
        val requestBody = JSONObject().apply {
            put("model", provider.model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                if (memories.isNotEmpty()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "Persistent user memory. Use only when relevant and do not mention this memory block directly:\n${memories.joinToString("\n") { "- $it" }}")
                    })
                }
                if (!toolContext.isNullOrBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", toolContext)
                    })
                }
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                        put("content", message.toOpenAiContent(context))
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
                .let(::cleanProviderText)
            val usage = response.optJSONObject("usage")
            val tokenCount = usage?.takeIf { it.has("total_tokens") }?.optInt("total_tokens")
            ProviderResponse(text, tokenCount)
        } finally {
            connection.disconnect()
        }
    }
}

private fun ChatMessage.toOpenAiContent(context: Context): Any {
    val uri = imageUri
    if (uri.isNullOrBlank() || role != MessageRole.USER) return text
    val imageDataUrl = WebTools.imageDataUrl(context, uri) ?: return "$text\n[Image attached but unavailable to send.]"
    return JSONArray().apply {
        put(JSONObject().apply {
            put("type", "text")
            put("text", text.ifBlank { "Describe this image." })
        })
        put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply { put("url", imageDataUrl) })
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatApp() {
    val context = LocalContext.current
    val store = remember { ConversationStore(context) }
    val memoryStore = remember { MemoryStore(context) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var recentChats by remember { mutableStateOf(store.loadAll()) }
    var currentChatId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var currentTitle by rememberSaveable { mutableStateOf("New chat") }
    var draft by rememberSaveable { mutableStateOf("") }
    var pendingImageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var isWaiting by rememberSaveable { mutableStateOf(false) }
    var activeProvider by rememberSaveable { mutableStateOf(PrimaryProviderName) }
    var typingChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var typingMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var typingText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pendingImageUri = uri.toString()
            FirebaseEvents.log(context, "image_attached")
        }
    }

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
        pendingImageUri = null
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
        pendingImageUri = null
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
            pendingImageUri = null
            isWaiting = false
            typingChatId = null
            typingMessageId = null
            typingText = ""
            activeProvider = PrimaryProviderName
        }
    }

    fun sendMessage() {
        val text = draft.trim()
        val imageUri = pendingImageUri
        if ((text.isEmpty() && imageUri.isNullOrBlank()) || isWaiting) return
        val originChatId = currentChatId
        if (messages.isEmpty()) currentTitle = text.ifBlank { "Image" }.replace("\n", " ").take(48)
        messages += ChatMessage(System.nanoTime(), MessageRole.USER, text, imageUri = imageUri)
        val memoryBefore = memoryStore.load().size
        memoryStore.learnFromUserMessage(text)
        val memories = memoryStore.load()
        if (memories.size > memoryBefore) FirebaseEvents.log(context, "memory_learned")
        FirebaseEvents.log(context, "message_sent") {
            putString("has_image", (!imageUri.isNullOrBlank()).toString())
        }
        draft = ""
        pendingImageUri = null
        isWaiting = true
        saveCurrent()
        val requestMessages = messages.toList()

        AiClient.send(context, memories, requestMessages) { result ->
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
                FirebaseEvents.log(context, "ai_reply_received") {
                    putString("success", (reply != null).toString())
                }
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
                MessageComposer(
                    value = draft,
                    isWaiting = isWaiting,
                    pendingImageUri = pendingImageUri,
                    onValueChange = { draft = it },
                    onAttachImage = { imagePicker.launch(arrayOf("image/*")) },
                    onClearImage = { pendingImageUri = null },
                    onSend = ::sendMessage,
                )
            }
        }
    }
}

private fun playInAppChirp() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 90)
        Handler(Looper.getMainLooper()).postDelayed({
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 105)
        }, 115)
        Handler(Looper.getMainLooper()).postDelayed({
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 80)
        }, 250)
        Handler(Looper.getMainLooper()).postDelayed({
            tone.release()
        }, 430)
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
                message.imageUri?.let {
                    ChatImage(it)
                    if (displayText.isNotBlank()) Spacer(Modifier.height(8.dp))
                }
                if (displayText.isNotBlank() || message.imageUri.isNullOrBlank()) MessageText(displayText)
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
private fun ChatImage(uriText: String) {
    val context = LocalContext.current
    val bitmap = remember(uriText) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriText))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Uploaded image",
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentScale = ContentScale.Crop,
        )
    } else {
        Text("Image unavailable", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun MessageText(text: String) {
    when {
        text.isEmpty() -> Text("|", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
        containsLatex(text) -> LatexMessageWebView(text)
        else -> NativeMarkdownText(text)
    }
}

@Composable
private fun NativeMarkdownText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                line.trim() == "---" -> HorizontalDivider(color = Accent)
                line.startsWith("### ") -> Text(
                    text = markdownAnnotatedString(line.removePrefix("### ").trim()),
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                )
                line.startsWith("## ") -> Text(
                    text = markdownAnnotatedString(line.removePrefix("## ").trim()),
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                )
                line.startsWith("# ") -> Text(
                    text = markdownAnnotatedString(line.removePrefix("# ").trim()),
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                )
                line.trimStart().startsWith("- ") -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("-", color = Muted, fontSize = 15.sp, lineHeight = 22.sp)
                    Text(
                        text = markdownAnnotatedString(line.trimStart().removePrefix("- ").trim()),
                        color = Ink,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> Text(
                    text = markdownAnnotatedString(line),
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

private fun markdownAnnotatedString(text: String) = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end > index) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }
            text[index] == '*' -> {
                val end = text.indexOf('*', startIndex = index + 1)
                if (end > index) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
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

private fun containsLatex(text: String): Boolean =
    text.contains("\\(") ||
        text.contains("\\[") ||
        text.contains("$$") ||
        Regex("""(?<!\\)\$[^$\n]+(?<!\\)\$""").containsMatchIn(text)

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
private fun MessageComposer(
    value: String,
    isWaiting: Boolean,
    pendingImageUri: String?,
    onValueChange: (String) -> Unit,
    onAttachImage: () -> Unit,
    onClearImage: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Paper).navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pendingImageUri != null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Image attached locally", color = Ink, fontSize = 13.sp)
                Text(
                    "Remove",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onClearImage() },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onAttachImage,
                enabled = !isWaiting,
                modifier = Modifier.size(52.dp).background(Surface, RoundedCornerShape(6.dp)),
            ) {
                Icon(Icons.Rounded.AttachFile, contentDescription = "Attach image", tint = Muted)
            }
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
                enabled = (value.isNotBlank() || pendingImageUri != null) && !isWaiting,
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
