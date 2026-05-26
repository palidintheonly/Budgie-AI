package com.monkeymischief.game

import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Backpack
import androidx.compose.material.icons.rounded.CatchingPokemon
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberModelInstance
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CanopyTheme { CanopyApp() } }
    }
}

private val Night = Color(0xFF0B1110)
private val Panel = Color(0xFF13201B)
private val PanelHigh = Color(0xFF1D2B25)
private val Ink = Color(0xFFE8F0E3)
private val InkMuted = Color(0xFFB9C6B6)
private val Banana = Color(0xFFF4C542)
private val CanopyGreen = Color(0xFF2F7D58)
private val RiverBlue = Color(0xFF4AA3B5)
private val Threat = Color(0xFFCF6B5A)
private val Plum = Color(0xFF6D597A)
private const val BackendBaseUrl = "http://212.227.38.182:5063"
private const val BackendToken = "MonkeyMischief-0.0.4-alpha-b27bbaac"
private const val GameLogTag = "MonkeyMischief"
private const val EnemyModelMonkey = "models/enemies/animal-monkey.glb"
private const val EnemyModelParrot = "models/enemies/animal-parrot.glb"
private const val EnemyModelLion = "models/enemies/animal-lion.glb"
private const val EnemyModelTiger = "models/enemies/animal-tiger.glb"
private const val CoinsPerNpcDefeated = 18

@Composable
private fun CanopyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Banana,
            secondary = CanopyGreen,
            tertiary = RiverBlue,
            background = Night,
            surface = Panel,
            onPrimary = Night,
            onSecondary = Color.White,
            onSurface = Ink,
            onBackground = Ink
        ),
        content = content
    )
}

enum class Element(val label: String, val color: Color) {
    Vine("Vine", CanopyGreen),
    Fruit("Fruit", Banana),
    Echo("Echo", RiverBlue)
}

enum class StatusEffect(val label: String) {
    None("Clear"),
    Tangled("Tangled"),
    Dazed("Dazed"),
    Guarded("Guarded")
}

data class Move(
    val name: String,
    val element: Element,
    val power: Int,
    val energyCost: Int,
    val cooldown: Int = 0,
    val status: StatusEffect = StatusEffect.None,
    val description: String
)

enum class BattleItemKind { HealHp, HealStatus, FullRestore, BoostAttack }

data class BattleItem(
    val id: String,
    val name: String,
    val kind: BattleItemKind,
    val amount: Int,
    val description: String
)

data class BagStack(
    val item: BattleItem,
    val count: Int
)

data class StoreOffer(
    val item: BattleItem,
    val price: Int,
    val limitHint: String
)

data class Fighter(
    val name: String,
    val species: String,
    val element: Element,
    @param:DrawableRes val art: Int,
    val avatarModel: String? = null,
    val maxHp: Int,
    val hp: Int,
    val maxEnergy: Int,
    val energy: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val mood: Int,
    val bond: Int,
    val status: StatusEffect = StatusEffect.None,
    val guard: Int = 0,
    val cooldowns: Map<String, Int> = emptyMap(),
    val moves: List<Move>
) {
    val isDown: Boolean get() = hp <= 0
}

data class Trainer(
    val name: String,
    val color: Color,
    val roster: List<Fighter>,
    val activeIndex: Int = 0,
    val wins: Int = 0
) {
    val active: Fighter get() = roster[activeIndex]
    val hasUsableFighter: Boolean get() = roster.any { !it.isDown }
}

enum class RoundPhase { PlayerChoice, RoundOver, MatchOver }

data class GameState(
    val turn: Int = 1,
    val opponentId: String = firstOpponent().id,
    val opponentRound: Int = 0,
    val coins: Int = 35,
    val player: Trainer = initialPlayer(),
    val ai: Trainer = firstOpponent().trainer,
    val backpack: List<BagStack> = initialBackpack(),
    val phase: RoundPhase = RoundPhase.PlayerChoice,
    val lastPlayerAction: String = "Choose a battle command.",
    val lastAiAction: String = "${firstOpponent().trainer.name} is sizing up your troop.",
    val log: List<String> = listOf("${firstOpponent().trainer.name} challenges the Elder Fig.")
)

