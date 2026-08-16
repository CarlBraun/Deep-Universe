package com.deepuniverse.app.ui.route

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.deepuniverse.app.ui.avatar.AvatarPortrait
import com.deepuniverse.app.ui.avatar.CastLooks
import com.deepuniverse.app.ui.home.AffectionMeter
import com.deepuniverse.app.ui.theme.MutedStar
import com.deepuniverse.core.game.AffectionLevel
import com.deepuniverse.core.game.GameState
import com.deepuniverse.core.game.LoveInterest
import com.deepuniverse.core.game.Scene

/** One character's page: who they are, how close you are, and what you can play. */
@Composable
fun RouteScreen(
    member: LoveInterest,
    state: GameState,
    available: List<Scene>,
    locked: List<Scene>,
    onPlayScene: (Scene) -> Unit,
    onBack: () -> Unit,
) {
    val accent = Color(member.themeColor)
    val points = state.affectionFor(member.id)
    val level = AffectionLevel.forPoints(points)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                AvatarPortrait(
                    appearance = CastLooks.of(member.id),
                    modifier = Modifier.fillMaxSize(),
                )
                // Fade the portrait into the page so the text below has something to sit on.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.55f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                )
                TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                    Text("← Crew deck")
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(member.name, style = MaterialTheme.typography.displaySmall)
                Text(
                    "${member.role} · ${member.pronouns.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Spacer(Modifier.height(14.dp))
                Text(member.tagline, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    member.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedStar,
                )
                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(level.label, style = MaterialTheme.typography.labelLarge, color = accent)
                    Text(
                        AffectionLevel.pointsToNext(points)
                            ?.let { "$it to ${nextLevelLabel(points)}" }
                            ?: "Closest bond",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedStar,
                    )
                }
                Spacer(Modifier.height(6.dp))
                AffectionMeter(points = points, accent = accent)
                Spacer(Modifier.height(24.dp))
                Text("Moments", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
            }
        }

        items(available, key = { it.id }) { scene ->
            SceneRow(
                scene = scene,
                accent = accent,
                played = scene.id in state.completedScenes,
                locked = false,
                onClick = { onPlayScene(scene) },
            )
        }

        items(locked, key = { it.id }) { scene ->
            SceneRow(
                scene = scene,
                accent = accent,
                played = false,
                locked = true,
                onClick = {},
            )
        }

        if (available.isEmpty() && locked.isEmpty()) {
            item {
                Text(
                    "Nothing new right now. Spend time with someone else and come back.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedStar,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun SceneRow(
    scene: Scene,
    accent: Color,
    played: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (locked) Modifier else Modifier.clickable(onClick = onClick))
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                scene.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (locked) MutedStar else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                when {
                    locked -> "🔒 ${scene.requiredLevel.label}"
                    played -> "Replay"
                    else -> "New"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (locked) MutedStar else accent,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (locked) "Grow closer to unlock this moment." else scene.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedStar,
        )
    }
}

private fun nextLevelLabel(points: Int): String =
    AffectionLevel.entries.firstOrNull { it.minPoints > points }?.label ?: ""
