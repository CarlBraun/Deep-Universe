package com.deepuniverse.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.deepuniverse.app.ui.avatar.AvatarPortrait
import com.deepuniverse.app.ui.avatar.CastLooks
import com.deepuniverse.app.ui.theme.MutedStar
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.game.AffectionLevel
import com.deepuniverse.core.game.Cast
import com.deepuniverse.core.game.GameState
import com.deepuniverse.core.game.LoveInterest

/** Aurora-9's crew deck: the player, and everyone aboard worth knowing. */
@Composable
fun HomeScreen(
    state: GameState,
    onOpenRoute: (String) -> Unit,
    onEditCharacter: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            PlayerBanner(player = state.player, onEdit = onEditCharacter)
        }

        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Text(
                "Aurora-9",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        items(Cast.all, key = { it.id }) { member ->
            CastCard(
                member = member,
                points = state.affectionFor(member.id),
                onClick = { onOpenRoute(member.id) },
            )
        }
    }
}

@Composable
private fun PlayerBanner(player: CharacterAppearance, onEdit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarPortrait(
            appearance = player,
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(player.name, style = MaterialTheme.typography.titleLarge)
            Text(
                "Resonance Pilot · ${player.pronouns.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MutedStar,
            )
        }
        TextButton(onClick = onEdit) { Text("Edit") }
    }
}

@Composable
private fun CastCard(member: LoveInterest, points: Int, onClick: () -> Unit) {
    val level = AffectionLevel.forPoints(points)
    val accent = Color(member.themeColor)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        Box {
            AvatarPortrait(
                appearance = CastLooks.of(member.id),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f),
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent.copy(alpha = 0.85f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    member.pronouns.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(member.name, style = MaterialTheme.typography.titleMedium)
            Text(
                member.role,
                style = MaterialTheme.typography.labelSmall,
                color = MutedStar,
            )
            Spacer(Modifier.height(8.dp))
            AffectionMeter(points = points, accent = accent)
            Text(
                level.label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** A bond meter that fills towards the next tier, not towards some invisible maximum. */
@Composable
fun AffectionMeter(points: Int, accent: Color, modifier: Modifier = Modifier) {
    val level = AffectionLevel.forPoints(points)
    val toNext = AffectionLevel.pointsToNext(points)
    val nextThreshold = toNext?.let { points + it }
    val progress = if (nextThreshold == null) {
        1f
    } else {
        val span = (nextThreshold - level.minPoints).coerceAtLeast(1)
        ((points - level.minPoints).toFloat() / span).coerceIn(0f, 1f)
    }

    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = accent,
        trackColor = accent.copy(alpha = 0.18f),
    )
}
