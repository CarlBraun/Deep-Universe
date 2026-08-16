package com.deepuniverse.app.ui.creator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepuniverse.app.ui.PhotoState
import com.deepuniverse.app.ui.avatar.AvatarPortrait
import com.deepuniverse.app.ui.theme.DriftGlow
import com.deepuniverse.app.ui.theme.MutedStar
import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.HairStyle
import com.deepuniverse.core.character.Palettes
import com.deepuniverse.core.character.ParamGroup
import com.deepuniverse.core.character.Preset
import com.deepuniverse.core.character.Presets
import com.deepuniverse.core.character.PresentationStyle
import com.deepuniverse.core.character.Pronouns
import com.deepuniverse.core.character.Swatch
import kotlin.math.roundToInt

/**
 * The character creator.
 *
 * Three routes into a character sit side by side and feed the same editor: a preset, a photo, or
 * pure manual editing. The photo is presented as one option among three rather than as the headline
 * flow, and every parameter it sets stays draggable underneath — the generated character is a
 * starting point, never a result the player is stuck with.
 */
@Composable
fun CharacterCreatorScreen(
    appearance: CharacterAppearance,
    photo: PhotoState,
    onParamChange: (AppearanceParam, Float) -> Unit,
    onParamReset: (AppearanceParam) -> Unit,
    onName: (String) -> Unit,
    onPronouns: (Pronouns) -> Unit,
    onPresentation: (PresentationStyle) -> Unit,
    onHairStyle: (HairStyle) -> Unit,
    onSkinColor: (Int) -> Unit,
    onHairColor: (Int) -> Unit,
    onEyeColor: (Int) -> Unit,
    onPreset: (Preset) -> Unit,
    onPhotoUri: (android.net.Uri) -> Unit,
    onPhotoStrength: (Float) -> Unit,
    onDismissPhotoError: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var cameraDenied by remember { mutableIntStateOf(0) }
    val photoSources = rememberPhotoSources(
        onPhoto = { uri ->
            onPhotoUri(uri)
        },
        onCameraDenied = { cameraDenied++ },
    )

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember { listOf("Start") + ParamGroup.entries.map { it.label } + listOf("Colour") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // ---- live portrait -------------------------------------------------
        Box(
            Modifier
                .fillMaxWidth()
                .height(280.dp),
            contentAlignment = Alignment.Center,
        ) {
            AvatarPortrait(
                appearance = appearance,
                modifier = Modifier.fillMaxSize(),
            )
            if (photo.isAnalyzing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = DriftGlow)
                        Spacer(Modifier.height(12.dp))
                        Text("Reading your features…", color = DriftGlow)
                        Text(
                            "On this device — the photo isn't going anywhere.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedStar,
                        )
                    }
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> StartTab(
                    appearance = appearance,
                    photo = photo,
                    cameraDeniedCount = cameraDenied,
                    onName = onName,
                    onPronouns = onPronouns,
                    onPresentation = onPresentation,
                    onPreset = onPreset,
                    onPickGallery = { photoSources.chooseFromGallery() },
                    onTakePhoto = { photoSources.takePhoto() },
                    hasCamera = photoSources.hasCamera,
                    onPhotoStrength = onPhotoStrength,
                    onDismissPhotoError = {
                        clearCapturedPhotos(context)
                        onDismissPhotoError()
                    },
                )

                in 1..ParamGroup.entries.size -> SliderTab(
                    group = ParamGroup.entries[tab - 1],
                    appearance = appearance,
                    photoTouched = photo.generated != null,
                    onParamChange = onParamChange,
                    onParamReset = onParamReset,
                )

                else -> ColourTab(
                    appearance = appearance,
                    onHairStyle = onHairStyle,
                    onSkinColor = onSkinColor,
                    onHairColor = onHairColor,
                    onEyeColor = onEyeColor,
                )
            }
        }

        Button(
            onClick = {
                clearCapturedPhotos(context)
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(52.dp),
            enabled = appearance.name.isNotBlank(),
        ) {
            Text("Enter the Deep Universe", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ------------------------------------------------------------------ tabs

@Composable
private fun StartTab(
    appearance: CharacterAppearance,
    photo: PhotoState,
    cameraDeniedCount: Int,
    onName: (String) -> Unit,
    onPronouns: (Pronouns) -> Unit,
    onPresentation: (PresentationStyle) -> Unit,
    onPreset: (Preset) -> Unit,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    hasCamera: Boolean,
    onPhotoStrength: (Float) -> Unit,
    onDismissPhotoError: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = appearance.name,
                onValueChange = onName,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Column {
                SectionLabel("Pronouns")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pronouns.entries.forEach { option ->
                        FilterChip(
                            selected = appearance.pronouns == option,
                            onClick = { onPronouns(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
                Text(
                    "Every character in the game can be romanced, whichever you choose.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedStar,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        item {
            Column {
                SectionLabel("Presentation")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresentationStyle.entries.forEach { option ->
                        FilterChip(
                            selected = appearance.presentation == option,
                            onClick = { onPresentation(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        }

        item { PhotoCard(
            photo = photo,
            cameraDeniedCount = cameraDeniedCount,
            hasCamera = hasCamera,
            onPickGallery = onPickGallery,
            onTakePhoto = onTakePhoto,
            onPhotoStrength = onPhotoStrength,
            onDismissPhotoError = onDismissPhotoError,
        ) }

        item {
            Column {
                SectionLabel("Or start from a look")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Presets.all.forEach { preset ->
                        PresetCard(preset = preset, onClick = { onPreset(preset) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    photo: PhotoState,
    cameraDeniedCount: Int,
    hasCamera: Boolean,
    onPickGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPhotoStrength: (Float) -> Unit,
    onDismissPhotoError: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Build it from a photo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "We read your face shape, features and colouring, then set the sliders for you. " +
                    "The photo is analysed on this device and is never uploaded or saved.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedStar,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onPickGallery, enabled = !photo.isAnalyzing) {
                    Text("Choose a photo")
                }
                if (hasCamera) {
                    OutlinedButton(onClick = onTakePhoto, enabled = !photo.isAnalyzing) {
                        Text("Take one")
                    }
                }
            }

            if (cameraDeniedCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No camera access — you can still choose an existing photo, or design your " +
                        "character by hand.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedStar,
                )
            }

            AnimatedVisibility(photo.error != null) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        photo.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    TextButton(onClick = onDismissPhotoError) { Text("Got it") }
                }
            }

            AnimatedVisibility(photo.generated != null) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "How much of the photo to use",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = photo.strength,
                        onValueChange = onPhotoStrength,
                        valueRange = 0f..1f,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Just me", style = MaterialTheme.typography.labelSmall, color = MutedStar)
                        Text(
                            "Match: ${(photo.confidence * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedStar,
                        )
                        Text("Full photo", style = MaterialTheme.typography.labelSmall, color = MutedStar)
                    }
                    photo.notes.forEach { note ->
                        Text(
                            "• ${note.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedStar,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetCard(preset: Preset, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(6.dp),
    ) {
        AvatarPortrait(
            appearance = preset.appearance,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Text(preset.label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SliderTab(
    group: ParamGroup,
    appearance: CharacterAppearance,
    photoTouched: Boolean,
    onParamChange: (AppearanceParam, Float) -> Unit,
    onParamReset: (AppearanceParam) -> Unit,
) {
    val params = remember(group) { AppearanceParam.inGroup(group) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        if (photoTouched) {
            item {
                Text(
                    "Set from your photo — drag anything you want to change.",
                    style = MaterialTheme.typography.labelSmall,
                    color = DriftGlow,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        items(params, key = { it.name }) { param ->
            ParamSlider(
                param = param,
                value = appearance[param],
                onChange = { onParamChange(param, it) },
                onReset = { onParamReset(param) },
            )
        }
    }
}

@Composable
private fun ParamSlider(
    param: AppearanceParam,
    value: Float,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(param.label, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onReset) {
                Text("Reset", style = MaterialTheme.typography.labelSmall)
            }
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(param.lowLabel, style = MaterialTheme.typography.labelSmall, color = MutedStar)
            Text(param.highLabel, style = MaterialTheme.typography.labelSmall, color = MutedStar)
        }
    }
}

@Composable
private fun ColourTab(
    appearance: CharacterAppearance,
    onHairStyle: (HairStyle) -> Unit,
    onSkinColor: (Int) -> Unit,
    onHairColor: (Int) -> Unit,
    onEyeColor: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column {
                SectionLabel("Skin")
                SwatchRow(
                    swatches = Palettes.skinTones,
                    selected = appearance.skinColor,
                    onSelect = onSkinColor,
                )
            }
        }
        item {
            Column {
                SectionLabel("Hair colour")
                SwatchRow(
                    swatches = Palettes.hairColors,
                    selected = appearance.hairColor,
                    onSelect = onHairColor,
                )
            }
        }
        item {
            Column {
                SectionLabel("Eyes")
                SwatchRow(
                    swatches = Palettes.eyeColors,
                    selected = appearance.eyeColor,
                    onSelect = onEyeColor,
                )
            }
        }
        item {
            Column {
                SectionLabel("Hair style")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HairStyle.entries.forEach { style ->
                        FilterChip(
                            selected = appearance.hairStyle == style,
                            onClick = { onHairStyle(style) },
                            label = { Text(style.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchRow(swatches: List<Swatch>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        swatches.forEach { swatch ->
            val isSelected = swatch.argb == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(swatch.argb))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) DriftGlow else Color.White.copy(alpha = 0.2f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(swatch.argb) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