data class AuthState(
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = false,
    val username: String? = null,
    val message: String = "Sign in or create an account to load your DB save."
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(GameState())
        private set
    var authState by mutableStateOf(AuthState())
        private set
    private val deviceId = resolveDeviceId(application)
    private val backendSlot: String get() = userId ?: "pending"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var userId: String? = null

    init {
        logVerbose("viewmodel_init turn=${state.turn} phase=${state.phase} deviceId=$deviceId auth=required")
    }

    fun login(username: String, password: String) = authenticate("login", username, password)

    fun signUp(username: String, password: String) = authenticate("signup", username, password)

    fun useMove(index: Int) {
        logVerbose("trigger=useMove index=$index phase=${state.phase} active=${state.player.active.name}")
        if (state.phase != RoundPhase.PlayerChoice) return
        val fighter = state.player.active
        val move = fighter.moves.getOrNull(index) ?: return
        val cooldown = fighter.cooldowns[move.name] ?: 0
        if (fighter.energy < move.energyCost || cooldown > 0 || fighter.isDown) {
            logVerbose("blocked=useMove move=${move.name} energy=${fighter.energy}/${fighter.maxEnergy} cooldown=$cooldown isDown=${fighter.isDown}")
            addLog("${fighter.name} cannot use ${move.name} right now.")
            return
        }
        resolveRound(PlayerCommand.Move(index))
    }

    fun defend() {
        logVerbose("trigger=defend phase=${state.phase} active=${state.player.active.name}")
        if (state.phase == RoundPhase.PlayerChoice) resolveRound(PlayerCommand.Defend)
    }

    fun useItem(itemId: String) {
        logVerbose("trigger=useItem itemId=$itemId phase=${state.phase} active=${state.player.active.name}")
        if (state.phase != RoundPhase.PlayerChoice) return
        val stack = state.backpack.firstOrNull { it.item.id == itemId && it.count > 0 }
        if (stack == null) {
            logVerbose("blocked=useItem missing itemId=$itemId backpack=${state.backpack.joinToString { "${it.item.id}:${it.count}" }}")
            addLog("That item is not in your backpack.")
            return
        }
        if (!stack.item.canApplyTo(state.player.active)) {
            logVerbose("blocked=useItem no_effect itemId=$itemId active=${state.player.active.name} hp=${state.player.active.hp}/${state.player.active.maxHp} status=${state.player.active.status}")
            addLog("${stack.item.name} would not help ${state.player.active.name} right now.")
            return
        }
        logVerbose("useItem_selected name=${stack.item.name} count=${stack.count} kind=${stack.item.kind} amount=${stack.item.amount}")
        resolveRound(PlayerCommand.UseItem(itemId))
    }

    fun buyItem(itemId: String) {
        val offer = storeOffers().firstOrNull { it.item.id == itemId }
        if (offer == null) {
            addLog("That store item is not available.")
            return
        }
        if (state.coins < offer.price) {
            addLog("Not enough coins for ${offer.item.name}.")
            return
        }
        state = state.copy(
            coins = state.coins - offer.price,
            backpack = state.backpack.addItem(offer.item, 1),
            log = (listOf("Bought ${offer.item.name} for ${offer.price} coins.") + state.log).take(12)
        )
        logVerbose("store_buy itemId=$itemId price=${offer.price} coins=${state.coins}")
        syncState("store_buy")
    }

    fun swap() {
        logVerbose("trigger=swap phase=${state.phase} activeIndex=${state.player.activeIndex} active=${state.player.active.name}")
        if (state.phase != RoundPhase.PlayerChoice) return
        val next = state.player.roster.indexOfFirstIndexed { index, fighter ->
            index != state.player.activeIndex && !fighter.isDown
        }
        if (next == -1) {
            logVerbose("blocked=swap no_resting_partner roster=${state.player.roster.joinToString { "${it.name}:${it.hp}" }}")
            addLog("No rested partner can swap in.")
            return
        }
        logVerbose("swap_selected index=$next fighter=${state.player.roster[next].name}")
        resolveRound(PlayerCommand.Swap(next))
    }

    fun nextRound() {
        logVerbose("trigger=nextRound phase=${state.phase} turn=${state.turn}")
        if (state.phase == RoundPhase.RoundOver) {
            state = state.copy(phase = RoundPhase.PlayerChoice, turn = state.turn + 1)
            logVerbose("state=nextRound turn=${state.turn} phase=${state.phase}")
            syncState("next_turn")
        }
    }

    fun resetMatch() {
        logVerbose("trigger=resetMatch oldTurn=${state.turn} oldPhase=${state.phase}")
        val next = nextOpponent(state)
        state = GameState(
            opponentId = next.id,
            opponentRound = state.opponentRound + 1,
            coins = state.coins,
            ai = next.trainer,
            backpack = state.backpack,
            log = listOf("${next.trainer.name} steps forward with a fresh party.")
        )
        logVerbose("state=resetMatch turn=${state.turn} phase=${state.phase}")
        syncState("reset_match")
    }

    private fun resolveRound(command: PlayerCommand) {
        val aiCommand = chooseAiCommand(state)
        val playerFirst = state.player.active.speed >= state.ai.active.speed
        val defeatedBefore = state.ai.roster.count { it.isDown }
        logVerbose("round_start turn=${state.turn} playerCommand=${command.describe()} aiCommand=${aiCommand.describe()} playerFirst=$playerFirst")
        var working = startRoundTick(state)
        val events = mutableListOf<String>()

        val first = if (playerFirst) Actor.Player else Actor.Ai
        val second = if (playerFirst) Actor.Ai else Actor.Player
        working = applyCommand(working, first, if (first == Actor.Player) command else aiCommand, events)
        if (working.player.hasUsableFighter && working.ai.hasUsableFighter) {
            working = applyCommand(working, second, if (second == Actor.Player) command else aiCommand, events)
        }
        working = autoPromoteDowned(working, events)
        val defeatedNow = working.ai.roster.count { it.isDown }
        val reward = (defeatedNow - defeatedBefore).coerceAtLeast(0) * CoinsPerNpcDefeated
        if (reward > 0) {
            working = working.copy(coins = working.coins + reward)
            events += "You earn $reward coins for defeating ${if (reward == CoinsPerNpcDefeated) "an NPC" else "${reward / CoinsPerNpcDefeated} NPCs"}."
        }

        val phase = when {
            !working.player.hasUsableFighter || !working.ai.hasUsableFighter -> RoundPhase.MatchOver
            else -> RoundPhase.RoundOver
        }
        val resultLine = when {
            !working.player.hasUsableFighter -> "Defeat. The rival troop wins the bout."
            !working.ai.hasUsableFighter -> "Victory. Your troop wins the bout."
            else -> "Round resolved. Prepare the next choice."
        }
        logVerbose("round_events ${events.joinToString(" | ")}")
        state = working.copy(
            phase = phase,
            lastPlayerAction = events.firstOrNull { it.startsWith("You") } ?: "You hold position.",
            lastAiAction = events.firstOrNull { it.startsWith(working.ai.name) } ?: "${working.ai.name} waits.",
            log = (events + resultLine + working.log).take(12)
        )
        logVerbose("round_end turn=${state.turn} phase=${state.phase} player=${state.player.active.name}:${state.player.active.hp}/${state.player.active.maxHp} ai=${state.ai.active.name}:${state.ai.active.hp}/${state.ai.active.maxHp}")
        syncState("round_resolved")
    }

    private fun addLog(message: String) {
        logVerbose("ui_log message=$message")
        state = state.copy(log = (listOf(message) + state.log).take(12))
    }

    private fun syncState(reason: String) {
        if (userId == null) {
            logVerbose("sync_skipped reason=$reason auth=missing")
            return
        }
        val snapshot = state.toBackendJson(reason, deviceId, userId).toString()
        logVerbose("sync_start reason=$reason url=$BackendBaseUrl/v1/game/$backendSlot bytes=${snapshot.length} turn=${state.turn} phase=${state.phase} deviceId=$deviceId userId=${userId ?: "pending"}")
        thread(name = "monkey-json-sync", isDaemon = true) {
            runCatching {
                val connection = (URL("$BackendBaseUrl/v1/game/$backendSlot").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3500
                    readTimeout = 3500
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("X-Monkey-Token", BackendToken)
                }
                connection.outputStream.use { output -> output.write(snapshot.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                logVerbose("sync_done reason=$reason status=$status response=$response")
                connection.disconnect()
            }.onFailure { error ->
                Log.e(GameLogTag, "sync_failed reason=$reason error=${error.message}", error)
            }
        }
    }

    private fun authenticate(action: String, username: String, password: String) {
        val cleanUsername = username.trim()
        if (cleanUsername.length < 3 || password.length < 6) {
            authState = authState.copy(message = "Use at least 3 characters for username and 6 for password.")
            return
        }
        authState = AuthState(isLoading = true, message = if (action == "signup") "Creating account..." else "Signing in...")
        logVerbose("auth_start action=$action username=$cleanUsername url=$BackendBaseUrl/v1/auth/$action")
        thread(name = "monkey-auth-$action", isDaemon = true) {
            runCatching {
                val payload = JSONObject()
                    .put("username", cleanUsername)
                    .put("password", password)
                    .put("deviceId", deviceId)
                    .put("client", "android")
                    .put("version", "0.0.9-alpha")
                    .toString()
                val connection = (URL("$BackendBaseUrl/v1/auth/$action").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3500
                    readTimeout = 3500
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("X-Monkey-Token", BackendToken)
                }
                connection.outputStream.use { output -> output.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                if (status in 200..299) {
                    val body = JSONObject(response)
                    userId = body.optString("userId").takeIf { it.isNotBlank() }
                    val accountName = body.optString("username", cleanUsername)
                    mainHandler.post {
                        authState = AuthState(
                            isAuthenticated = userId != null,
                            username = accountName,
                            message = if (action == "signup") "Account created." else "Signed in."
                        )
                    }
                    logVerbose("auth_done action=$action status=$status userId=${userId ?: "missing"} username=$accountName response=$response")
                    loadStateThenSync(action)
                } else {
                    val message = JSONObject(response.ifBlank { "{}" }).optString("error", "Sign-in failed.")
                    mainHandler.post { authState = AuthState(message = message) }
                    logVerbose("auth_failed_status action=$action status=$status response=$response")
                }
                connection.disconnect()
            }.onFailure { error ->
                mainHandler.post { authState = AuthState(message = "Network error: ${error.message}") }
                Log.e(GameLogTag, "auth_failed action=$action deviceId=$deviceId error=${error.message}", error)
            }
        }
    }

    private fun loadStateThenSync(reason: String) {
        logVerbose("load_start reason=$reason url=$BackendBaseUrl/v1/game/$backendSlot")
        runCatching {
            val connection = (URL("$BackendBaseUrl/v1/game/$backendSlot").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("X-Monkey-Token", BackendToken)
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            connection.disconnect()

            if (status !in 200..299) {
                logVerbose("load_failed_status status=$status response=$response")
                syncState(reason)
                return
            }

            val body = JSONObject(response)
            val remoteState = body.optJSONObject("state")
            if (!body.optBoolean("exists", false) || remoteState == null) {
                logVerbose("load_empty reason=$reason slot=$backendSlot")
                syncState(reason)
                return
            }

            val loaded = remoteState.toGameState()
            mainHandler.post {
                state = loaded
                logVerbose("load_done slot=$backendSlot turn=${loaded.turn} phase=${loaded.phase} userId=${userId ?: "missing"}")
            }
        }.onFailure { error ->
            Log.e(GameLogTag, "load_failed reason=$reason error=${error.message}", error)
            syncState(reason)
        }
    }
}

private fun logVerbose(message: String) {
    Log.v(GameLogTag, message)
    println("$GameLogTag: $message")
}

private fun resolveDeviceId(application: Application): String {
    val raw = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)
    return raw
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9_.-]"), "_")
        ?.takeIf { it.isNotBlank() }
        ?: "unknown-device"
}

private enum class Actor { Player, Ai }

private sealed class PlayerCommand {
    data class Move(val index: Int) : PlayerCommand()
    data object Defend : PlayerCommand()
    data class UseItem(val itemId: String) : PlayerCommand()
    data class Swap(val index: Int) : PlayerCommand()
}

private fun PlayerCommand.describe(): String = when (this) {
    is PlayerCommand.Move -> "Move(index=$index)"
    PlayerCommand.Defend -> "Defend"
    is PlayerCommand.UseItem -> "UseItem(itemId=$itemId)"
    is PlayerCommand.Swap -> "Swap(index=$index)"
}

private fun startRoundTick(state: GameState): GameState {
    fun tickFighter(fighter: Fighter): Fighter {
        val cooled = fighter.cooldowns.mapValues { (_, value) -> (value - 1).coerceAtLeast(0) }
        val status = if (fighter.status == StatusEffect.Guarded) StatusEffect.None else fighter.status
        return fighter.copy(
            energy = (fighter.energy + 2).coerceAtMost(fighter.maxEnergy),
            guard = (fighter.guard - 1).coerceAtLeast(0),
            status = status,
            cooldowns = cooled
        )
    }
    return state.copy(
        player = state.player.updateActive(::tickFighter),
        ai = state.ai.updateActive(::tickFighter)
    )
}

private fun applyCommand(state: GameState, actor: Actor, command: PlayerCommand, events: MutableList<String>): GameState {
    return when (command) {
        is PlayerCommand.Move -> applyMove(state, actor, command.index, events)
        PlayerCommand.Defend -> applyDefend(state, actor, events)
        is PlayerCommand.UseItem -> applyItem(state, command.itemId, events)
        is PlayerCommand.Swap -> applySwap(state, actor, command.index, events)
    }
}

private fun applyMove(state: GameState, actor: Actor, moveIndex: Int, events: MutableList<String>): GameState {
    val attackerTrainer = if (actor == Actor.Player) state.player else state.ai
    val defenderTrainer = if (actor == Actor.Player) state.ai else state.player
    val attacker = attackerTrainer.active
    val defender = defenderTrainer.active
    val move = attacker.moves.getOrNull(moveIndex) ?: return state
    if (attacker.isDown || defender.isDown) return state
    val cooldown = attacker.cooldowns[move.name] ?: 0
    if (attacker.energy < move.energyCost || cooldown > 0) {
        events += "${actor.label()} ${attacker.name} tries ${move.name}, but it is not ready."
        return state
    }
    val typeBonus = elementBonus(move.element, defender.element)
    val moodBonus = if (actor == Actor.Player && attacker.mood > 70) 2 else 0
    val statusPenalty = if (attacker.status == StatusEffect.Dazed) 4 else 0
    val guardReduction = defender.guard * 5
    val raw = move.power + attacker.attack + moodBonus + typeBonus - defender.defense - guardReduction - statusPenalty
    val damage = raw.coerceAtLeast(if (move.power > 0) 2 else 0)
    val newDefender = defender.copy(
        hp = (defender.hp - damage).coerceAtLeast(0),
        status = if (move.status != StatusEffect.None && damage > 0) move.status else defender.status
    )
    val newAttacker = attacker.copy(
        energy = (attacker.energy - move.energyCost).coerceAtLeast(0),
        cooldowns = attacker.cooldowns + (move.name to move.cooldown)
    )
    events += "${actor.label()} ${attacker.name} uses ${move.name} for $damage damage${if (typeBonus > 0) " with advantage" else ""}."
    val updatedAttacker = attackerTrainer.replaceActive(newAttacker)
    val updatedDefender = defenderTrainer.replaceActive(newDefender)
    return if (actor == Actor.Player) {
        state.copy(player = updatedAttacker, ai = updatedDefender)
    } else {
        state.copy(player = updatedDefender, ai = updatedAttacker)
    }
}

private fun applyDefend(state: GameState, actor: Actor, events: MutableList<String>): GameState {
    val trainer = if (actor == Actor.Player) state.player else state.ai
    val fighter = trainer.active.copy(guard = 2, status = StatusEffect.Guarded, energy = (trainer.active.energy + 2).coerceAtMost(trainer.active.maxEnergy))
    events += "${actor.label()} ${trainer.active.name} defends and stores energy."
    return if (actor == Actor.Player) state.copy(player = trainer.replaceActive(fighter)) else state.copy(ai = trainer.replaceActive(fighter))
}

private fun applyItem(state: GameState, itemId: String, events: MutableList<String>): GameState {
    val stack = state.backpack.firstOrNull { it.item.id == itemId && it.count > 0 } ?: return state
    val fighter = state.player.active
    val item = stack.item
    if (!item.canApplyTo(fighter)) {
        events += "${item.name} would not help ${fighter.name} right now."
        return state
    }
    val updated = when (item.kind) {
        BattleItemKind.HealHp -> fighter.copy(hp = (fighter.hp + item.amount).coerceAtMost(fighter.maxHp))
        BattleItemKind.HealStatus -> fighter.copy(status = StatusEffect.None)
        BattleItemKind.FullRestore -> fighter.copy(hp = fighter.maxHp, status = StatusEffect.None)
        BattleItemKind.BoostAttack -> fighter.copy(attack = fighter.attack + item.amount, mood = (fighter.mood + 4).coerceAtMost(100))
    }
    events += "You use ${item.name} on ${fighter.name}. ${item.description}"
    return state.copy(
        backpack = state.backpack.map {
            if (it.item.id == itemId) it.copy(count = (it.count - 1).coerceAtLeast(0)) else it
        },
        player = state.player.replaceActive(updated)
    )
}

private fun BattleItem.canApplyTo(fighter: Fighter): Boolean = when (kind) {
    BattleItemKind.HealHp -> !fighter.isDown && fighter.hp < fighter.maxHp
    BattleItemKind.HealStatus -> !fighter.isDown && fighter.status != StatusEffect.None
    BattleItemKind.FullRestore -> fighter.hp < fighter.maxHp || fighter.status != StatusEffect.None
    BattleItemKind.BoostAttack -> !fighter.isDown
}

private fun applySwap(state: GameState, actor: Actor, index: Int, events: MutableList<String>): GameState {
    val trainer = if (actor == Actor.Player) state.player else state.ai
    if (index !in trainer.roster.indices || trainer.roster[index].isDown) return state
    events += "${actor.label()} swaps to ${trainer.roster[index].name}."
    return if (actor == Actor.Player) state.copy(player = trainer.copy(activeIndex = index)) else state.copy(ai = trainer.copy(activeIndex = index))
}

private fun autoPromoteDowned(state: GameState, events: MutableList<String>): GameState {
    fun promote(trainer: Trainer, owner: String): Trainer {
        if (!trainer.active.isDown) return trainer
        val next = trainer.roster.indexOfFirst { !it.isDown }
        if (next >= 0) events += "$owner sends in ${trainer.roster[next].name}."
        return if (next >= 0) trainer.copy(activeIndex = next) else trainer
    }
    return state.copy(
        player = promote(state.player, "You"),
        ai = promote(state.ai, state.ai.name)
    )
}

private fun chooseAiCommand(state: GameState): PlayerCommand {
    val ai = state.ai.active
    val player = state.player.active
    val nextBench = state.ai.roster.indexOfFirstIndexed { index, fighter -> index != state.ai.activeIndex && !fighter.isDown && fighter.hp > ai.hp }
    if (ai.hp < ai.maxHp * 0.28f && nextBench >= 0 && Random(state.turn + ai.hp).nextFloat() < 0.35f) return PlayerCommand.Swap(nextBench)
    if (ai.hp < ai.maxHp * 0.35f && ai.guard == 0) return PlayerCommand.Defend
    val bestMove = ai.moves
        .mapIndexed { index, move -> index to move }
        .filter { (_, move) -> ai.energy >= move.energyCost && (ai.cooldowns[move.name] ?: 0) == 0 }
        .maxByOrNull { (_, move) -> move.power + elementBonus(move.element, player.element) + if (move.status != StatusEffect.None && player.status == StatusEffect.None) 6 else 0 }
    return bestMove?.let { PlayerCommand.Move(it.first) } ?: PlayerCommand.Defend
}

private fun elementBonus(attacking: Element, defending: Element): Int = when {
    attacking == Element.Vine && defending == Element.Echo -> 5
    attacking == Element.Echo && defending == Element.Fruit -> 5
    attacking == Element.Fruit && defending == Element.Vine -> 5
    else -> 0
}

private fun Trainer.updateActive(update: (Fighter) -> Fighter): Trainer = replaceActive(update(active))

private fun Trainer.replaceActive(fighter: Fighter): Trainer {
    return copy(roster = roster.mapIndexed { index, current -> if (index == activeIndex) fighter else current })
}

private inline fun <T> List<T>.indexOfFirstIndexed(predicate: (Int, T) -> Boolean): Int {
    for (index in indices) if (predicate(index, this[index])) return index
    return -1
}

private fun Actor.label(): String = if (this == Actor.Player) "You" else "The rival troop"

private fun GameState.toBackendJson(reason: String, deviceId: String, userId: String?): JSONObject = JSONObject()
    .put("reason", reason)
    .put("deviceId", deviceId)
    .put("userId", userId ?: JSONObject.NULL)
    .put("opponentId", opponentId)
    .put("opponentRound", opponentRound)
    .put("coins", coins)
    .put("turn", turn)
    .put("phase", phase.name)
    .put("lastPlayerAction", lastPlayerAction)
    .put("lastAiAction", lastAiAction)
    .put("player", player.toJson())
    .put("ai", ai.toJson())
    .put("backpack", JSONArray().also { array -> backpack.forEach { array.put(it.toJson()) } })
    .put("log", JSONArray().also { array -> log.forEach { array.put(it) } })

private fun Trainer.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("activeIndex", activeIndex)
    .put("active", active.toJson())
    .put("wins", wins)
    .put("roster", JSONArray().also { array -> roster.forEach { array.put(it.toJson()) } })

private fun Fighter.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("species", species)
    .put("element", element.name)
    .put("avatarModel", avatarModel ?: JSONObject.NULL)
    .put("maxHp", maxHp)
    .put("hp", hp)
    .put("maxEnergy", maxEnergy)
    .put("energy", energy)
    .put("attack", attack)
    .put("defense", defense)
    .put("speed", speed)
    .put("mood", mood)
    .put("bond", bond)
    .put("status", status.name)
    .put("guard", guard)
    .put("cooldowns", JSONObject().also { json -> cooldowns.forEach { (name, value) -> json.put(name, value) } })
    .put("moves", JSONArray().also { array -> moves.forEach { array.put(it.toJson()) } })

private fun Move.toJson(): JSONObject = JSONObject()
    .put("name", name)
    .put("element", element.name)
    .put("power", power)
    .put("energyCost", energyCost)
    .put("cooldown", cooldown)
    .put("status", status.name)
    .put("description", description)

private fun BagStack.toJson(): JSONObject = JSONObject()
    .put("count", count)
    .put("item", JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("kind", item.kind.name)
        .put("amount", item.amount)
        .put("description", item.description)
    )

private fun JSONObject.toGameState(): GameState {
    val fallback = GameState()
    val opponentId = optString("opponentId", fallback.opponentId)
    val opponent = opponentById(opponentId)
    return GameState(
        turn = optInt("turn", fallback.turn).coerceAtLeast(1),
        opponentId = opponent.id,
        opponentRound = optInt("opponentRound", fallback.opponentRound).coerceAtLeast(0),
        coins = optInt("coins", fallback.coins).coerceAtLeast(0),
        player = optJSONObject("player")?.toTrainer(Banana, fallback.player) ?: fallback.player,
        ai = optJSONObject("ai")?.toTrainer(opponent.color, opponent.trainer) ?: opponent.trainer,
        backpack = optJSONArray("backpack")?.toBagStacks(fallback.backpack) ?: fallback.backpack,
        phase = enumValueOrDefault(optString("phase"), fallback.phase),
        lastPlayerAction = optString("lastPlayerAction", fallback.lastPlayerAction),
        lastAiAction = optString("lastAiAction", fallback.lastAiAction),
        log = optJSONArray("log")?.toStringList().orEmpty().ifEmpty { fallback.log }.take(12)
    )
}

private fun JSONObject.toTrainer(color: Color, fallback: Trainer): Trainer {
    val roster = optJSONArray("roster")?.toFighters(fallback.roster) ?: fallback.roster
    return Trainer(
        name = optString("name", fallback.name),
        color = color,
        roster = roster,
        activeIndex = optInt("activeIndex", fallback.activeIndex).coerceIn(roster.indices),
        wins = optInt("wins", fallback.wins)
    )
}

private fun JSONArray.toFighters(fallback: List<Fighter>): List<Fighter> {
    return List(length().coerceAtLeast(fallback.size)) { index ->
        optJSONObject(index)?.toFighter(fallback.getOrNull(index) ?: fallback.first()) ?: fallback.getOrNull(index) ?: fallback.first()
    }
}

private fun JSONObject.toFighter(fallback: Fighter): Fighter {
    val moves = optJSONArray("moves")?.toMoves(fallback.moves) ?: fallback.moves
    val name = optString("name", fallback.name)
    return Fighter(
        name = name,
        species = optString("species", fallback.species),
        element = enumValueOrDefault(optString("element"), fallback.element),
        art = artForFighter(name, fallback.art),
        avatarModel = optString("avatarModel").takeIf { it.isNotBlank() } ?: fallback.avatarModel,
        maxHp = optInt("maxHp", fallback.maxHp).coerceAtLeast(1),
        hp = optInt("hp", fallback.hp).coerceAtLeast(0),
        maxEnergy = optInt("maxEnergy", fallback.maxEnergy).coerceAtLeast(1),
        energy = optInt("energy", fallback.energy).coerceAtLeast(0),
        attack = optInt("attack", fallback.attack),
        defense = optInt("defense", fallback.defense),
        speed = optInt("speed", fallback.speed),
        mood = optInt("mood", fallback.mood),
        bond = optInt("bond", fallback.bond),
        status = enumValueOrDefault(optString("status"), fallback.status),
        guard = optInt("guard", fallback.guard).coerceAtLeast(0),
        cooldowns = optJSONObject("cooldowns")?.toIntMap() ?: fallback.cooldowns,
        moves = moves
    )
}

private fun JSONArray.toMoves(fallback: List<Move>): List<Move> {
    return List(length().coerceAtLeast(fallback.size)) { index ->
        optJSONObject(index)?.toMove(fallback.getOrNull(index) ?: fallback.first()) ?: fallback.getOrNull(index) ?: fallback.first()
    }
}

private fun JSONObject.toMove(fallback: Move): Move = Move(
    name = optString("name", fallback.name),
    element = enumValueOrDefault(optString("element"), fallback.element),
    power = optInt("power", fallback.power),
    energyCost = optInt("energyCost", fallback.energyCost),
    cooldown = optInt("cooldown", fallback.cooldown),
    status = enumValueOrDefault(optString("status"), fallback.status),
    description = optString("description", fallback.description)
)

private fun JSONArray.toBagStacks(fallback: List<BagStack>): List<BagStack> {
    val saved = List(length()) { index ->
        optJSONObject(index)?.toBagStack(fallback.getOrNull(index) ?: fallback.first())
    }.filterNotNull()
    val savedById = saved.associateBy { it.item.id }
    val merged = fallback.map { stack ->
        savedById[stack.item.id]?.copy(item = stack.item) ?: stack
    }
    return merged + saved.filter { savedStack -> merged.none { it.item.id == savedStack.item.id } }
}

private fun JSONObject.toBagStack(fallback: BagStack): BagStack {
    val itemJson = optJSONObject("item")
    val item = if (itemJson == null) {
        fallback.item
    } else {
        BattleItem(
            id = itemJson.optString("id", fallback.item.id),
            name = itemJson.optString("name", fallback.item.name),
            kind = enumValueOrDefault(itemJson.optString("kind"), fallback.item.kind),
            amount = itemJson.optInt("amount", fallback.item.amount),
            description = itemJson.optString("description", fallback.item.description)
        )
    }
    return BagStack(item = item, count = optInt("count", fallback.count).coerceAtLeast(0))
}

private fun List<BagStack>.addItem(item: BattleItem, amount: Int): List<BagStack> {
    if (amount <= 0) return this
    val found = any { it.item.id == item.id }
    return if (found) {
        map { stack -> if (stack.item.id == item.id) stack.copy(item = item, count = stack.count + amount) else stack }
    } else {
        this + BagStack(item, amount)
    }
}

private fun JSONArray.toStringList(): List<String> = List(length()) { index -> optString(index) }.filter { it.isNotBlank() }

private fun JSONObject.toIntMap(): Map<String, Int> {
    val values = mutableMapOf<String, Int>()
    keys().forEach { key -> values[key] = optInt(key, 0) }
    return values
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T {
    return enumValues<T>().firstOrNull { it.name == value } ?: fallback
}

@DrawableRes
private fun artForFighter(name: String, fallback: Int): Int = when (name) {
    "Milo", "Looptail", "Goldcap" -> R.drawable.animal_monkey
    "Kiwi", "Skyhook", "Thunderbeak" -> R.drawable.animal_parrot
    "Bruno", "Bananabandit", "Mischief Munk", "Old Fig", "Mossback" -> R.drawable.animal_gorilla
    else -> fallback
}

private fun initialPlayer() = Trainer(
    name = "You",
    color = Banana,
    roster = listOf(
        Fighter(
            name = "Milo",
            species = "Canopy Monkey",
            element = Element.Fruit,
            art = R.drawable.animal_monkey,
            maxHp = 86,
            hp = 86,
            maxEnergy = 10,
            energy = 7,
            attack = 10,
            defense = 5,
            speed = 9,
            mood = 72,
            bond = 64,
            moves = listOf(
                Move("Banana Toss", Element.Fruit, power = 15, energyCost = 3, description = "Reliable ranged hit."),
                Move("Vine Trip", Element.Vine, power = 10, energyCost = 3, status = StatusEffect.Tangled, cooldown = 1, description = "Damage and chance to tangle."),
                Move("Echo Screech", Element.Echo, power = 8, energyCost = 2, status = StatusEffect.Dazed, cooldown = 2, description = "Lower enemy focus.")
            )
        ),
        Fighter(
            name = "Kiwi",
            species = "Signal Parrot",
            element = Element.Echo,
            art = R.drawable.animal_parrot,
            maxHp = 64,
            hp = 64,
            maxEnergy = 12,
            energy = 8,
            attack = 8,
            defense = 4,
            speed = 13,
            mood = 67,
            bond = 54,
            moves = listOf(
                Move("Sky Peck", Element.Echo, power = 13, energyCost = 3, description = "Fast pressure."),
                Move("Bright Call", Element.Echo, power = 7, energyCost = 2, status = StatusEffect.Dazed, cooldown = 1, description = "Disrupt focus."),
                Move("Fruit Drop", Element.Fruit, power = 12, energyCost = 3, description = "Useful against vine fighters.")
            )
        )
    )
)

data class OpponentProfile(
    val id: String,
    val color: Color,
    val trainer: Trainer
)

private fun firstOpponent(): OpponentProfile = opponentProfiles.first()

private fun opponentById(id: String): OpponentProfile = opponentProfiles.firstOrNull { it.id == id } ?: firstOpponent()

private fun nextOpponent(state: GameState): OpponentProfile {
    val currentIndex = opponentProfiles.indexOfFirst { it.id == state.opponentId }.coerceAtLeast(0)
    val nextIndex = if (state.phase == RoundPhase.MatchOver && state.player.hasUsableFighter) {
        currentIndex + 1
    } else {
        currentIndex
    }
    return opponentProfiles[nextIndex % opponentProfiles.size]
}

private val opponentProfiles: List<OpponentProfile> = listOf(
    OpponentProfile("naughty_knuckles", Threat, naughtyKnuckles()),
    OpponentProfile("canopy_scouts", RiverBlue, canopyScouts()),
    OpponentProfile("fig_guardians", CanopyGreen, figGuardians()),
    OpponentProfile("storm_callers", Plum, stormCallers())
)

private fun naughtyKnuckles() = Trainer(
    name = "Naughty Knuckles",
    color = Threat,
    roster = listOf(
        Fighter(
            name = "Bananabandit",
            species = "Bad-Apple Gorilla",
            element = Element.Vine,
            art = R.drawable.animal_gorilla,
            avatarModel = EnemyModelLion,
            maxHp = 98,
            hp = 98,
            maxEnergy = 9,
            energy = 6,
            attack = 12,
            defense = 7,
            speed = 5,
            mood = 58,
            bond = 44,
            moves = listOf(
                Move("Branch Slam", Element.Vine, power = 18, energyCost = 4, description = "Heavy hit."),
                Move("Root Snare", Element.Vine, power = 9, energyCost = 3, status = StatusEffect.Tangled, cooldown = 1, description = "Slows the target."),
                Move("Chest Drum", Element.Echo, power = 10, energyCost = 2, cooldown = 1, description = "Low-cost pressure.")
            )
        ),
        Fighter(
            name = "Mischief Munk",
            species = "Rotten Scout Monkey",
            element = Element.Fruit,
            art = R.drawable.animal_monkey,
            avatarModel = EnemyModelMonkey,
            maxHp = 76,
            hp = 76,
            maxEnergy = 10,
            energy = 7,
            attack = 9,
            defense = 5,
            speed = 10,
            mood = 55,
            bond = 38,
            moves = listOf(
                Move("Stone Fruit", Element.Fruit, power = 14, energyCost = 3, description = "Solid hit."),
                Move("Tail Feint", Element.Vine, power = 8, energyCost = 2, status = StatusEffect.Dazed, cooldown = 1, description = "Disrupts."),
                Move("Leap Strike", Element.Echo, power = 12, energyCost = 3, description = "Fast attack.")
            )
        )
    )
)

private fun canopyScouts() = Trainer(
    name = "Canopy Scouts",
    color = RiverBlue,
    roster = listOf(
        Fighter(
            name = "Skyhook",
            species = "Ridge Parrot",
            element = Element.Echo,
            art = R.drawable.animal_parrot,
            avatarModel = EnemyModelParrot,
            maxHp = 70,
            hp = 70,
            maxEnergy = 12,
            energy = 8,
            attack = 9,
            defense = 4,
            speed = 14,
            mood = 62,
            bond = 52,
            moves = listOf(
                Move("High Cry", Element.Echo, power = 14, energyCost = 3, status = StatusEffect.Dazed, cooldown = 1, description = "Fast echo pressure."),
                Move("Wing Clip", Element.Vine, power = 9, energyCost = 2, status = StatusEffect.Tangled, description = "Slows the active fighter."),
                Move("Fruit Dive", Element.Fruit, power = 13, energyCost = 3, description = "A sharp aerial drop.")
            )
        ),
        Fighter(
            name = "Looptail",
            species = "Signal Monkey",
            element = Element.Vine,
            art = R.drawable.animal_monkey,
            avatarModel = EnemyModelMonkey,
            maxHp = 82,
            hp = 82,
            maxEnergy = 11,
            energy = 7,
            attack = 10,
            defense = 5,
            speed = 11,
            mood = 59,
            bond = 48,
            moves = listOf(
                Move("Canopy Snare", Element.Vine, power = 13, energyCost = 3, status = StatusEffect.Tangled, cooldown = 1, description = "Roots the target."),
                Move("Echo Tap", Element.Echo, power = 10, energyCost = 2, description = "Quick disruption."),
                Move("Branch Vault", Element.Vine, power = 16, energyCost = 4, cooldown = 1, description = "A committed swing.")
            )
        )
    )
)

private fun figGuardians() = Trainer(
    name = "Fig Guardians",
    color = CanopyGreen,
    roster = listOf(
        Fighter(
            name = "Old Fig",
            species = "Grove Gorilla",
            element = Element.Vine,
            art = R.drawable.animal_gorilla,
            avatarModel = EnemyModelTiger,
            maxHp = 112,
            hp = 112,
            maxEnergy = 8,
            energy = 6,
            attack = 11,
            defense = 9,
            speed = 4,
            mood = 71,
            bond = 70,
            moves = listOf(
                Move("Root Wallop", Element.Vine, power = 19, energyCost = 4, cooldown = 1, description = "Slow and heavy."),
                Move("Guard Bark", Element.Echo, power = 6, energyCost = 2, status = StatusEffect.Dazed, description = "Breaks rhythm."),
                Move("Fig Crush", Element.Fruit, power = 15, energyCost = 3, description = "Dense fruit impact.")
            )
        ),
        Fighter(
            name = "Goldcap",
            species = "Fruit Monkey",
            element = Element.Fruit,
            art = R.drawable.animal_monkey,
            avatarModel = EnemyModelMonkey,
            maxHp = 88,
            hp = 88,
            maxEnergy = 10,
            energy = 7,
            attack = 12,
            defense = 6,
            speed = 8,
            mood = 66,
            bond = 60,
            moves = listOf(
                Move("Golden Toss", Element.Fruit, power = 17, energyCost = 4, description = "A clean fruit strike."),
                Move("Vine Hook", Element.Vine, power = 10, energyCost = 3, status = StatusEffect.Tangled, description = "Pulls low."),
                Move("Rally Drum", Element.Echo, power = 9, energyCost = 2, cooldown = 1, description = "Keeps pressure up.")
            )
        )
    )
)

private fun stormCallers() = Trainer(
    name = "Storm Callers",
    color = Plum,
    roster = listOf(
        Fighter(
            name = "Thunderbeak",
            species = "Rain Parrot",
            element = Element.Echo,
            art = R.drawable.animal_parrot,
            avatarModel = EnemyModelParrot,
            maxHp = 78,
            hp = 78,
            maxEnergy = 13,
            energy = 9,
            attack = 11,
            defense = 5,
            speed = 15,
            mood = 68,
            bond = 58,
            moves = listOf(
                Move("Storm Call", Element.Echo, power = 18, energyCost = 4, status = StatusEffect.Dazed, cooldown = 1, description = "A loud opening hit."),
                Move("Rain Fruit", Element.Fruit, power = 13, energyCost = 3, description = "Drops from above."),
                Move("Gust Guard", Element.Echo, power = 7, energyCost = 2, cooldown = 1, description = "Defensive pressure.")
            )
        ),
        Fighter(
            name = "Mossback",
            species = "Storm Gorilla",
            element = Element.Vine,
            art = R.drawable.animal_gorilla,
            avatarModel = EnemyModelTiger,
            maxHp = 104,
            hp = 104,
            maxEnergy = 9,
            energy = 6,
            attack = 13,
            defense = 8,
            speed = 6,
            mood = 60,
            bond = 55,
            moves = listOf(
                Move("Moss Slam", Element.Vine, power = 20, energyCost = 5, cooldown = 1, description = "High-cost finisher."),
                Move("Static Vine", Element.Vine, power = 11, energyCost = 3, status = StatusEffect.Tangled, description = "Locks movement."),
                Move("Fruit Break", Element.Fruit, power = 14, energyCost = 3, description = "A direct hit.")
            )
        )
    )
)

private fun battleItemCatalog() = listOf(
    BattleItem("banana_salve", "Banana Salve", BattleItemKind.HealHp, 20, "Restores 20 HP."),
    BattleItem("jungle_tonic", "Jungle Tonic", BattleItemKind.HealHp, 50, "Restores 50 HP."),
    BattleItem("calm_leaf", "Calm Leaf", BattleItemKind.HealStatus, 0, "Clears status."),
    BattleItem("golden_bunch", "Golden Bunch", BattleItemKind.FullRestore, 0, "Fully restores HP and clears status."),
    BattleItem("war_drum", "War Drum", BattleItemKind.BoostAttack, 3, "Raises attack for this bout.")
)

private fun battleItem(id: String): BattleItem = battleItemCatalog().first { it.id == id }

private fun initialBackpack() = listOf(
    BagStack(battleItem("banana_salve"), 4),
    BagStack(battleItem("jungle_tonic"), 2),
    BagStack(battleItem("calm_leaf"), 2),
    BagStack(battleItem("golden_bunch"), 1),
    BagStack(battleItem("war_drum"), 1)
)

private fun storeOffers() = listOf(
    StoreOffer(battleItem("banana_salve"), 10, "Quick heal"),
    StoreOffer(battleItem("jungle_tonic"), 22, "Large heal"),
    StoreOffer(battleItem("calm_leaf"), 14, "Status cure"),
    StoreOffer(battleItem("golden_bunch"), 42, "Full restore"),
    StoreOffer(battleItem("war_drum"), 30, "Attack boost")
)

@Composable
private fun CanopyApp(vm: GameViewModel = viewModel()) {
    var tab by remember { mutableStateOf(GameTab.Battle) }
    var navVisible by remember { mutableStateOf(true) }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1713), Night)))
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Night
    ) {
        if (!vm.authState.isAuthenticated) {
            AuthScreen(
                authState = vm.authState,
                onLogin = vm::login,
                onSignUp = vm::signUp
            )
            return@Surface
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
            AnimatedContent(targetState = tab, label = "tab") { selected ->
                when (selected) {
                    GameTab.Battle -> BattleScreen(vm.state, vm::useMove, vm::defend, vm::useItem, vm::swap, vm::nextRound, vm::resetMatch)
                    GameTab.Team -> TeamScreen(vm.state)
                    GameTab.Backpack -> BackpackScreen(vm.state, vm::useItem)
                    GameTab.Store -> StoreScreen(vm.state, vm::buyItem)
                    GameTab.Log -> LogScreen(vm.state)
                }
            }
        }
            if (navVisible) {
                CompactGameNav(
                    selected = tab,
                    onSelected = { tab = it },
                    onHide = { navVisible = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                )
            } else {
                HiddenNavHandle(
                    onShow = { navVisible = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    authState: AuthState,
    onLogin: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit
) {
    var mode by remember { mutableStateOf(AuthMode.Login) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf<String?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF183228), Night)))
            .padding(horizontal = 32.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(CanopyGreen.copy(alpha = 0.16f), radius = size.width * 0.28f, center = Offset(size.width * 0.18f, size.height * 0.18f))
            drawCircle(Banana.copy(alpha = 0.12f), radius = size.width * 0.22f, center = Offset(size.width * 0.84f, size.height * 0.72f))
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xEE13201B)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .heightIn(max = 310.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(0.72f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Monkey Mischief", color = Ink, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black)
                    Text(if (mode == AuthMode.Login) "Log in to load your DB save." else "Create a DB account.", color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (authState.isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Banana)
                        Text(localMessage ?: authState.message, color = InkMuted, fontSize = 11.sp, lineHeight = 13.sp)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            localMessage = null
                        },
                        enabled = !authState.isLoading,
                        singleLine = true,
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            localMessage = null
                        },
                        enabled = !authState.isLoading,
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mode == AuthMode.Register) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                localMessage = null
                            },
                            enabled = !authState.isLoading,
                            singleLine = true,
                            label = { Text("Confirm password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (mode == AuthMode.Login) {
                            Button(
                                onClick = { onLogin(username, password) },
                                enabled = !authState.isLoading,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Banana, contentColor = Night),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Log In", fontWeight = FontWeight.Black)
                            }
                            Button(
                                onClick = {
                                    mode = AuthMode.Register
                                    localMessage = "Fill the form to create your account."
                                },
                                enabled = !authState.isLoading,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CanopyGreen, contentColor = Color.White),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Register", fontWeight = FontWeight.Black)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (password != confirmPassword) {
                                        localMessage = "Passwords do not match."
                                    } else {
                                        onSignUp(username, password)
                                    }
                                },
                                enabled = !authState.isLoading,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CanopyGreen, contentColor = Color.White),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Create", fontWeight = FontWeight.Black)
                            }
                            Button(
                                onClick = {
                                    mode = AuthMode.Login
                                    confirmPassword = ""
                                    localMessage = null
                                },
                                enabled = !authState.isLoading,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PanelHigh, contentColor = Ink),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Text("Back", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class AuthMode { Login, Register }

@Composable
private fun CompactGameNav(
    selected: GameTab,
    onSelected: (GameTab) -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xEE070D0B))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GameTab.entries.forEach { item ->
            val active = selected == item
            Button(
                onClick = { onSelected(item) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(8.dp),
                contentPadding = ButtonDefaults.ContentPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) Banana else Color.Transparent,
                    contentColor = if (active) Night else Color(0xFF9EA8A3)
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(item.icon, item.label, modifier = Modifier.size(18.dp))
                    Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        IconButton(
            onClick = onHide,
            modifier = Modifier
                .fillMaxHeight()
                .width(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Panel)
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Hide menu", tint = InkMuted)
        }
    }
}

