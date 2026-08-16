package com.deepuniverse.core.character

import com.deepuniverse.core.color.argb
import com.deepuniverse.core.color.perceptualDistance
import kotlinx.serialization.Serializable

@Serializable
data class Swatch(val id: String, val label: String, val argb: Int)

/**
 * The colour swatches offered in the character creator.
 *
 * The photo analyzer does not hand the renderer a raw sampled pixel — it snaps the sample to the
 * nearest swatch here. Raw pixels carry the photograph's white balance with them, so a face shot
 * under warm indoor light would produce an orange character; snapping to an art-directed palette
 * keeps every generated character on-model, and leaves the player editing the same swatch list they
 * would have used by hand.
 */
object Palettes {

    val skinTones: List<Swatch> = listOf(
        Swatch("porcelain", "Porcelain", argb(0xF6, 0xE0, 0xD2)),
        Swatch("ivory", "Ivory", argb(0xEE, 0xCF, 0xB8)),
        Swatch("sand", "Sand", argb(0xE3, 0xBA, 0x9B)),
        Swatch("honey", "Honey", argb(0xD3, 0xA2, 0x7C)),
        Swatch("amber", "Amber", argb(0xBE, 0x86, 0x5C)),
        Swatch("bronze", "Bronze", argb(0xA3, 0x6A, 0x45)),
        Swatch("chestnut", "Chestnut", argb(0x82, 0x51, 0x33)),
        Swatch("umber", "Umber", argb(0x63, 0x3C, 0x26)),
        Swatch("espresso", "Espresso", argb(0x46, 0x2A, 0x1B)),
    )

    val hairColors: List<Swatch> = listOf(
        Swatch("jet", "Jet black", argb(0x1B, 0x18, 0x1F)),
        Swatch("espresso", "Dark brown", argb(0x3B, 0x2A, 0x22)),
        Swatch("chestnut", "Chestnut", argb(0x6A, 0x42, 0x2C)),
        Swatch("caramel", "Caramel", argb(0x99, 0x68, 0x3B)),
        Swatch("honeyblond", "Honey blond", argb(0xC9, 0x9C, 0x5B)),
        Swatch("platinum", "Platinum", argb(0xE4, 0xDC, 0xC8)),
        Swatch("ash", "Ash grey", argb(0xA8, 0xA6, 0xAE)),
        Swatch("auburn", "Auburn", argb(0x8C, 0x37, 0x24)),
        // Stylised colours — never inferred from a photo, but selectable by hand.
        Swatch("starlight", "Starlight silver", argb(0xD6, 0xE2, 0xF5)),
        Swatch("nebula", "Nebula violet", argb(0x7B, 0x5C, 0xC4)),
        Swatch("cosmos", "Cosmos blue", argb(0x35, 0x62, 0xB8)),
        Swatch("ember", "Ember red", argb(0xC4, 0x3A, 0x3A)),
        Swatch("bloom", "Bloom pink", argb(0xE0, 0x8A, 0xB8)),
        Swatch("mint", "Mint", argb(0x6F, 0xC5, 0xA8)),
    )

    val eyeColors: List<Swatch> = listOf(
        Swatch("darkbrown", "Dark brown", argb(0x4A, 0x2F, 0x20)),
        Swatch("hazel", "Hazel", argb(0x8A, 0x5F, 0x2E)),
        Swatch("amber", "Amber", argb(0xB5, 0x7E, 0x25)),
        Swatch("olive", "Olive green", argb(0x6B, 0x7A, 0x3C)),
        Swatch("green", "Green", argb(0x3E, 0x8C, 0x5A)),
        Swatch("grey", "Storm grey", argb(0x77, 0x82, 0x8C)),
        Swatch("blue", "Blue", argb(0x3E, 0x6E, 0xA8)),
        Swatch("ice", "Ice blue", argb(0x9C, 0xC7, 0xE0)),
        Swatch("violet", "Violet", argb(0x7A, 0x4F, 0xB0)),
        Swatch("gold", "Starlit gold", argb(0xD8, 0xB1, 0x45)),
    )

    /**
     * Hair colours the analyzer is allowed to pick. The stylised half of the palette is excluded so
     * that a photo never produces mint-green hair — the player can still choose it by hand.
     */
    val naturalHairColors: List<Swatch> = hairColors.take(8)

    fun nearest(swatches: List<Swatch>, sampled: Int): Swatch =
        swatches.minBy { perceptualDistance(it.argb, sampled) }

    fun skin(id: String): Swatch = skinTones.first { it.id == id }
    fun hair(id: String): Swatch = hairColors.first { it.id == id }
    fun eye(id: String): Swatch = eyeColors.first { it.id == id }
}
