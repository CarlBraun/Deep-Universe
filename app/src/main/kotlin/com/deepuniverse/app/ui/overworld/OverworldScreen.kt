package com.deepuniverse.app.ui.overworld

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepuniverse.app.ui.avatar.CastLooks
import com.deepuniverse.app.ui.overworld.PixelSprite.drawCharacterSprite
import com.deepuniverse.app.ui.overworld.TileArt.drawTile
import com.deepuniverse.app.ui.theme.DriftGlow
import com.deepuniverse.app.ui.theme.MutedStar
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.game.Cast
import com.deepuniverse.core.world.Area
import com.deepuniverse.core.world.Direction
import com.deepuniverse.core.world.NpcSpawn
import com.deepuniverse.core.world.WorldAtlas
import com.deepuniverse.core.world.WorldPosition
import kotlinx.coroutines.delay

/** How long a single tile of walking takes. Fast enough to feel responsive, slow enough to read. */
private const val STEP_MILLIS = 150

/**
 * The walk-around world.
 *
 * You move your pixel self between the camp, the lodge, the cabins and the beach, and pressing the
 * talk button while facing someone drops you into the full-size portrait and dialogue. Finding
 * people is the point: nobody is in a menu, so learning where everyone spends their time *is*
 * getting to know them.
 */
@Composable
fun OverworldScreen(
    position: WorldPosition,
    player: CharacterAppearance,
    facingNpc: NpcSpawn?,
    message: String?,
    onMove: (Direction) -> Unit,
    onInteract: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenJournal: () -> Unit,
) {
    val area = remember(position.areaId) { WorldAtlas.area(position.areaId) }

    // A walk cycle that only advances while the player is actually moving.
    var frame by remember { mutableIntStateOf(0) }
    var lastTile by remember { mutableStateOf(position.x to position.y) }
    LaunchedEffect(position.x, position.y) {
        if (lastTile != position.x to position.y) {
            frame = (frame + 1) % 4
            lastTile = position.x to position.y
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ---- location banner ------------------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(area.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    area.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedStar,
                )
            }
            TextButton(onClick = onOpenJournal) { Text("Journal") }
        }

        // ---- the map --------------------------------------------------------
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (area.indoors) Color(0xFF15101C) else Color(0xFF152416)),
            contentAlignment = Alignment.Center,
        ) {
            // Re-keyed per area so the walk animation starts fresh instead of sliding the
            // character across the screen when they step through a door.
            key(position.areaId) {
                WorldCanvas(
                    area = area,
                    position = position,
                    player = player,
                    frame = frame,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // A passing remark from someone with no new scene for you, over the map so the world
            // stays visible behind it.
            if (message != null && facingNpc != null) {
                val member = Cast.byId(facingNpc.loveInterestId)
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .pointerInput(Unit) { detectTapGestures(onTap = { onDismissMessage() }) }
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            member.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(member.themeColor),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ---- who you are facing ---------------------------------------------
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (facingNpc != null) {
                val member = Cast.byId(facingNpc.loveInterestId)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(member.themeColor),
                    )
                    Text(
                        facingNpc.activity,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedStar,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    "Walk up to someone and face them to talk.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedStar.copy(alpha = 0.6f),
                )
            }
        }

        // ---- controls --------------------------------------------------------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            DirectionPad(onStep = onMove)
            TalkButton(enabled = facingNpc != null, onClick = onInteract)
        }
    }
}

@Composable
private fun WorldCanvas(
    area: Area,
    position: WorldPosition,
    player: CharacterAppearance,
    frame: Int,
    modifier: Modifier = Modifier,
) {
    // Interpolating the drawn position rather than snapping is what makes walking feel like walking.
    val drawX by animateFloatAsState(
        targetValue = position.x.toFloat(),
        animationSpec = tween(STEP_MILLIS, easing = LinearEasing),
        label = "walkX",
    )
    val drawY by animateFloatAsState(
        targetValue = position.y.toFloat(),
        animationSpec = tween(STEP_MILLIS, easing = LinearEasing),
        label = "walkY",
    )

    Canvas(modifier) {
        // The whole area is shown at once — these maps are small, and seeing the entire clearing
        // beats scrolling a camera around it.
        val tile = minOf(size.width / area.map.width, size.height / area.map.height)
        val originX = (size.width - tile * area.map.width) / 2f
        val originY = (size.height - tile * area.map.height) / 2f

        for (y in 0 until area.map.height) {
            for (x in 0 until area.map.width) {
                drawTile(
                    tile = area.map[x, y],
                    x = x,
                    y = y,
                    left = originX + x * tile,
                    top = originY + y * tile,
                    size = tile,
                )
            }
        }

        // Characters and the player are drawn in row order so someone standing lower on the map
        // overlaps someone higher up, which is what sells the pseudo-3D of a top-down world.
        data class Actor(val row: Float, val draw: () -> Unit)

        val actors = mutableListOf<Actor>()

        for (npc in area.npcs) {
            val look = CastLooks.of(npc.loveInterestId)
            actors.add(
                Actor(npc.y.toFloat()) {
                    drawCharacterSprite(
                        appearance = look,
                        facing = npc.facing,
                        frame = 0,
                        origin = Offset(
                            originX + (npc.x + 0.5f) * tile,
                            originY + (npc.y + 1f) * tile,
                        ),
                        height = PixelSprite.spriteHeight(look, tile),
                    )
                },
            )
        }

        actors.add(
            Actor(drawY) {
                drawCharacterSprite(
                    appearance = player,
                    facing = position.facing,
                    frame = frame,
                    origin = Offset(
                        originX + (drawX + 0.5f) * tile,
                        originY + (drawY + 1f) * tile,
                    ),
                    height = PixelSprite.spriteHeight(player, tile),
                )
            },
        )

        actors.sortedBy { it.row }.forEach { it.draw() }
    }
}

/**
 * A four-way pad that repeats while held.
 *
 * Hold-to-walk matters more than it sounds: without it, crossing a map means tapping thirty times,
 * and the world stops feeling like somewhere you can wander.
 */
@Composable
private fun DirectionPad(onStep: (Direction) -> Unit) {
    var held by remember { mutableStateOf<Direction?>(null) }

    LaunchedEffect(held) {
        val direction = held ?: return@LaunchedEffect
        onStep(direction)
        // A brief pause before repeating, so a single tap is a single step.
        delay(260)
        while (true) {
            onStep(direction)
            delay(STEP_MILLIS.toLong())
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PadButton("▲", Direction.UP) { held = it }
        Row {
            PadButton("◀", Direction.LEFT) { held = it }
            Spacer(Modifier.size(56.dp))
            PadButton("▶", Direction.RIGHT) { held = it }
        }
        PadButton("▼", Direction.DOWN) { held = it }
    }
}

@Composable
private fun PadButton(glyph: String, direction: Direction, onHeldChange: (Direction?) -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(direction) {
                detectTapGestures(
                    onPress = {
                        onHeldChange(direction)
                        // Suspends until the finger lifts or the gesture is cancelled, which is
                        // what lets the repeat loop above run for exactly as long as it is held.
                        tryAwaitRelease()
                        onHeldChange(null)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = DriftGlow)
    }
}

@Composable
private fun TalkButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            )
            .pointerInput(enabled) {
                detectTapGestures(onTap = { if (enabled) onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "TALK",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else MutedStar.copy(alpha = 0.5f),
        )
    }
}
