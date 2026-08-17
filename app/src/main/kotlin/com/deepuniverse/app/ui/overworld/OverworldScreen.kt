package com.deepuniverse.app.ui.overworld

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepuniverse.app.ui.Reward
import com.deepuniverse.app.ui.avatar.AvatarPortrait
import com.deepuniverse.app.ui.avatar.CastLooks
import com.deepuniverse.app.ui.overworld.PixelSprite.drawCharacterSprite
import com.deepuniverse.app.ui.overworld.TileArt.drawTile
import com.deepuniverse.app.ui.theme.DriftGlow
import com.deepuniverse.app.ui.theme.MutedStar
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.alpha
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.game.Bond
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
 * How much the off-axis lean has to beat the on-axis one before the stick changes direction.
 *
 * 1.0 would mean no hysteresis at all and a thumb resting near a diagonal would flicker between two
 * directions many times a second.
 */
private const val AXIS_STICKINESS = 1.5f

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
    reward: Reward?,
    momentsLeft: Int,
    momentsMax: Int,
    stars: Int,
    boosted: Boolean,
    puzzleLabel: String?,
    onMove: (Direction) -> Unit,
    /** Tapped a tile: walk there, and face whoever was tapped. */
    onWalkTo: (Int, Int) -> Unit,
    onInteract: () -> Unit,
    onPlayPuzzle: () -> Unit,
    onDismissMessage: () -> Unit,
    onDismissReward: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpenStore: () -> Unit,
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
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "◈ $momentsLeft/$momentsMax",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (momentsLeft > 0) DriftGlow else MutedStar,
                    )
                    if (boosted) {
                        Spacer(Modifier.size(6.dp))
                        Text("×2", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8FD9A8))
                    }
                }
                Row {
                    TextButton(onClick = onOpenJournal) { Text("Journal") }
                    TextButton(onClick = onOpenStore) { Text("✦ $stars") }
                }
            }
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
                    onTapTile = onWalkTo,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (reward != null) {
                RewardCard(reward = reward, onDismiss = onDismissReward)
            }

            // A passing remark from someone with no new scene for you, over the map so the world
            // stays visible behind it.
            if (message != null && reward == null) {
                val member = facingNpc?.let { Cast.byId(it.loveInterestId) }
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
                        member?.let {
                            Text(
                                it.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(it.themeColor),
                            )
                            Spacer(Modifier.height(4.dp))
                        }
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
                    "Tap where you want to go — or tap someone to walk over and talk.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedStar.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
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
            Joystick(onStep = onMove)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (facingNpc != null && puzzleLabel != null) {
                    OutlinedButton(onClick = onPlayPuzzle) { Text(puzzleLabel) }
                    Spacer(Modifier.height(10.dp))
                }
                TalkButton(enabled = facingNpc != null, onClick = onInteract)
            }
        }
    }
}

