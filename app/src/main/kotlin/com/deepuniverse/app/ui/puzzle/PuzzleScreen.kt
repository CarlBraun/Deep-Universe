package com.deepuniverse.app.ui.puzzle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepuniverse.app.ui.PuzzleState
import com.deepuniverse.app.ui.theme.DriftGlow
import com.deepuniverse.app.ui.theme.MutedStar
import com.deepuniverse.core.game.LoveInterest
import com.deepuniverse.core.puzzle.CookingGame
import com.deepuniverse.core.puzzle.Minesweeper
import com.deepuniverse.core.puzzle.PuzzleKind
import com.deepuniverse.core.puzzle.PuzzleOutcome
import com.deepuniverse.core.puzzle.SquirrelHunt
import com.deepuniverse.core.store.PuzzleRetry
import com.deepuniverse.core.store.StoreCatalog

/**
 * The minigames, played with somebody.
 *
 * All three share this frame — the character's name at the top, the game in the middle, and one
 * result panel at the bottom — so a player who learns one has learned the shape of all of them. The
 * games themselves own no rules: every tap goes to the state machine in `core` and this only draws
 * what comes back.
 */
@Composable
fun PuzzleScreen(
    puzzle: PuzzleState,
    member: LoveInterest,
    stars: Int,
    adReady: Boolean,
    onRevealCell: (Int, Int) -> Unit,
    onFlagCell: (Int, Int) -> Unit,
    onSearch: (Int, Int) -> Unit,
    onCookTick: (Long, Boolean) -> Unit,
    onRetryWithStars: () -> Unit,
    onRetryWithAd: () -> Unit,
    onLeave: () -> Unit,
) {
    val accent = Color(member.themeColor)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLeave) { Text("← Leave") }
            Spacer(Modifier.weight(1f))
            Text("✦ $stars", style = MaterialTheme.typography.labelLarge, color = DriftGlow)
        }

        Text(puzzle.kind.label, style = MaterialTheme.typography.displaySmall)
        Text(
            "with ${member.name}",
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(puzzle.kind.blurb, style = MaterialTheme.typography.labelSmall, color = MutedStar)
        Spacer(Modifier.height(14.dp))

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            when (puzzle) {
                is PuzzleState.Sweep -> MinesweeperBoard(
                    board = puzzle.board,
                    accent = accent,
                    flagging = puzzle.flagging,
                    onReveal = onRevealCell,
                    onFlag = onFlagCell,
                )

                is PuzzleState.Hunt -> SquirrelBoard(
                    hunt = puzzle.hunt,
                    accent = accent,
                    onSearch = onSearch,
                )

                is PuzzleState.Cook -> CookingPot(
                    game = puzzle.game,
                    accent = accent,
                    onTick = onCookTick,
                )
            }
        }

        ResultPanel(
            outcome = puzzle.outcome,
            kind = puzzle.kind,
            member = member,
            accent = accent,
            stars = stars,
            adReady = adReady,
            onRetryWithStars = onRetryWithStars,
            onRetryWithAd = onRetryWithAd,
            onLeave = onLeave,
        )
    }
}

// --------------------------------------------------------------------------- minesweeper

