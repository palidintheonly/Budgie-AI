package com.canopydominion.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CanopyTheme {
                CanopyApp()
            }
        }
    }
}

private val CanopyGreen = Color(0xFF2F7D58)
private val Moss = Color(0xFF6E8E4E)
private val Banana = Color(0xFFF4C542)
private val Bark = Color(0xFF81522D)
private val Soil = Color(0xFF4A3524)
private val Night = Color(0xFF101914)
private val Panel = Color(0xFF18251E)
private val River = Color(0xFF4AA3B5)
private val Threat = Color(0xFFCF6B5A)

@Composable
private fun CanopyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Banana,
            secondary = CanopyGreen,
            tertiary = River,
            background = Night,
            surface = Panel,
            onPrimary = Night,
            onSecondary = Color.White,
            onSurface = Color(0xFFEAF3DF),
            onBackground = Color(0xFFEAF3DF)
        ),
        content = content
    )
}

enum class Terrain(val label: String, val color: Color, val food: Int, val wood: Int, val lore: Int) {
    Grove("Grove", Color(0xFF2F7D58), 3, 1, 0),
    Jungle("Jungle", Color(0xFF276043), 2, 3, 1),
    River("River", River, 2, 0, 2),
    Hill("Hill", Color(0xFF8C7650), 0, 2, 2),
    Ruin("Ruin", Color(0xFF8B6A9B), 0, 1, 4),
    Thorn("Thorn", Color(0xFF5F3C36), 1, 1, 0)
}

enum class Tribe { Player, Scarlet, Ivory, None }

data class HexTile(
    val id: Int,
    val q: Int,
    val r: Int,
    val terrain: Terrain,
    val owner: Tribe = Tribe.None,
    val explored: Boolean = false,
    val settlement: String? = null
)

data class Rival(
    val tribe: Tribe,
    val name: String,
    val color: Color,
    val hunger: Float,
    val curiosity: Float,
    val temper: Float,
    val power: Int,
    val mood: Int,
    val plan: String
)

data class GameState(
    val turn: Int = 1,
    val bananas: Int = 18,
    val timber: Int = 12,
    val lore: Int = 4,
    val troop: Int = 5,
    val joy: Int = 70,
    val hunger: Int = 38,
    val bond: Int = 62,
    val research: Int = 0,
    val selectedTile: Int = 18,
    val tiles: List<HexTile> = initialMap(),
    val rivals: List<Rival> = initialRivals(),
    val log: List<String> = listOf("Your monkey troop wakes under the Elder Fig.")
)

class GameViewModel : ViewModel() {
    var state by mutableStateOf(GameState())
        private set

    fun selectTile(id: Int) {
        state = state.copy(selectedTile = id)
    }

    fun feed() {
        if (state.bananas < 3) {
            addLog("Not enough bananas for a feast.")
            return
        }
        state = state.copy(
            bananas = state.bananas - 3,
            hunger = (state.hunger - 24).coerceAtLeast(0),
            joy = (state.joy + 7).coerceAtMost(100),
            bond = (state.bond + 5).coerceAtMost(100)
        )
        addLog("You serve sweet bananas. The troop settles down.")
    }

    fun play() {
        if (state.timber < 2) {
            addLog("You need timber to build climbing toys.")
            return
        }
        state = state.copy(
            timber = state.timber - 2,
            joy = (state.joy + 18).coerceAtMost(100),
            bond = (state.bond + 3).coerceAtMost(100),
            hunger = (state.hunger + 5).coerceAtMost(100)
        )
        addLog("The troop races through new vine swings.")
    }

    fun groom() {
        state = state.copy(
            bond = (state.bond + 13).coerceAtMost(100),
            joy = (state.joy + 5).coerceAtMost(100),
            hunger = (state.hunger + 3).coerceAtMost(100)
        )
        addLog("Grooming calms the alpha and keeps the troop loyal.")
    }

    fun scout() {
        val selected = state.tiles.first { it.id == state.selectedTile }
        val neighbors = state.tiles.filter { tile ->
            axialDistance(selected.q, selected.r, tile.q, tile.r) == 1 && !tile.explored
        }
        if (state.bananas < 2 || neighbors.isEmpty()) {
            addLog("No nearby hidden hex can be scouted right now.")
            return
        }
        val target = neighbors.maxBy { it.terrain.lore + it.terrain.food }
        state = state.copy(
            bananas = state.bananas - 2,
            tiles = state.tiles.map { if (it.id == target.id) it.copy(explored = true) else it },
            lore = state.lore + target.terrain.lore,
            selectedTile = target.id
        )
        addLog("Scouts reveal a ${target.terrain.label.lowercase()} hex.")
    }