@Composable
private fun HiddenNavHandle(onShow: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xEE070D0B))
            .padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onShow,
            modifier = Modifier
                .height(22.dp)
                .width(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Panel)
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, "Show menu", tint = Banana, modifier = Modifier.size(18.dp))
        }
    }
}

enum class GameTab(val label: String, val icon: ImageVector) {
    Battle("Battle", Icons.Rounded.CatchingPokemon),
    Team("Team", Icons.Rounded.Groups),
    Backpack("Bag", Icons.Rounded.Backpack),
    Store("Store", Icons.Rounded.Storefront),
    Log("Log", Icons.Rounded.Article)
}

@Composable
private fun BattleScreen(
    state: GameState,
    onMove: (Int) -> Unit,
    onDefend: () -> Unit,
    onUseItem: (String) -> Unit,
    onSwap: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Header(state)
        BattleArena(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.82f)
        )
        ActionPanel(
            state = state,
            onMove = onMove,
            onDefend = onDefend,
            onUseItem = onUseItem,
            onSwap = onSwap,
            onNext = onNext,
            onReset = onReset,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.18f)
        )
    }
}

@Composable
private fun CompactHeader(state: GameState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Canopy Duel", fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("Turn ${state.turn}", color = InkMuted, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Coins", color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(state.coins.toString(), color = Banana, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun Header(state: GameState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Canopy Duel", fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("Turn ${state.turn} / ${state.ai.name}", color = InkMuted, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Coins", color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(state.coins.toString(), color = Banana, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BattleArena(state: GameState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Color(0xFF233E36), Color(0xFF101715))))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawArenaBackdrop(state.phase)
            }
            FighterSlot(
                fighter = state.ai.active,
                trainerColor = state.ai.color,
                alignEnd = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
            FighterSlot(
                fighter = state.player.active,
                trainerColor = state.player.color,
                alignEnd = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun FighterSlot(fighter: Fighter, trainerColor: Color, alignEnd: Boolean, modifier: Modifier = Modifier) {
    val idle = rememberInfiniteTransition(label = "${fighter.name}_idle")
    val bob by idle.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1500 + fighter.speed * 45), RepeatMode.Reverse),
        label = "${fighter.name}_bob"
    )
    val hpProgress by animateFloatAsState(fighter.hp / fighter.maxHp.toFloat(), label = "${fighter.name}_hp")
    val energyProgress by animateFloatAsState(fighter.energy / fighter.maxEnergy.toFloat(), label = "${fighter.name}_energy")
    Row(
        modifier = modifier.fillMaxWidth(0.48f),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!alignEnd) CreatureArt(fighter, bob)
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD111A16)),
            modifier = Modifier.weight(1f)
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(fighter.name, fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(fighter.element.label, color = fighter.element.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Meter("HP", fighter.hp, fighter.maxHp, hpProgress, Threat)
                Meter("EN", fighter.energy, fighter.maxEnergy, energyProgress, RiverBlue)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(fighter.status.label, if (fighter.status == StatusEffect.None) CanopyGreen else Banana)
                    Chip("Guard ${fighter.guard}", trainerColor)
                }
            }
        }
        if (alignEnd) CreatureArt(fighter, bob)
    }
}