@Composable
private fun WorldCanvas(
    area: Area,
    position: WorldPosition,
    player: CharacterAppearance,
    frame: Int,
    onTapTile: (Int, Int) -> Unit,
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

    Canvas(
        modifier.pointerInput(area) {
            // Tap the world to walk there. This is the primary way to get anywhere: aiming a stick
            // at one specific doorway on a phone is genuinely hard, and pointing at the door is
            // what the player wanted to say in the first place.
            detectTapGestures { tap ->
                val tile = minOf(
                    size.width.toFloat() / area.map.width,
                    size.height.toFloat() / area.map.height,
                )
                if (tile <= 0f) return@detectTapGestures
                val originX = (size.width - tile * area.map.width) / 2f
                val originY = (size.height - tile * area.map.height) / 2f
                val x = kotlin.math.floor((tap.x - originX) / tile).toInt()
                val y = kotlin.math.floor((tap.y - originY) / tile).toInt()
                if (x in 0 until area.map.width && y in 0 until area.map.height) {
                    onTapTile(x, y)
                }
            }
        },
    ) {
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
 * An analog stick: press anywhere on the pad and drag towards where you want to go.
 *
 * Replaces a four-way d-pad, which was fiddly on a phone — the buttons are small, the gaps between
 * them are dead, and changing direction means lifting your thumb and finding a different target. A
 * stick is one continuous gesture: put a thumb down, lean, keep leaning. You can also press it like
 * a pad, because pressing off-centre reads as leaning in that direction.
 *
 * Movement is still grid-based underneath, so the stick's job is only to answer "which way, and is
 * the player still asking" — the diagonal is resolved to whichever axis dominates.
 */
@Composable
private fun Joystick(onStep: (Direction) -> Unit) {
    val size = 148.dp
    val knobSize = 60.dp
    val density = LocalDensity.current
    val radiusPx = with(density) { (size - knobSize).toPx() / 2f }
    // Below this the touch is too central to mean a direction, which stops a resting thumb from
    // walking the character into a wall. Kept small: on a stick this wide, a deliberate lean is
    // obvious, and a large dead zone is felt as the controls ignoring you.
    val deadZone = radiusPx * 0.18f

    var knob by remember { mutableStateOf(Offset.Zero) }
    var heading by remember { mutableStateOf<Direction?>(null) }
    var engaged by remember { mutableStateOf(false) }

    // The repeat loop is keyed on *whether a thumb is down*, not on which way it is pointing, and
    // reads the heading fresh each tick. Keying it on the heading — the obvious version — restarts
    // the whole loop, initial delay and all, every time a wobbling thumb crosses a diagonal, which
    // is exactly what made the stick feel like it was fighting back.
    LaunchedEffect(engaged) {
        if (!engaged) return@LaunchedEffect
        var stepsTaken = 0
        while (true) {
            val direction = heading
            if (direction == null) {
                // Thumb down but centred: wait for it to mean something without burning the loop.
                delay(16)
                continue
            }
            onStep(direction)
            // A brief pause after the first step, so a tap of the stick is a single tile rather
            // than a sprint. Every step after that is at walking speed.
            delay(if (stepsTaken == 0) 190L else STEP_MILLIS.toLong())
            stepsTaken++
        }
    }

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .pointerInput(Unit) {
                val centre = Offset(this.size.width / 2f, this.size.height / 2f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun applyTouch(position: Offset) {
                        val from = position - centre
                        val length = kotlin.math.hypot(from.x, from.y)
                        knob = if (length > radiusPx && length > 0f) {
                            from * (radiusPx / length)
                        } else {
                            from
                        }
                        heading = if (length < deadZone) {
                            null
                        } else {
                            val horizontalNow = kotlin.math.abs(from.x)
                            val verticalNow = kotlin.math.abs(from.y)
                            // The axis you are already walking along is sticky: the other one has to
                            // win clearly to take over. Without this, holding the stick anywhere near
                            // a diagonal alternates between the two axes every few milliseconds and
                            // the character shuffles on the spot instead of going anywhere.
                            val goHorizontal = when (heading) {
                                Direction.LEFT, Direction.RIGHT -> horizontalNow * AXIS_STICKINESS > verticalNow
                                Direction.UP, Direction.DOWN -> horizontalNow > verticalNow * AXIS_STICKINESS
                                null -> horizontalNow > verticalNow
                            }
                            when {
                                goHorizontal && from.x > 0 -> Direction.RIGHT
                                goHorizontal -> Direction.LEFT
                                from.y > 0 -> Direction.DOWN
                                else -> Direction.UP
                            }
                        }
                    }

                    applyTouch(down.position)
                    engaged = true
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        applyTouch(change.position)
                        change.consume()
                    }
                    knob = Offset.Zero
                    heading = null
                    engaged = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A faint cross, so the stick still reads as directional at a glance.
        Text(
            "＋",
            style = MaterialTheme.typography.displaySmall,
            color = MutedStar.copy(alpha = 0.25f),
        )
        Box(
            Modifier
                .offset { IntOffset(knob.x.toInt(), knob.y.toInt()) }
                .size(knobSize)
                .clip(CircleShape)
                .background(
                    if (heading != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (heading) {
                    Direction.UP -> "▲"
                    Direction.DOWN -> "▼"
                    Direction.LEFT -> "◀"
                    Direction.RIGHT -> "▶"
                    null -> "●"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (heading != null) Color.White else DriftGlow.copy(alpha = 0.8f),
            )
        }
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

/**
 * The payoff for a moment together: what they said, what it earned, and any face it unlocked.
 *
 * A new face is shown *on the character*, immediately, rather than as an icon in a list — the
 * reward is seeing them look at you differently, so that is what the card shows.
 */
@Composable
private fun RewardCard(reward: Reward, onDismiss: () -> Unit) {
    val member = Cast.byId(reward.loveInterestId)
    val accent = Color(member.themeColor)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            if (reward.unlockedExpression != null) {
                AvatarPortrait(
                    appearance = CastLooks.of(member.id),
                    expression = reward.unlockedExpression,
                    accent = accent,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .aspectRatio(0.8f)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "New expression",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedStar,
                )
                Text(
                    reward.unlockedExpression.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
                Text(
                    reward.unlockedExpression.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedStar,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
            }

            Text(
                reward.line,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "+${reward.points} bond" + if (reward.boosted) "  (boosted)" else "",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            reward.newRank?.let { rank ->
                Text(
                    "${member.name} — ${Bond.titleFor(rank)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = DriftGlow,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "tap to continue",
                style = MaterialTheme.typography.labelSmall,
                color = MutedStar.copy(alpha = 0.7f),
                modifier = Modifier.alpha(0.8f),
            )
        }
    }
}