    fun settle() {
        val tile = state.tiles.first { it.id == state.selectedTile }
        if (!tile.explored || tile.owner != Tribe.None || state.bananas < 5 || state.timber < 5) {
            addLog("Settling needs an explored free hex, 5 bananas, and 5 timber.")
            return
        }
        state = state.copy(
            bananas = state.bananas - 5,
            timber = state.timber - 5,
            tiles = state.tiles.map {
                if (it.id == tile.id) it.copy(owner = Tribe.Player, settlement = "Nest ${ownedTiles(Tribe.Player) + 1}") else it
            },
            bond = (state.bond + 4).coerceAtMost(100)
        )
        addLog("A new canopy nest claims the ${tile.terrain.label.lowercase()}.")
    }

    fun research() {
        if (state.lore < 6) {
            addLog("You need 6 lore to teach a new trick.")
            return
        }
        val next = state.research + 1
        state = state.copy(
            lore = state.lore - 6,
            research = next,
            joy = (state.joy + 4).coerceAtMost(100)
        )
        addLog(when (next) {
            1 -> "Research unlocked: Shell Counting improves resource planning."
            2 -> "Research unlocked: Vine Bridges make scouts bolder."
            3 -> "Research unlocked: Drum Signals strengthen defense."
            else -> "The troop refines old wisdom into sharper routines."
        })
    }

    fun endTurn() {
        val owned = state.tiles.filter { it.owner == Tribe.Player }
        val foodGain = owned.sumOf { it.terrain.food } + state.research
        val woodGain = owned.sumOf { it.terrain.wood }
        val loreGain = owned.sumOf { it.terrain.lore }
        val neglect = when {
            state.hunger > 82 -> 13
            state.joy < 28 -> 8
            else -> 0
        }
        val npcResult = runNpcTurn(state)
        val event = randomEvent(state.turn, state.joy, state.hunger)
        state = state.copy(
            turn = state.turn + 1,
            bananas = (state.bananas + foodGain + event.bananas).coerceAtLeast(0),
            timber = (state.timber + woodGain + event.timber).coerceAtLeast(0),
            lore = (state.lore + loreGain + event.lore).coerceAtLeast(0),
            troop = (state.troop + if (state.bond > 80 && state.hunger < 40) 1 else 0).coerceAtMost(30),
            hunger = (state.hunger + 13 - foodGain / 2).coerceIn(0, 100),
            joy = (state.joy - 7 - neglect + event.joy).coerceIn(0, 100),
            bond = (state.bond - if (neglect > 0) 5 else 1).coerceIn(0, 100),
            rivals = npcResult.rivals,
            tiles = npcResult.tiles,
            log = (listOf(event.message) + npcResult.messages + state.log).take(9)
        )
    }

    private fun ownedTiles(tribe: Tribe): Int = state.tiles.count { it.owner == tribe }

    private fun addLog(message: String) {
        state = state.copy(log = (listOf(message) + state.log).take(9))
    }
}

data class EventResult(
    val bananas: Int = 0,
    val timber: Int = 0,
    val lore: Int = 0,
    val joy: Int = 0,
    val message: String
)

private fun randomEvent(turn: Int, joy: Int, hunger: Int): EventResult {
    val roll = Random(turn * 31 + joy * 7 + hunger).nextInt(5)
    return when (roll) {
        0 -> EventResult(bananas = 4, joy = 2, message = "A hidden banana bloom ripens near the nests.")
        1 -> EventResult(timber = 3, message = "Old branches fall cleanly after the night rain.")
        2 -> EventResult(lore = 2, message = "Young scouts bring back strange shell markings.")
        3 -> EventResult(joy = -5, message = "A thorn storm rattles the canopy and lowers morale.")
        else -> EventResult(message = "The island stays quiet while plans mature.")
    }
}

data class NpcTurn(val rivals: List<Rival>, val tiles: List<HexTile>, val messages: List<String>)