@Composable
private fun CreatureArt(fighter: Fighter, bob: Float) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(fighter.element.color.copy(alpha = 0.18f), radius = size.minDimension * 0.44f)
        }
        if (fighter.avatarModel != null) {
            EnemyModelAvatar(
                modelPath = fighter.avatarModel,
                fighter = fighter,
                bob = bob
            )
        } else {
            Image(
                painter = painterResource(fighter.art),
                contentDescription = fighter.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        translationY = bob
                        rotationZ = bob * 0.35f
                        alpha = if (fighter.isDown) 0.35f else 1f
                    }
            )
        }
    }
}

@Composable
private fun EnemyModelAvatar(modelPath: String, fighter: Fighter, bob: Float) {
    SceneView(
        modifier = Modifier
            .size(58.dp)
            .graphicsLayer {
                translationY = bob
                alpha = if (fighter.isDown) 0.35f else 1f
            }
    ) {
        rememberModelInstance(modelLoader, modelPath)?.let { modelInstance ->
            ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 0.85f,
                autoAnimate = true
            )
        }
    }
}

@Composable
private fun Meter(label: String, value: Int, max: Int, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("$value/$max", color = InkMuted, fontSize = 10.sp)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = color,
            trackColor = Color(0xFF314039),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionPanel(
    state: GameState,
    onMove: (Int) -> Unit,
    onDefend: () -> Unit,
    onUseItem: (String) -> Unit,
    onSwap: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(phaseText(state), color = Ink, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
                Text(
                    "${state.lastPlayerAction}\n${state.lastAiAction}",
                    color = InkMuted,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    maxLines = 4
                )
            }
            if (state.phase == RoundPhase.PlayerChoice) {
                Column(
                    modifier = Modifier
                        .weight(1.45f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        state.player.active.moves.take(2).forEachIndexed { index, move ->
                            MoveButton(move, state.player.active, onClick = { onMove(index) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        val thirdMove = state.player.active.moves.getOrNull(2)
                        if (thirdMove != null) {
                            MoveButton(thirdMove, state.player.active, onClick = { onMove(2) }, modifier = Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        BattleButton("Defend", "Guard + EN", onDefend, enabled = true, modifier = Modifier.weight(1f), color = Plum)
                        BattleButton("Item", firstItemLabel(state), { state.firstUsableStack()?.let { onUseItem(it.item.id) } }, enabled = state.firstUsableStack() != null, modifier = Modifier.weight(1f), color = CanopyGreen)
                        BattleButton("Swap", "Partner", onSwap, enabled = state.player.roster.any { it != state.player.active && !it.isDown }, modifier = Modifier.weight(1f), color = RiverBlue)
                    }
                }
            } else {
                Button(
                    onClick = if (state.phase == RoundPhase.MatchOver) onReset else onNext,
                    modifier = Modifier
                        .weight(1.45f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Banana, contentColor = Night)
                ) {
                    Text(matchActionText(state), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun matchActionText(state: GameState): String = when {
    state.phase != RoundPhase.MatchOver -> "Next Turn"
    state.player.hasUsableFighter -> "Next Rival"
    else -> "Rematch"
}

private fun firstItemLabel(state: GameState): String {
    val stack = state.firstUsableStack() ?: return "No effect"
    return "${stack.item.name} x${stack.count}"
}

private fun GameState.firstUsableStack(): BagStack? = backpack.firstOrNull { it.count > 0 && it.item.canApplyTo(player.active) }

@Composable
private fun MoveButton(move: Move, fighter: Fighter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cooldown = fighter.cooldowns[move.name] ?: 0
    val enabled = fighter.energy >= move.energyCost && cooldown == 0
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = PanelHigh, contentColor = Ink)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Text(move.name, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(move.element.label, color = move.element.color, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
            Text(if (cooldown > 0) "CD $cooldown" else "${move.energyCost} EN", fontSize = 9.sp, lineHeight = 10.sp, color = InkMuted)
        }
    }
}

@Composable
private fun BattleButton(title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier, color: Color) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(subtitle, fontSize = 8.sp, lineHeight = 9.sp, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

private fun phaseText(state: GameState): String = when (state.phase) {
    RoundPhase.PlayerChoice -> "Choose a command"
    RoundPhase.RoundOver -> "Turn ${state.turn} resolved"
    RoundPhase.MatchOver -> if (state.player.hasUsableFighter) "Victory" else "Defeat"
}

@Composable
private fun TeamScreen(state: GameState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header(state) }
        item { TrainerRoster("Your Team", state.player) }
        item { TrainerRoster("Bad Monkey Crew", state.ai) }
    }
}

@Composable
private fun TrainerRoster(title: String, trainer: Trainer) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Black)
            trainer.roster.forEachIndexed { index, fighter ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(fighter.art), fighter.name, modifier = Modifier.size(44.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${fighter.name} / ${fighter.species}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${fighter.element.label} - HP ${fighter.hp}/${fighter.maxHp} - EN ${fighter.energy}/${fighter.maxEnergy}", color = InkMuted, fontSize = 11.sp)
                    }
                    if (index == trainer.activeIndex) Chip("Active", trainer.color)
                }
            }
        }
    }
}

@Composable
private fun BackpackScreen(state: GameState, onUseItem: (String) -> Unit) {
    val totalItems = state.backpack.sumOf { it.count }
    val usableItems = state.backpack.count { it.count > 0 && it.item.canApplyTo(state.player.active) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header(state) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(state.player.active.art), state.player.active.name, modifier = Modifier.size(74.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Backpack", fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("$totalItems items carried. $usableItems can help ${state.player.active.name} right now.", color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
                        Text("Using an item spends your turn.", color = InkMuted, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
        items(state.backpack) { stack ->
            BackpackItemCard(
                stack = stack,
                enabled = state.phase == RoundPhase.PlayerChoice && stack.count > 0 && stack.item.canApplyTo(state.player.active),
                onUseItem = onUseItem
            )
        }
    }
}

@Composable
private fun BackpackItemCard(stack: BagStack, enabled: Boolean, onUseItem: (String) -> Unit) {
    val accent = when (stack.item.kind) {
        BattleItemKind.HealHp -> Banana
        BattleItemKind.HealStatus -> RiverBlue
        BattleItemKind.FullRestore -> CanopyGreen
        BattleItemKind.BoostAttack -> Threat
    }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("x${stack.count}", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stack.item.name, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(stack.item.description, color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Button(
                onClick = { onUseItem(stack.item.id) },
                enabled = enabled,
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Night)
            ) {
                Text("Use", fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StoreScreen(state: GameState, onBuyItem: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header(state) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Banana.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(state.coins.toString(), color = Banana, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Canopy Store", fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text("Earn $CoinsPerNpcDefeated coins for each NPC defeated, then restock battle items here.", color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
        items(storeOffers()) { offer ->
            StoreOfferCard(
                offer = offer,
                owned = state.backpack.firstOrNull { it.item.id == offer.item.id }?.count ?: 0,
                coins = state.coins,
                onBuyItem = onBuyItem
            )
        }
    }
}

@Composable
private fun StoreOfferCard(offer: StoreOffer, owned: Int, coins: Int, onBuyItem: (String) -> Unit) {
    val accent = when (offer.item.kind) {
        BattleItemKind.HealHp -> Banana
        BattleItemKind.HealStatus -> RiverBlue
        BattleItemKind.FullRestore -> CanopyGreen
        BattleItemKind.BoostAttack -> Threat
    }
    val canAfford = coins >= offer.price
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${offer.price}", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(offer.item.name, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(offer.item.description, color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
                Text("${offer.limitHint} - Owned x$owned", color = InkMuted, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Button(
                onClick = { onBuyItem(offer.item.id) },
                enabled = canAfford,
                modifier = Modifier.height(46.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Night)
            ) {
                Text("Buy", fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun LogScreen(state: GameState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header(state) }
        items(state.log) { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
                Text(entry, modifier = Modifier.padding(14.dp), color = Ink, fontSize = 14.sp, lineHeight = 19.sp)
            }
        }
        item {
            Text("Offline two-player-style battle: human player versus local AI.", color = InkMuted, textAlign = TextAlign.Center, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(10.dp))
        }
    }
}

private fun DrawScope.drawArenaBackdrop(phase: RoundPhase) {
    drawRect(Color(0xFF183228))
    drawCircle(CanopyGreen.copy(alpha = 0.18f), radius = size.width * 0.42f, center = Offset(size.width * 0.18f, size.height * 0.12f))
    drawCircle(RiverBlue.copy(alpha = 0.13f), radius = size.width * 0.32f, center = Offset(size.width * 0.88f, size.height * 0.3f))
    repeat(10) { index ->
        val x = size.width * (index / 9f)
        val y = size.height * 0.43f + if (index % 2 == 0) 8f else -8f
        val leaf = Path().apply {
            moveTo(x, y - 7f)
            cubicTo(x + 11f, y - 5f, x + 11f, y + 10f, x, y + 13f)
            cubicTo(x - 11f, y + 10f, x - 11f, y - 5f, x, y - 7f)
        }
        drawPath(leaf, Color(0x555EBE82))
    }
    drawOval(Brush.radialGradient(listOf(Color(0xFF314D3B), Color(0xFF172019))), topLeft = Offset(size.width * 0.05f, size.height * 0.62f), size = androidx.compose.ui.geometry.Size(size.width * 0.9f, size.height * 0.32f))
    if (phase == RoundPhase.MatchOver) drawRect(Color(0x66000000))
}
