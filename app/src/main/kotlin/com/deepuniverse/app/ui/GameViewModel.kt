package com.deepuniverse.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepuniverse.app.data.SaveStore
import com.deepuniverse.app.photo.PhotoCharacterGenerator
import com.deepuniverse.core.character.AppearanceParam
import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.HairStyle
import com.deepuniverse.core.character.Preset
import com.deepuniverse.core.character.PresentationStyle
import com.deepuniverse.core.character.Pronouns
import com.deepuniverse.core.game.GameState
import com.deepuniverse.core.game.Scene
import com.deepuniverse.core.game.ScenePlayback
import com.deepuniverse.core.game.StoryEngine
import com.deepuniverse.core.photo.AnalysisNote
import com.deepuniverse.core.photo.AnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which screen is on top. Kept as a small stack so Back always has somewhere sensible to go. */
sealed interface Screen {
    data object Loading : Screen
    data object Title : Screen
    data object Creator : Screen
    data object Home : Screen
    data class Route(val loveInterestId: String) : Screen
    data object Story : Screen
}

/** The photo half of the character creator. */
data class PhotoState(
    val isAnalyzing: Boolean = false,
    /** Set when an analysis succeeded, so the strength slider has something to blend towards. */
    val generated: CharacterAppearance? = null,
    /** The look the player had before applying the photo, so strength can blend back to it. */
    val beforePhoto: CharacterAppearance? = null,
    val strength: Float = 1f,
    val confidence: Float = 0f,
    val notes: List<AnalysisNote> = emptyList(),
    val error: String? = null,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val saveStore = SaveStore(application)
    private val generator = PhotoCharacterGenerator(application)
    private val storyEngine = StoryEngine()

    private val _screen = MutableStateFlow<Screen>(Screen.Loading)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    /** The character being edited in the creator; committed to [state] on save. */
    private val _draft = MutableStateFlow(CharacterAppearance())
    val draft: StateFlow<CharacterAppearance> = _draft.asStateFlow()

    private val _photo = MutableStateFlow(PhotoState())
    val photo: StateFlow<PhotoState> = _photo.asStateFlow()

    private val _playback = MutableStateFlow<ScenePlayback?>(null)
    val playback: StateFlow<ScenePlayback?> = _playback.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = saveStore.load()
            if (loaded != null) {
                _state.value = loaded
                _draft.value = loaded.player
            }
            _screen.value = Screen.Title
        }
    }

    // ------------------------------------------------------------------ navigation

    fun openCreator(fromExistingCharacter: Boolean) {
        _draft.value = if (fromExistingCharacter) _state.value.player else CharacterAppearance()
        _photo.value = PhotoState()
        _screen.value = Screen.Creator
    }

    fun openHome() {
        _screen.value = Screen.Home
    }

    fun openRoute(loveInterestId: String) {
        _screen.value = Screen.Route(loveInterestId)
    }

    /** Back handling. Returns false when there is nothing left to pop, so the Activity can finish. */
    fun goBack(): Boolean = when (_screen.value) {
        is Screen.Story -> {
            val route = _playback.value?.scene?.loveInterestId
            _playback.value = null
            _screen.value = route?.let { Screen.Route(it) } ?: Screen.Home
            true
        }

        is Screen.Route -> {
            _screen.value = Screen.Home
            true
        }

        is Screen.Creator -> {
            // Only leave the creator if there is already a character to go back to.
            if (_state.value.characterCreated) {
                _screen.value = Screen.Home
                true
            } else {
                _screen.value = Screen.Title
                true
            }
        }

        is Screen.Home -> {
            _screen.value = Screen.Title
            true
        }

        Screen.Title, Screen.Loading -> false
    }

    // ------------------------------------------------------------------ character editing

    fun setParam(param: AppearanceParam, value: Float) {
        _draft.update { it.with(param, value) }
        // A manual edit means the player has taken over from the photo; stop the strength slider
        // from being able to wipe the edit out from under them.
        if (_photo.value.generated != null && param.inferredFromPhoto) {
            _photo.update { it.copy(generated = null, beforePhoto = null) }
        }
    }

    fun resetParam(param: AppearanceParam) {
        _draft.update { it.reset(param) }
    }

    fun setName(name: String) = _draft.update { it.copy(name = name.take(20)) }

    fun setPronouns(pronouns: Pronouns) = _draft.update { it.copy(pronouns = pronouns) }

    fun setPresentation(style: PresentationStyle) = _draft.update { it.copy(presentation = style) }

    fun setHairStyle(style: HairStyle) = _draft.update { it.copy(hairStyle = style) }

    fun setSkinColor(argb: Int) = _draft.update { it.copy(skinColor = argb) }

    fun setHairColor(argb: Int) = _draft.update { it.copy(hairColor = argb) }

    fun setEyeColor(argb: Int) = _draft.update { it.copy(eyeColor = argb) }

    fun applyPreset(preset: Preset) {
        // Keep whatever name and pronouns the player already entered — a preset is a look, not an
        // identity, and silently resetting their name would be obnoxious.
        val current = _draft.value
        _draft.value = preset.appearance.copy(name = current.name, pronouns = current.pronouns)
        _photo.value = PhotoState()
    }

    fun saveCharacter() {
        val player = _draft.value
        _state.update { it.copy(player = player, characterCreated = true) }
        persist()
        _screen.value = Screen.Home
    }

    // ------------------------------------------------------------------ photo generation

    fun generateFromPhoto(uri: Uri) {
        if (_photo.value.isAnalyzing) return
        _photo.update { it.copy(isAnalyzing = true, error = null) }
        viewModelScope.launch {
            val before = _draft.value
            when (val result = generator.generate(uri, before)) {
                is AnalysisResult.Success -> {
                    _draft.value = result.appearance
                    _photo.value = PhotoState(
                        isAnalyzing = false,
                        generated = result.appearance,
                        beforePhoto = before,
                        strength = 1f,
                        confidence = result.confidence,
                        notes = result.notes,
                    )
                }

                is AnalysisResult.Failed -> {
                    _photo.value = PhotoState(
                        isAnalyzing = false,
                        error = result.reason.playerMessage,
                    )
                }
            }
        }
    }

    /** Blends between the pre-photo look and the generated one. */
    fun setPhotoStrength(strength: Float) {
        val photo = _photo.value
        val generated = photo.generated ?: return
        val before = photo.beforePhoto ?: return
        _photo.update { it.copy(strength = strength) }
        _draft.value = before.blendTowards(generated, strength)
    }

    fun dismissPhotoError() = _photo.update { it.copy(error = null) }

    // ------------------------------------------------------------------ story

    fun availableScenes(loveInterestId: String): List<Scene> =
        storyEngine.availableScenes(_state.value, loveInterestId)

    fun lockedScenes(loveInterestId: String): List<Scene> =
        storyEngine.lockedScenes(_state.value, loveInterestId)

    fun nextScene(loveInterestId: String): Scene? =
        storyEngine.nextScene(_state.value, loveInterestId)

    fun startScene(scene: Scene) {
        _playback.value = storyEngine.start(_state.value, scene)
        _screen.value = Screen.Story
    }

    fun advanceStory() {
        _playback.update { it?.advance() }
        commitPlaybackState()
    }

    fun chooseStoryOption(index: Int) {
        _playback.update { it?.choose(index) }
        commitPlaybackState()
    }

    /** Leaves the scene, keeping whatever affection was earned. */
    fun endScene() {
        val route = _playback.value?.scene?.loveInterestId
        _playback.value = null
        _screen.value = route?.let { Screen.Route(it) } ?: Screen.Home
    }

    /**
     * Copies the playback's game state back into the save.
     *
     * Done on every beat rather than at the end of the scene so that a player who is interrupted
     * mid-conversation keeps the affection they had already earned.
     */
    private fun commitPlaybackState() {
        val playbackState = _playback.value?.state ?: return
        _state.value = playbackState
        persist()
    }

    private fun persist() {
        val snapshot = _state.value
        viewModelScope.launch { saveStore.save(snapshot) }
    }

    override fun onCleared() {
        generator.close()
        super.onCleared()
    }
}