private fun runNpcTurn(state: GameState): NpcTurn {
    var tiles = state.tiles
    val messages = mutableListOf<String>()
    val rivals = state.rivals.map { rival ->
        val owned = tiles.filter { it.owner == rival.tribe }
        val frontier = owned.flatMap { origin ->
            tiles.filter { axialDistance(origin.q, origin.r, it.q, it.r) == 1 && it.owner == Tribe.None }
        }.distinctBy { it.id }
        val target = frontier.maxByOrNull { tile ->
            tile.terrain.food * rival.hunger + tile.terrain.lore * rival.curiosity + Random(state.turn + tile.id).nextFloat()
        }
        val shouldExpand = target != null && Random(state.turn * rival.name.length).nextFloat() < 0.62f
        if (shouldExpand && target != null) {
            tiles = tiles.map { if (it.id == target.id) it.copy(owner = rival.tribe, explored = true) else it }
            messages += "${rival.name} claims ${target.terrain.label.lowercase()} land."
            rival.copy(
                power = rival.power + target.terrain.food + target.terrain.wood + 1,
                mood = (rival.mood + 2).coerceAtMost(100),
                plan = "Expanding toward ${target.terrain.label.lowercase()}"
            )
        } else {
            val plan = if (rival.temper > rival.curiosity) "Training raiders" else "Studying old ruins"
            rival.copy(power = rival.power + 1, mood = (rival.mood - 1).coerceAtLeast(0), plan = plan)
        }
    }
    return NpcTurn(rivals, tiles, messages.ifEmpty { listOf("Rival tribes hold position and watch your canopy.") })
}

private fun initialRivals() = listOf(
    Rival(Tribe.Scarlet, "Scarlet Tails", Color(0xFFD65545), hunger = 0.8f, curiosity = 0.25f, temper = 0.75f, power = 6, mood = 52, plan = "Testing borders"),
    Rival(Tribe.Ivory, "Ivory Hands", Color(0xFFE7D9B5), hunger = 0.4f, curiosity = 0.9f, temper = 0.35f, power = 5, mood = 65, plan = "Seeking ruins")
)

private fun initialMap(): List<HexTile> {
    val terrains = listOf(
        Terrain.Thorn, Terrain.Jungle, Terrain.Grove, Terrain.Hill, Terrain.River,
        Terrain.Jungle, Terrain.Ruin, Terrain.Grove, Terrain.Jungle, Terrain.Hill,
        Terrain.Grove, Terrain.River, Terrain.Grove, Terrain.Jungle, Terrain.Ruin,
        Terrain.Hill, Terrain.Jungle, Terrain.Grove, Terrain.Grove, Terrain.River,
        Terrain.Thorn, Terrain.Grove, Terrain.Hill, Terrain.Jungle, Terrain.Ruin,
        Terrain.River, Terrain.Jungle, Terrain.Grove, Terrain.Hill, Terrain.Thorn,
        Terrain.Jungle, Terrain.Grove, Terrain.River, Terrain.Jungle, Terrain.Hill,
        Terrain.Ruin, Terrain.Thorn, Terrain.Grove, Terrain.Jungle
    )
    val coords = buildList {
        val radius = 3
        for (q in -radius..radius) {
            val r1 = maxOf(-radius, -q - radius)
            val r2 = minOf(radius, -q + radius)
            for (r in r1..r2) add(q to r)
        }
    }
    return coords.mapIndexed { index, (q, r) ->
        when (index) {
            18 -> HexTile(index, q, r, Terrain.Grove, Tribe.Player, explored = true, settlement = "Elder Fig")
            7 -> HexTile(index, q, r, terrains[index], Tribe.Player, explored = true)
            30 -> HexTile(index, q, r, terrains[index], Tribe.Scarlet, explored = true, settlement = "Red Perch")
            4 -> HexTile(index, q, r, terrains[index], Tribe.Ivory, explored = true, settlement = "Moon Nest")
            in listOf(11, 12, 13, 17, 19, 23, 24, 25) -> HexTile(index, q, r, terrains[index], explored = true)
            else -> HexTile(index, q, r, terrains[index])
        }
    }
}

private fun axialDistance(q1: Int, r1: Int, q2: Int, r2: Int): Int {
    val s1 = -q1 - r1
    val s2 = -q2 - r2
    return maxOf(kotlin.math.abs(q1 - q2), kotlin.math.abs(r1 - r2), kotlin.math.abs(s1 - s2))
}

@Composable
private fun CanopyApp(vm: GameViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(GameTab.Canopy) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F1712)) {
                GameTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.icon, fontWeight = FontWeight.Bold) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Night)
                .padding(padding),
            color = Night
        ) {
            AnimatedContent(targetState = tab, label = "tab") { selected ->
                when (selected) {
                    GameTab.Canopy -> CanopyScreen(vm.state, vm::feed, vm::play, vm::groom, vm::endTurn)
                    GameTab.Island -> IslandScreen(vm.state, vm::selectTile, vm::scout, vm::settle)
                    GameTab.Tribes -> TribesScreen(vm.state, vm::research)
                    GameTab.Log -> LogScreen(vm.state)
                }
            }
        }
    }
}

