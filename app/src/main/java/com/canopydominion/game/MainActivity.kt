package com.canopydominion.game

import android.os.Bundle
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.CatchingPokemon
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val bananas: Int = 8,
    val phase: RoundPhase = RoundPhase.PlayerChoice,
    val lastPlayerAction: String = "Choose a battle command.",
    val lastAiAction: String = "Rival trainer is watching your troop.",
    val log: List<String> = listOf("A rival troop challenges the Elder Fig.")
)

class GameViewModel : ViewModel() {
    var state by mutableStateOf(GameState())
        private set

    fun useMove(index: Int) {
        if (state.phase != RoundPhase.PlayerChoice) return
        val fighter = state.player.active
        val move = fighter.moves.getOrNull(index) ?: return
        val cooldown = fighter.cooldowns[move.name] ?: 0
        if (fighter.energy < move.energyCost || cooldown > 0 || fighter.isDown) {
            addLog("${fighter.name} cannot use ${move.name} right now.")
            return
        }
        resolveRound(PlayerCommand.Move(index))
    }

    fun defend() {
        if (state.phase == RoundPhase.PlayerChoice) resolveRound(PlayerCommand.Defend)
    }

    fun care() {
        if (state.phase != RoundPhase.PlayerChoice) return
        if (state.bananas <= 0) {
            addLog("No bananas left for a care break.")
            return
        }
        resolveRound(PlayerCommand.Care)
    }

    fun swap() {
        if (state.phase != RoundPhase.PlayerChoice) return
        val next = state.player.roster.indexOfFirstIndexed { index, fighter ->
            index != state.player.activeIndex && !fighter.isDown
        }
        if (next == -1) {
            addLog("No rested partner can swap in.")
            return
        }
        resolveRound(PlayerCommand.Swap(next))
    }

    fun nextRound() {
        if (state.phase == RoundPhase.RoundOver) {
            state = state.copy(phase = RoundPhase.PlayerChoice, turn = state.turn + 1)
        }
    }

    fun resetMatch() {
        state = GameState()
    }

    private fun resolveRound(command: PlayerCommand) {
        val aiCommand = chooseAiCommand(state)
        val playerFirst = state.player.active.speed >= state.ai.active.speed
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
        state = working.copy(
            phase = phase,
            lastPlayerAction = events.firstOrNull { it.startsWith("You") } ?: "You hold position.",
            lastAiAction = events.firstOrNull { it.startsWith("Rival") } ?: "Rival waits.",
            log = (events + resultLine + working.log).take(12)
        )
    }

    private fun addLog(message: String) {
        state = state.copy(log = (listOf(message) + state.log).take(12))
    }
}

private enum class Actor { Player, Ai }

private sealed class PlayerCommand {
    data class Move(val index: Int) : PlayerCommand()
    data object Defend : PlayerCommand()
    data object Care : PlayerCommand()
    data class Swap(val index: Int) : PlayerCommand()
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
        PlayerCommand.Care -> applyCare(state, events)
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

private fun applyCare(state: GameState, events: MutableList<String>): GameState {
    val fighter = state.player.active
    val healed = fighter.copy(
        hp = (fighter.hp + 12).coerceAtMost(fighter.maxHp),
        energy = (fighter.energy + 2).coerceAtMost(fighter.maxEnergy),
        mood = (fighter.mood + 8).coerceAtMost(100),
        bond = (fighter.bond + 6).coerceAtMost(100),
        status = if (fighter.status == StatusEffect.Dazed || fighter.status == StatusEffect.Tangled) StatusEffect.None else fighter.status
    )
    events += "You give ${fighter.name} a banana break. HP, energy, and mood recover."
    return state.copy(bananas = (state.bananas - 1).coerceAtLeast(0), player = state.player.replaceActive(healed))
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
        ai = promote(state.ai, "Rival")
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

private fun Actor.label(): String = if (this == Actor.Player) "You" else "Rival"

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
    name = "Rival",
    color = Threat,
    roster = listOf(
        Fighter(
            name = "Brakka",
            species = "Redback Gorilla",
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
            name = "Koro",
            species = "Scout Monkey",
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

@Composable
private fun CanopyApp(vm: GameViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(GameTab.Battle) }
    Scaffold(
        containerColor = Night,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0A0F0D), modifier = Modifier.navigationBarsPadding()) {
                GameTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, item.label, modifier = Modifier.size(22.dp)) },
                        label = { Text(item.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Night,
                            selectedTextColor = CanopyGreen,
                            indicatorColor = Banana,
                            unselectedIconColor = Color(0xFF9EA8A3),
                            unselectedTextColor = Color(0xFF9EA8A3)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0D1713), Night)))
                .statusBarsPadding()
                .padding(padding),
            color = Night
        ) {
            AnimatedContent(targetState = tab, label = "tab") { selected ->
                when (selected) {
                    GameTab.Battle -> BattleScreen(vm.state, vm::useMove, vm::defend, vm::care, vm::swap, vm::nextRound, vm::resetMatch)
                    GameTab.Team -> TeamScreen(vm.state)
                    GameTab.Care -> CareScreen(vm.state, vm::care)
                    GameTab.Log -> LogScreen(vm.state)
                }
            }
        }
    }
}