@Composable
private fun MinesweeperBoard(
    board: Minesweeper,
    accent: Color,
    flagging: Boolean,
    onReveal: (Int, Int) -> Unit,
    onFlag: (Int, Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${board.minesRemaining} left to find" +
                if (flagging) " · flag mode" else " · long-press to flag",
            style = MaterialTheme.typography.labelSmall,
            color = MutedStar,
        )
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(board.width.toFloat() / board.height),
        ) {
            for (y in 0 until board.height) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (x in 0 until board.width) {
                        val cell = board[x, y]
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        !cell.revealed -> MaterialTheme.colorScheme.surfaceVariant
                                        cell.mine -> Color(0xFF8C2F3E)
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                )
                                .pointerInput(x, y, board.outcome) {
                                    detectTapGestures(
                                        // Long-press flags, which is the convention everyone
                                        // already knows from the desktop right-click.
                                        onLongPress = { onFlag(x, y) },
                                        onTap = { if (flagging) onFlag(x, y) else onReveal(x, y) },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                cell.flagged && !cell.revealed -> Text("⚑", color = accent)
                                cell.revealed && cell.mine -> Text("✳", color = Color.White)
                                cell.revealed && cell.neighbours > 0 -> Text(
                                    "${cell.neighbours}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = numberColour(cell.neighbours),
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun numberColour(n: Int): Color = when (n) {
    1 -> Color(0xFF7FB4E8)
    2 -> Color(0xFF8FD9A8)
    3 -> Color(0xFFE8899F)
    4 -> Color(0xFFB79BFF)
    else -> Color(0xFFE8C46A)
}

// --------------------------------------------------------------------------- squirrel

@Composable
private fun SquirrelBoard(hunt: SquirrelHunt, accent: Color, onSearch: (Int, Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            hunt.lastWarmth?.label ?: "Somewhere in here, something is holding very still.",
            style = MaterialTheme.typography.bodyLarge,
            color = if (hunt.lastWarmth != null) accent else MutedStar,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${hunt.attemptsLeft} looks left",
            style = MaterialTheme.typography.labelSmall,
            color = MutedStar,
        )
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .aspectRatio(hunt.width.toFloat() / hunt.height),
        ) {
            for (y in 0 until hunt.height) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (x in 0 until hunt.width) {
                        val checked = (x to y) in hunt.searched
                        val found = hunt.outcome == PuzzleOutcome.WON &&
                            x == hunt.squirrelX && y == hunt.squirrelY
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        found -> accent
                                        checked -> MaterialTheme.colorScheme.surface
                                        else -> Color(0xFF3D6533)
                                    },
                                )
                                .clickable(enabled = !checked) { onSearch(x, y) },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                found -> Text("🐿", style = MaterialTheme.typography.titleMedium)
                                checked -> Text(
                                    "·",
                                    color = MutedStar.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- cooking

@Composable
private fun CookingPot(game: CookingGame, accent: Color, onTick: (Long, Boolean) -> Unit) {
    var holding by remember { mutableStateOf(false) }

    // Drive the pot from the frame clock. The game owns no timer of its own, so this hands it the
    // elapsed milliseconds and whether a finger is down, once a frame.
    LaunchedEffect(game.outcome) {
        if (game.outcome != PuzzleOutcome.IN_PROGRESS) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) {
            val nowMillis = withFrameMillis { it }
            onTick(nowMillis - last, holding)
            last = nowMillis
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The gauge: heat as a marker, the target as a band you are trying to sit inside.
        Box(
            Modifier
                .width(78.dp)
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(game.bandHalfHeight * 2f)
                    .align(BiasAlignment(0f, 1f - game.bandCentre * 2f))
                    .background(accent.copy(alpha = if (game.inBand) 0.55f else 0.28f)),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(BiasAlignment(0f, 1f - game.heat * 2f))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White, Color.Transparent),
                        ),
                    ),
            )
        }

        Column(Modifier.weight(1f)) {
            Text(
                if (game.inBand) "Good — hold it there." else "Off the mark.",
                style = MaterialTheme.typography.titleMedium,
                color = if (game.inBand) accent else MutedStar,
            )
            Spacer(Modifier.height(8.dp))
            Text("Dish", style = MaterialTheme.typography.labelSmall, color = MutedStar)
            LinearProgressIndicator(
                progress = { game.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = accent,
                trackColor = accent.copy(alpha = 0.18f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${game.millisRemaining / 1000}s left",
                style = MaterialTheme.typography.labelSmall,
                color = MutedStar,
            )
            Spacer(Modifier.height(20.dp))

            // Press and hold to raise the heat; let go and it falls.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (holding) accent else MaterialTheme.colorScheme.surface,
                    )
                    .pointerInput(game.outcome) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            holding = true
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                            }
                            holding = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (holding) "HEAT" else "HOLD",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (holding) Color.White else DriftGlow,
                )
            }
        }
    }
}

/** Positions a child by fraction of the parent, so the gauge marker can sit at any height. */
private class BiasAlignment(
    private val horizontalBias: Float,
    private val verticalBias: Float,
) : Alignment {
    override fun align(
        size: androidx.compose.ui.unit.IntSize,
        space: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
    ): androidx.compose.ui.unit.IntOffset {
        val centreX = (space.width - size.width).toFloat() / 2f
        val centreY = (space.height - size.height).toFloat() / 2f
        val x = centreX * (1 + horizontalBias)
        val y = centreY * (1 + verticalBias.coerceIn(-1f, 1f))
        return androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
    }
}

// --------------------------------------------------------------------------- result

/**
 * The panel under every game.
 *
 * On a loss it offers an immediate retry for an ad or Stars — and states, in the same breath, that
 * coming back later is free. Both halves are shown together on purpose: an offer that hides the
 * free alternative is the version of this pattern that earns refund requests.
 */
@Composable
private fun ResultPanel(
    outcome: PuzzleOutcome,
    kind: PuzzleKind,
    member: LoveInterest,
    accent: Color,
    stars: Int,
    adReady: Boolean,
    onRetryWithStars: () -> Unit,
    onRetryWithAd: () -> Unit,
    onLeave: () -> Unit,
) {
    when (outcome) {
        PuzzleOutcome.IN_PROGRESS -> Unit

        PuzzleOutcome.WON -> Column(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Together, easily.", style = MaterialTheme.typography.titleLarge, color = accent)
            Text(
                "+${kind.reward} bond with ${member.name.substringBefore(' ')}",
                style = MaterialTheme.typography.labelLarge,
                color = MutedStar,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onLeave,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text("Back to the world") }
        }

        PuzzleOutcome.LOST -> Column(
            Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Text("That one got away.", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                PuzzleRetry.FREE_RETRY_EXPLANATION,
                style = MaterialTheme.typography.labelSmall,
                color = MutedStar,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (adReady) {
                    OutlinedButton(
                        onClick = onRetryWithAd,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text("Watch an ad") }
                }
                OutlinedButton(
                    onClick = onRetryWithStars,
                    enabled = stars >= PuzzleRetry.STAR_COST,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("${PuzzleRetry.STAR_COST} ${StoreCatalog.CURRENCY}")
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text("Leave it for now")
            }
        }
    }
}