enum class GameTab(val label: String, val icon: String) {
    Canopy("Canopy", "C"),
    Island("Island", "I"),
    Tribes("Tribes", "T"),
    Log("Log", "L")
}

@Composable
private fun CanopyScreen(
    state: GameState,
    onFeed: () -> Unit,
    onPlay: () -> Unit,
    onGroom: () -> Unit,
    onEndTurn: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Header(state)
        }
        item {
            MonkeyHabitat(state)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CareButton("Feed", "3 bananas", onFeed, Modifier.weight(1f))
                CareButton("Play", "2 timber", onPlay, Modifier.weight(1f))
                CareButton("Groom", "free", onGroom, Modifier.weight(1f))
            }
        }
        item {
            NeedPanel(state)
        }
        item {
            Button(
                onClick = onEndTurn,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Banana, contentColor = Night)
            ) {
                Text("End Turn", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Header(state: GameState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Canopy Dominion", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Turn ${state.turn} · offline monkey 4X", color = Color(0xFFB8C8B2))
            }
            Text("Troop ${state.troop}", color = Banana, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ResourcePill("Bananas", state.bananas, Banana, Modifier.weight(1f))
            ResourcePill("Timber", state.timber, Bark, Modifier.weight(1f))
            ResourcePill("Lore", state.lore, River, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResourcePill(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2B23))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, color = Color(0xFFB8C8B2), style = MaterialTheme.typography.labelSmall)
            Text(value.toString(), color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MonkeyHabitat(state: GameState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF244B38), Color(0xFF132017))))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCanopyBackground()
                drawMonkey(
                    mood = state.joy,
                    hunger = state.hunger,
                    center = Offset(size.width * 0.5f, size.height * 0.54f),
                    scale = size.minDimension / 260f
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    monkeyStatus(state),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Care affects production, loyalty, and troop growth.",
                    color = Color(0xFFCBD8C1),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun monkeyStatus(state: GameState): String = when {
    state.hunger > 78 -> "Your alpha is hungry and impatient."
    state.joy < 32 -> "The troop needs games and attention."
    state.bond > 82 -> "Your troop trusts your rule."
    else -> "The canopy is steady."
}

private fun DrawScope.drawCanopyBackground() {
    drawRect(Color(0xFF1E3528), size = size)
    for (i in 0..8) {
        val x = size.width * (i / 8f)
        drawCircle(Color(0xFF2F7D58), radius = 58f, center = Offset(x, 34f + (i % 3) * 22f))
    }
    drawRect(Soil, topLeft = Offset(0f, size.height * 0.82f), size = Size(size.width, size.height * 0.18f))
    drawLine(Bark, Offset(0f, size.height * 0.68f), Offset(size.width, size.height * 0.6f), strokeWidth = 18f)
}

private fun DrawScope.drawMonkey(mood: Int, hunger: Int, center: Offset, scale: Float) {
    fun Float.s() = this * scale
    drawCircle(Bark, 55f.s(), center)
    drawCircle(Color(0xFF6B421F), 28f.s(), center + Offset((-50f).s(), (-6f).s()))
    drawCircle(Color(0xFF6B421F), 28f.s(), center + Offset(50f.s(), (-6f).s()))
    drawCircle(Color(0xFFD9A066), 38f.s(), center + Offset(0f, 18f.s()))
    drawCircle(Night, 6f.s(), center + Offset((-18f).s(), (-9f).s()))
    drawCircle(Night, 6f.s(), center + Offset(18f.s(), (-9f).s()))
    drawCircle(Color(0xFFF2C48B), 8f.s(), center + Offset(0f, 11f.s()))
    val smile = mood >= 45 && hunger < 72
    val mouthY = center.y + 28f.s()
    if (smile) {
        drawArc(Night, 10f, 160f, false, Offset(center.x - 24f.s(), mouthY - 18f.s()), Size(48f.s(), 30f.s()), style = Stroke(4f.s()))
    } else {
        drawLine(Night, Offset(center.x - 18f.s(), mouthY), Offset(center.x + 18f.s(), mouthY), strokeWidth = 4f.s())
    }
    drawCircle(Banana, 10f.s(), center + Offset(62f.s(), 48f.s()))
}

@Composable
private fun CareButton(title: String, cost: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF244532), contentColor = Color.White)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(cost, style = MaterialTheme.typography.labelSmall, color = Color(0xFFC8D6C0))
        }
    }
}

@Composable
private fun NeedPanel(state: GameState) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NeedBar("Hunger", state.hunger, Threat)
            NeedBar("Joy", state.joy, Banana)
            NeedBar("Bond", state.bond, CanopyGreen)
        }
    }
}

