package com.deepuniverse.app.ui.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepuniverse.app.ui.theme.DriftGlow
import com.deepuniverse.app.ui.theme.MutedStar
import com.deepuniverse.core.game.GameState
import com.deepuniverse.core.store.StoreCatalog
import com.deepuniverse.core.store.StoreOffer
import com.deepuniverse.core.store.SupportTier

/**
 * Where money comes in.
 *
 * The page is written to be honest rather than pushy, for two reasons. The obvious one is that
 * pressure tactics attached to a romance game are what draw regulator attention and store takedowns.
 * The less obvious one is that they do not work as well: people who feel handled stop playing, and a
 * player who stays is worth far more than one who is squeezed once.
 *
 * So: real prices up front, an explicit statement of what money does *not* buy, no countdown timers,
 * no fake scarcity, and a spending limit the player can set on themselves.
 */
@Composable
fun StoreScreen(
    state: GameState,
    purchasing: Boolean,
    message: String?,
    onBuy: (SupportTier) -> Unit,
    onRedeem: (StoreOffer) -> Unit,
    onSetLimit: (Int?) -> Unit,
    onDismissMessage: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Back") }
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.wallet.stars} ${StoreCatalog.CURRENCY}",
                    style = MaterialTheme.typography.titleMedium,
                    color = DriftGlow,
                )
            }
        }

        item {
            Column {
                Text("Support Deep Universe", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Buying Starlight speeds things up and says thank you. It does not buy " +
                        "affection: every scene, every character and every expression can be " +
                        "reached by playing, for free, at your own pace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedStar,
                )
            }
        }

        message?.let {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onDismissMessage() }
                        .padding(14.dp),
                ) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // ---- spend Starlight -------------------------------------------------
        item {
            Text(
                "Spend Starlight",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        items(StoreCatalog.offers, key = { it.id }) { offer ->
            val affordable = state.wallet.canAfford(offer.cost)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = affordable) { onRedeem(offer) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(offer.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        offer.blurb,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedStar,
                    )
                }
                Text(
                    "${offer.cost}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (affordable) DriftGlow else MutedStar.copy(alpha = 0.5f),
                )
            }
        }

        // ---- buy Starlight ---------------------------------------------------
        item {
            Text(
                "Get Starlight",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        items(StoreCatalog.tiers, key = { it.id }) { tier ->
            TierCard(tier = tier, enabled = !purchasing, onBuy = { onBuy(tier) })
        }

        if (purchasing) {
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DriftGlow)
                }
            }
        }

        // ---- spending controls ------------------------------------------------
        item {
            Column(Modifier.padding(top = 16.dp)) {
                Text("Set your own limit", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    state.wallet.selfImposedMonthlyLimit?.let {
                        "Currently capped at ${formatUnits(it)} a month. " +
                            "Purchases past that are refused before any payment screen opens."
                    } ?: "No limit set. You can cap what this game can charge you in a month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedStar,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(500, 2000, 5000).forEach { cents ->
                        TextButton(onClick = { onSetLimit(cents) }) { Text(formatUnits(cents)) }
                    }
                    TextButton(onClick = { onSetLimit(null) }) { Text("None") }
                }
            }
        }

        item {
            Column(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                if (state.wallet.purchaseCount > 0) {
                    Text(
                        "You have supported the game ${state.wallet.purchaseCount} " +
                            if (state.wallet.purchaseCount == 1) "time. Thank you." else "times. Thank you.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DriftGlow,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "Payments are handled by the app store, not by us. Prices shown are charged " +
                        "once — there is no subscription and nothing renews.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedStar.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun TierCard(tier: SupportTier, enabled: Boolean, onBuy: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (tier.highlighted) {
                    Modifier.border(1.5.dp, DriftGlow.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onBuy)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tier.title, style = MaterialTheme.typography.titleMedium)
                if (tier.highlighted) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "most chosen",
                        style = MaterialTheme.typography.labelSmall,
                        color = DriftGlow,
                    )
                }
            }
            Text(tier.blurb, style = MaterialTheme.typography.labelSmall, color = MutedStar)
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    "${tier.stars} ${StoreCatalog.CURRENCY}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DriftGlow,
                )
                if (tier.bonusStars > 0) {
                    Text(
                        "  +${tier.bonusStars} bonus",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8FD9A8),
                    )
                }
            }
        }
        Text(
            tier.fallbackPriceLabel,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Minor units to a readable price. Display only — the store is the authority on real prices. */
private fun formatUnits(cents: Int): String = "$%.2f".format(cents / 100f)
