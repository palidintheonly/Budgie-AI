package com.monkeymischief.game

import android.app.Application
import android.os.Bundle
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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

data class Fighter(
    val name: String,
    val species: String,
    val element: Element,
    @DrawableRes val art: Int,
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
    val player: Trainer = initialPlayer(),
    val ai: Trainer = initialAi(),
    val backpack: List<BagStack> = initialBackpack(),
    val phase: RoundPhase = RoundPhase.PlayerChoice,
    val lastPlayerAction: String = "Choose a battle command.",
    val lastAiAction: String = "The Naughty Knuckles are watching your troop.",
    val log: List<String> = listOf("The Naughty Knuckles challenge the Elder Fig.")
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(GameState())
        private set
    private val deviceId = resolveDeviceId(application)
    private val backendSlot = "device-$deviceId"
    private var userId: String? = null

    init {
        logVerbose("viewmodel_init turn=${state.turn} phase=${state.phase} deviceId=$deviceId slot=$backendSlot")
        registerDeviceThenSync("app_start")
    }

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
        logVerbose("useItem_selected name=${stack.item.name} count=${stack.count} kind=${stack.item.kind} amount=${stack.item.amount}")
        resolveRound(PlayerCommand.UseItem(itemId))
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
        state = GameState()
        logVerbose("state=resetMatch turn=${state.turn} phase=${state.phase}")
        syncState("reset_match")
    }

    private fun resolveRound(command: PlayerCommand) {
        val aiCommand = chooseAiCommand(state)
        val playerFirst = state.player.active.speed >= state.ai.active.speed
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

    private fun registerDeviceThenSync(reason: String) {
        logVerbose("register_start deviceId=$deviceId url=$BackendBaseUrl/v1/users/register")
        thread(name = "monkey-user-register", isDaemon = true) {
            runCatching {
                val payload = JSONObject()
                    .put("deviceId", deviceId)
                    .put("client", "android")
                    .put("version", "0.0.5-alpha")
                    .toString()
                val connection = (URL("$BackendBaseUrl/v1/users/register").openConnection() as HttpURLConnection).apply {
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
                    userId = JSONObject(response).optString("userId").takeIf { it.isNotBlank() }
                    logVerbose("register_done status=$status userId=${userId ?: "missing"} response=$response")
                } else {
                    logVerbose("register_failed_status status=$status response=$response")
                }
                connection.disconnect()
            }.onFailure { error ->
                Log.e(GameLogTag, "register_failed deviceId=$deviceId error=${error.message}", error)
            }
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

private fun Actor.label(): String = if (this == Actor.Player) "You" else "Naughty Knuckles"

private fun GameState.toBackendJson(reason: String, deviceId: String, userId: String?): JSONObject = JSONObject()
    .put("reason", reason)
    .put("deviceId", deviceId)
    .put("userId", userId ?: JSONObject.NULL)
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

private fun initialAi() = Trainer(
    name = "Naughty Knuckles",
    color = Threat,
    roster = listOf(
        Fighter(
            name = "Bananabandit",
            species = "Bad-Apple Gorilla",
            element = Element.Vine,
            art = R.drawable.animal_gorilla,
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

private fun initialBackpack() = listOf(
    BagStack(BattleItem("banana_salve", "Banana Salve", BattleItemKind.HealHp, 20, "Restores 20 HP."), 4),
    BagStack(BattleItem("jungle_tonic", "Jungle Tonic", BattleItemKind.HealHp, 50, "Restores 50 HP."), 2),
    BagStack(BattleItem("calm_leaf", "Calm Leaf", BattleItemKind.HealStatus, 0, "Clears status."), 2),
    BagStack(BattleItem("golden_bunch", "Golden Bunch", BattleItemKind.FullRestore, 0, "Fully restores HP and clears status."), 1),
    BagStack(BattleItem("war_drum", "War Drum", BattleItemKind.BoostAttack, 3, "Raises attack for this bout."), 1)
)

@Composable
private fun CanopyApp(vm: GameViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(GameTab.Battle) }
    var navVisible by rememberSaveable { mutableStateOf(true) }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1713), Night)))
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = Night
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
            AnimatedContent(targetState = tab, label = "tab") { selected ->
                when (selected) {
                    GameTab.Battle -> BattleScreen(vm.state, vm::useMove, vm::defend, vm::useItem, vm::swap, vm::nextRound, vm::resetMatch)
                    GameTab.Team -> TeamScreen(vm.state)
                    GameTab.Backpack -> BackpackScreen(vm.state, vm::useItem)
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Header(state)
        BattleArena(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.92f)
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
                .weight(1.08f)
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
            Text("Items", color = InkMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(state.backpack.sumOf { it.count }.toString(), color = Banana, fontSize = 18.sp, fontWeight = FontWeight.Black)
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
            Text("Canopy Duel", fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("Turn ${state.turn} / offline AI battle", color = InkMuted, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Items", color = InkMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(state.backpack.sumOf { it.count }.toString(), color = Banana, fontSize = 21.sp, fontWeight = FontWeight.Black)
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
                    .padding(14.dp)
            )
            FighterSlot(
                fighter = state.player.active,
                trainerColor = state.player.color,
                alignEnd = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
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
        modifier = modifier.fillMaxWidth(0.58f),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!alignEnd) CreatureArt(fighter, bob)
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD111A16)),
            modifier = Modifier.weight(1f)
        ) {
            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(fighter.name, fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
                    Text(fighter.element.label, color = fighter.element.color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(82.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(fighter.element.color.copy(alpha = 0.18f), radius = size.minDimension * 0.44f)
        }
        Image(
            painter = painterResource(fighter.art),
            contentDescription = fighter.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(66.dp)
                .graphicsLayer {
                    translationY = bob
                    rotationZ = bob * 0.35f
                    alpha = if (fighter.isDown) 0.35f else 1f
                }
        )
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
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(phaseText(state), color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text("${state.lastPlayerAction}\n${state.lastAiAction}", color = InkMuted, fontSize = 11.sp, lineHeight = 14.sp)
            if (state.phase == RoundPhase.PlayerChoice) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Banana)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("ATTACK", color = Night, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                    Text("Choose one move", color = InkMuted, fontSize = 12.sp)
                }
                state.player.active.moves.forEachIndexed { index, move ->
                    MoveButton(move, state.player.active, onClick = { onMove(index) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BattleButton("Defend", "Guard + energy", onDefend, enabled = true, modifier = Modifier.weight(1f), color = Plum)
                    BattleButton("Use Item", firstItemLabel(state), { state.backpack.firstOrNull { it.count > 0 }?.let { onUseItem(it.item.id) } }, enabled = state.backpack.any { it.count > 0 }, modifier = Modifier.weight(1f), color = CanopyGreen)
                    BattleButton("Swap", "Partner", onSwap, enabled = state.player.roster.any { it != state.player.active && !it.isDown }, modifier = Modifier.weight(1f), color = RiverBlue)
                }
            } else {
                Button(
                    onClick = if (state.phase == RoundPhase.MatchOver) onReset else onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Banana, contentColor = Night)
                ) {
                    Text(if (state.phase == RoundPhase.MatchOver) "Rematch" else "Next Turn", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun firstItemLabel(state: GameState): String {
    val stack = state.backpack.firstOrNull { it.count > 0 } ?: return "Empty"
    return "${stack.item.name} x${stack.count}"
}

@Composable
private fun MoveButton(move: Move, fighter: Fighter, onClick: () -> Unit) {
    val cooldown = fighter.cooldowns[move.name] ?: 0
    val enabled = fighter.energy >= move.energyCost && cooldown == 0
    Button(
        onClick = onClick,
        enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = PanelHigh, contentColor = Ink)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(move.element.color.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text("ATK", color = move.element.color, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(move.name, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black)
                Text(move.description, fontSize = 10.sp, lineHeight = 12.sp, color = InkMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(move.element.label, color = move.element.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (cooldown > 0) "CD $cooldown" else "${move.energyCost} EN", fontSize = 10.sp, lineHeight = 12.sp, color = InkMuted)
            }
        }
    }
}

@Composable
private fun BattleButton(title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier, color: Color) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 9.sp, lineHeight = 11.sp)
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
                        Text("Use one item on ${state.player.active.name}. Items spend your turn, so the rival still acts.", color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
        items(state.backpack) { stack ->
            BackpackItemCard(
                stack = stack,
                enabled = state.phase == RoundPhase.PlayerChoice && stack.count > 0,
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