@Composable
private fun NeedBar(label: String, value: Int, color: Color) {
    val progress by animateFloatAsState(value / 100f, label = label)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Bold)
            Text("$value")
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape),
            color = color,
            trackColor = Color(0xFF314036)
        )
    }
}

@Composable
private fun IslandScreen(
    state: GameState,
    onSelect: (Int) -> Unit,
    onScout: () -> Unit,
    onSettle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(state)
        HexMap(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            onSelect = onSelect
        )
        SelectedTilePanel(state, onScout, onSettle)
    }
}

@Composable
private fun HexMap(state: GameState, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 15.2f
            val center = Offset(size.width / 2f, size.height / 2f)
            state.tiles.forEach { tile ->
                val x = center.x + radius * 1.72f * (tile.q + tile.r / 2f)
                val y = center.y + radius * 1.5f * tile.r
                drawHex(tile, Offset(x, y), radius, tile.id == state.selectedTile)
            }
        }
        state.tiles.forEach { tile ->
            val radius = 1f / 15.2f
            val x = 0.5f + radius * 1.72f * (tile.q + tile.r / 2f)
            val y = 0.5f + radius * 1.5f * tile.r
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = ((x * 100).coerceIn(0f, 92f)).dp,
                        top = ((y * 100).coerceIn(0f, 92f)).dp
                    )
                    .size(42.dp)
                    .clickable { onSelect(tile.id) }
            )
        }
    }
}

private fun DrawScope.drawHex(tile: HexTile, center: Offset, radius: Float, selected: Boolean) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = PI / 180.0 * (60 * i - 30)
        val point = Offset(
            center.x + radius * cos(angle).toFloat(),
            center.y + radius * sin(angle).toFloat()
        )
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    val color = if (tile.explored) tile.terrain.color else Color(0xFF27302A)
    drawPath(path, color)
    drawPath(path, if (selected) Banana else Color(0xFF0B120E), style = Stroke(if (selected) 5f else 2f))
    if (tile.owner != Tribe.None) {
        val ownerColor = when (tile.owner) {
            Tribe.Player -> Banana
            Tribe.Scarlet -> Threat
            Tribe.Ivory -> Color(0xFFE7D9B5)
            Tribe.None -> Color.Transparent
        }
        drawCircle(ownerColor, radius * 0.22f, center)
    }
    if (tile.settlement != null) {
        drawRect(Color(0xFF15110C), Offset(center.x - radius * 0.28f, center.y - radius * 0.5f), Size(radius * 0.56f, radius * 0.46f))
    }
}

@Composable
private fun SelectedTilePanel(state: GameState, onScout: () -> Unit, onSettle: () -> Unit) {
    val tile = state.tiles.first { it.id == state.selectedTile }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (tile.explored) tile.terrain.label else "Unknown Canopy", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Owner: ${tile.owner.readable()}${tile.settlement?.let { " · $it" } ?: ""}", color = Color(0xFFB8C8B2))
                }
                Text("F${tile.terrain.food} W${tile.terrain.wood} L${tile.terrain.lore}", color = Banana, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onScout, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("Scout")
                }
                Button(onClick = onSettle, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("Settle")
                }
            }
        }
    }
}

private fun Tribe.readable(): String = when (this) {
    Tribe.Player -> "You"
    Tribe.Scarlet -> "Scarlet Tails"
    Tribe.Ivory -> "Ivory Hands"
    Tribe.None -> "None"
}

@Composable
private fun TribesScreen(state: GameState, onResearch: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Header(state) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Research", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Current level ${state.research}. Spend lore to improve your tribe economy and defense.", color = Color(0xFFB8C8B2))
                    Button(onClick = onResearch, shape = RoundedCornerShape(8.dp)) {
                        Text("Teach Trick · 6 lore")
                    }
                }
            }
        }
        items(state.rivals.size) { index ->
            RivalCard(state.rivals[index])
        }
    }
}

@Composable
private fun RivalCard(rival: Rival) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(rival.color),
                contentAlignment = Alignment.Center
            ) {
                Text(rival.name.take(1), color = Night, fontWeight = FontWeight.Black)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(rival.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(rival.plan, color = Color(0xFFB8C8B2))
                Text("Power ${rival.power} · Mood ${rival.mood}", color = Banana, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LogScreen(state: GameState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header(state) }
        items(state.log.size) { index ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
                Text(
                    state.log[index],
                    modifier = Modifier.padding(14.dp),
                    color = Color(0xFFEAF3DF)
                )
            }
        }
        item {
            Text(
                "No network permission is declared. All NPC choices, events, and game state run on device.",
                color = Color(0xFFB8C8B2),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            )
        }
    }
}
