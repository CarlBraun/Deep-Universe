package com.deepuniverse.app.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deepuniverse.app.ui.avatar.AvatarPortrait
import com.deepuniverse.app.ui.avatar.CastLooks
import com.deepuniverse.app.ui.theme.DriftGlow
import com.deepuniverse.app.ui.theme.MutedStar
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.game.LoveInterest
import com.deepuniverse.core.game.ScenePlayback
import com.deepuniverse.core.game.StoryFrame

/**
 * The visual-novel screen.
 *
 * Tapping anywhere advances, which is the genre convention and keeps the reading hand where it
 * already is. Choices deliberately break that: they are buttons that must be aimed at, so a player
 * tapping through dialogue cannot blunder into an answer they did not mean to give.
 */
@Composable
fun StoryScreen(
    playback: ScenePlayback,
    /** Null for a scene about the world rather than a person — the ship in the bracken has no owner. */
    member: LoveInterest?,
    player: CharacterAppearance,
    onAdvance: () -> Unit,
    onChoose: (Int) -> Unit,
    onExit: () -> Unit,
) {
    val accent = member?.let { Color(it.themeColor) } ?: DriftGlow
    val frame = playback.frame()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (playback.canAdvance) Modifier.clickable(onClick = onAdvance) else Modifier),
    ) {
        // Whoever is speaking gets the stage. Narration keeps the partner on screen, dimmed.
        // With nobody on stage — a scene about a place — the player stands in it alone.
        val speakerIsPlayer = (frame as? StoryFrame.Line)?.isPlayer == true
        AvatarPortrait(
            appearance = if (speakerIsPlayer || member == null) player else CastLooks.of(member.id),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .align(Alignment.TopCenter)
                .alpha(if ((frame as? StoryFrame.Line)?.isNarration == true) 0.55f else 1f),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )

        Text(
            playback.scene.title,
            style = MaterialTheme.typography.labelSmall,
            color = MutedStar,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            when (frame) {
                is StoryFrame.Line -> DialogueBox(frame = frame, accent = accent)

                is StoryFrame.Question -> {
                    Text(
                        frame.prompt,
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MutedStar,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        frame.options.forEachIndexed { index, option ->
                            OutlinedButton(
                                onClick = { onChoose(index) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text(
                                    option,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }

                is StoryFrame.Ended -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (frame.gainedAffection > 0 && member != null) {
                                "You grew closer to ${member.name}."
                            } else {
                                "The moment passes."
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (frame.gainedAffection > 0) {
                            Text(
                                "+${frame.gainedAffection} bond",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = onExit,
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            Text(member?.let { "Back to ${it.name.substringBefore(' ')}" } ?: "Go on")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogueBox(frame: StoryFrame.Line, accent: Color) {
    Column {
        frame.speakerName?.let { name ->
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = if (frame.isPlayer) MaterialTheme.colorScheme.onBackground else accent,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(16.dp),
        ) {
            Text(
                frame.text,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = if (frame.isNarration) FontStyle.Italic else FontStyle.Normal,
                color = if (frame.isNarration) MutedStar else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            "Tap to continue",
            style = MaterialTheme.typography.labelSmall,
            color = MutedStar,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 6.dp),
        )
    }
}