enum class GameTab(val label: String, val icon: ImageVector) {
    Battle("Battle", Icons.Rounded.CatchingPokemon),
    Team("Team", Icons.Rounded.Groups),
    Care("Care", Icons.Rounded.Favorite),
    Log("Log", Icons.Rounded.Article)
}

@Composable
private fun BattleScreen(
    state: GameState,
    onMove: (Int) -> Unit,
    onDefend: () -> Unit,
    onCare: () -> Unit,
    onSwap: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header(state) }
        item {
            BattleArena(state)
        }
        item {
            ActionPanel(state, onMove, onDefend, onCare, onSwap, onNext, onReset)
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
            Text("Canopy Duel", fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("Turn ${state.turn} / offline AI battle", color = InkMuted, fontSize = 15.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Bananas", color = InkMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(state.bananas.toString(), color = Banana, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BattleArena(state: GameState) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp)
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
        modifier = modifier.fillMaxWidth(0.86f),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!alignEnd) CreatureArt(fighter, bob)
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD111A16)),
            modifier = Modifier.weight(1f)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(fighter.name, fontSize = 16.sp, fontWeight = FontWeight.Black)
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
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(fighter.element.color.copy(alpha = 0.18f), radius = size.minDimension * 0.44f)
        }
        Image(
            painter = painterResource(fighter.art),
            contentDescription = fighter.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(78.dp)
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
    onCare: () -> Unit,
    onSwap: () -> Unit,
    onNext: () -> Unit,
    onReset: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(phaseText(state), color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("${state.lastPlayerAction}\n${state.lastAiAction}", color = InkMuted, fontSize = 12.sp, lineHeight = 16.sp)
            if (state.phase == RoundPhase.PlayerChoice) {
                state.player.active.moves.forEachIndexed { index, move ->
                    MoveButton(move, state.player.active, onClick = { onMove(index) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BattleButton("Defend", "Guard + energy", onDefend, enabled = true, modifier = Modifier.weight(1f), color = Plum)
                    BattleButton("Care", "1 banana", onCare, enabled = state.bananas > 0, modifier = Modifier.weight(1f), color = CanopyGreen)
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

@Composable
private fun MoveButton(move: Move, fighter: Fighter, onClick: () -> Unit) {
    val cooldown = fighter.cooldowns[move.name] ?: 0
    val enabled = fighter.energy >= move.energyCost && cooldown == 0
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PanelHigh, contentColor = Ink)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(move.name, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Text(move.description, fontSize = 10.sp, color = InkMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(move.element.label, color = move.element.color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (cooldown > 0) "CD $cooldown" else "${move.energyCost} EN", fontSize = 10.sp, color = InkMuted)
            }
        }
    }
}

@Composable
private fun BattleButton(title: String, subtitle: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier, color: Color) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 9.sp)
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
        item { TrainerRoster("AI Rival", state.ai) }
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
private fun CareScreen(state: GameState, onCare: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header(state) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(state.player.active.art), state.player.active.name, modifier = Modifier.size(118.dp))
                    Text("${state.player.active.name} needs care between attacks.", fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Text("Care is a real battle action: it heals, restores energy, clears common status, and spends one banana.", color = InkMuted, fontSize = 13.sp, lineHeight = 18.sp, textAlign = TextAlign.Center)
                    Button(
                        onClick = onCare,
                        enabled = state.phase == RoundPhase.PlayerChoice && state.bananas > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Banana, contentColor = Night)
                    ) {
                        Text("Banana Care", fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
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
