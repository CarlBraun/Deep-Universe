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
import com.deepuniverse.core.character.Expression
import com.deepuniverse.core.game.CompanionResult
import com.deepuniverse.core.game.Companionship
import com.deepuniverse.core.game.activeBoost
import com.deepuniverse.core.game.GameState
import com.deepuniverse.core.game.Scene
import com.deepuniverse.core.game.Stamina
import com.deepuniverse.core.store.Boost
import com.deepuniverse.core.store.BoostKind
import com.deepuniverse.core.store.NoOpPurchaseGateway
import com.deepuniverse.core.store.PurchaseGateway
import com.deepuniverse.core.store.PurchaseResult
import com.deepuniverse.core.store.StoreCatalog
import com.deepuniverse.core.store.StoreOffer
import com.deepuniverse.core.store.SupportTier
import com.deepuniverse.core.game.ScenePlayback
import com.deepuniverse.core.game.StoryEngine
import com.deepuniverse.core.photo.AnalysisNote
import com.deepuniverse.core.photo.AnalysisResult
import com.deepuniverse.core.world.Direction
import com.deepuniverse.core.world.MoveResult
import com.deepuniverse.core.world.NpcSpawn
import com.deepuniverse.core.world.WorldEngine
import com.deepuniverse.core.world.WorldPosition
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

    /** The walk-around world. This is where the player spends most of their time. */
    data object Overworld : Screen

    /** The journal: the whole cast and how close you are to each of them. */
    data object Home : Screen

    /** Support the game, and spend Starlight on time-savers. */
    data object Store : Screen
    data class Route(val loveInterestId: String) : Screen
    data object Story : Screen
}

/** What the player just earned from a moment together, shown as a card and then dismissed. */
data class Reward(
    val loveInterestId: String,
    val line: String,
    val points: Int,
    val boosted: Boolean,
    val newRank: Int?,
    val unlockedExpression: Expression?,
)

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

    /**
     * Swapped for a real Play Billing implementation when the game ships. Until then no money can
     * change hands, and the stub is deliberately obvious so a debug build cannot be mistaken for a
     * live one.
     *
     * Deliberately *not* a constructor parameter with a default. Kotlin default arguments do not
     * generate a one-argument constructor, and the default ViewModel factory looks up
     * `GameViewModel(Application)` reflectively — so taking the gateway as a defaulted parameter
     * compiles perfectly and then throws "Cannot create an instance of class GameViewModel" the
     * instant the UI composes. Injecting it needs a real ViewModelProvider.Factory, not a default.
     */
    private val gateway: PurchaseGateway = NoOpPurchaseGateway()

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

    /** A passing line from whoever you just spoke to, when they have no new scene for you. */
    private val _overworldMessage = MutableStateFlow<String?>(null)
    val overworldMessage: StateFlow<String?> = _overworldMessage.asStateFlow()

    private val _reward = MutableStateFlow<Reward?>(null)
    val reward: StateFlow<Reward?> = _reward.asStateFlow()

    private val _storeMessage = MutableStateFlow<String?>(null)
    val storeMessage: StateFlow<String?> = _storeMessage.asStateFlow()

    private val _purchasing = MutableStateFlow(false)
    val purchasing: StateFlow<Boolean> = _purchasing.asStateFlow()

    /** Where the player is standing. Lives in [state] so it is part of the save. */
    val worldPosition: StateFlow<WorldPosition>
        get() = _worldPosition
    private val _worldPosition = MutableStateFlow(GameState().world)

    init {
        viewModelScope.launch {
            // Everything here is best-effort. The one outcome that must never happen is failing to
            // leave the loading screen: a player staring at a spinner cannot even start a new game,
            // which is indistinguishable from the app being broken.
            runCatching {
                val loaded = saveStore.load()
                if (loaded != null) {
                    _state.value = loaded
                    _draft.value = loaded.player
                    _worldPosition.value = loaded.world
                }
                refreshTimedState()
                // A purchase that settled while the app was gone must still be delivered; losing
                // what someone paid for is the one store bug there is no apologising for.
                for (restored in gateway.restorePurchases()) {
                    _state.update {
                        it.copy(
                            wallet = it.wallet.recordPurchase(
                                restored.tier,
                                restored.priceUnits,
                                monthKey(),
                            ),
                        )
                    }
                }
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

    fun openOverworld() {
        _overworldMessage.value = null
        _screen.value = Screen.Overworld
    }

    fun openRoute(loveInterestId: String) {
        _screen.value = Screen.Route(loveInterestId)
    }

    /** Back handling. Returns false when there is nothing left to pop, so the Activity can finish. */
    fun goBack(): Boolean = when (_screen.value) {
        is Screen.Story -> {
            // Scenes are entered from the world, so that is where finishing one returns you.
            _playback.value = null
            _screen.value = Screen.Overworld
            true
        }

        is Screen.Route -> {
            _screen.value = Screen.Home
            true
        }

        is Screen.Overworld -> {
            _screen.value = Screen.Title
            true
        }

        is Screen.Store -> {
            _storeMessage.value = null
            _screen.value = if (_state.value.characterCreated) Screen.Overworld else Screen.Title
            true
        }

        is Screen.Creator -> {
            // Only leave the creator if there is already a character to go back to.
            if (_state.value.characterCreated) {
                _screen.value = Screen.Overworld
                true
            } else {
                _screen.value = Screen.Title
                true
            }
        }

        is Screen.Home -> {
            _screen.value = if (_state.value.characterCreated) Screen.Overworld else Screen.Title
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
        _screen.value = Screen.Overworld
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

    // ------------------------------------------------------------------ overworld

    /** Whoever the player is currently facing, or null. Drives the TALK button. */
    fun facingNpc(): NpcSpawn? = WorldEngine.facingNpc(_worldPosition.value)

    /**
     * Takes one step (or turns on the spot).
     *
     * The save is only written when the player changes area rather than on every tile. Walking is
     * the most frequent thing in the game, and writing a file thirty times crossing a clearing
     * would be wasteful; an area boundary is a natural, cheap checkpoint.
     */
    fun move(direction: Direction) {
        if (_screen.value != Screen.Overworld) return
        _overworldMessage.value = null

        when (val result = WorldEngine.move(_worldPosition.value, direction)) {
            is MoveResult.Turned -> _worldPosition.value = result.position
            is MoveResult.Walked -> _worldPosition.value = result.position
            is MoveResult.Blocked -> Unit

            is MoveResult.Travelled -> {
                _worldPosition.value = result.position
                _state.update { it.copy(world = result.position) }
                persist()
            }
        }
    }

    /**
     * Talks to whoever the player is facing.
     *
     * If they have a scene ready, it starts. If not, they say something in passing rather than
     * nothing at all — walking up to someone should never feel like hitting a wall.
     */
    fun interact() {
        val npc = facingNpc() ?: return
        // Remember where the player was standing, so the story starts and ends in the same spot.
        _state.update { it.copy(world = _worldPosition.value) }

        val scene = storyEngine.nextScene(_state.value, npc.loveInterestId)
        if (scene != null) {
            startScene(scene)
        } else {
            // Out of written scenes with this person — spend a moment together instead, which is
            // what makes the game endless rather than finished.
            spendMomentWith(npc.loveInterestId)
        }
    }

    fun dismissOverworldMessage() {
        _overworldMessage.value = null
    }

    // ------------------------------------------------------------------ the endless loop

    /** Wall clock in epoch seconds. The only place the game reads real time. */
    private fun now(): Long = System.currentTimeMillis() / 1000L

    /**
     * Spends a moment with whoever the player is facing.
     *
     * This is the repeatable half of the game: once someone's written scenes are exhausted, time
     * spent together still moves the bond and still earns faces, forever.
     */
    fun spendMomentWith(loveInterestId: String) {
        when (val result = Companionship.spendMomentWith(_state.value, loveInterestId, now())) {
            is CompanionResult.Shared -> {
                _state.value = result.state
                _reward.value = Reward(
                    line = result.line,
                    points = result.pointsGained,
                    boosted = result.boosted,
                    newRank = result.newRank,
                    unlockedExpression = result.unlockedExpression,
                    loveInterestId = loveInterestId,
                )
                persist()
            }

            is CompanionResult.OutOfMoments -> {
                _overworldMessage.value = buildString {
                    append("You're out of moments for now.")
                    result.secondsUntilNext?.let {
                        append(" One more in about ${(it / 60) + 1} min.")
                    }
                }
            }
        }
    }

    /** Whether a Signal Boost is running right now, for the overworld's ×2 badge. */
    fun isBoosted(): Boolean =
        _state.value.activeBoost(BoostKind.AFFECTION_DOUBLE, now()) != null

    fun dismissReward() {
        _reward.value = null
    }

    /** Refreshes regenerated moments. Called when the app comes back to the foreground. */
    fun refreshTimedState() {
        val refreshed = Stamina.regenerated(_state.value.stamina, now())
        if (refreshed != _state.value.stamina) {
            _state.update { it.copy(stamina = refreshed) }
        }
    }

    // ------------------------------------------------------------------ store

    fun openStore() {
        _screen.value = Screen.Store
    }

    /** The month bucket the spend guard counts against. */
    private fun monthKey(): String {
        val calendar = java.util.Calendar.getInstance()
        return "%04d-%02d".format(calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1)
    }

    fun buy(tier: SupportTier) {
        if (_purchasing.value) return

        val priceUnits = NoOpPurchaseGateway.priceUnitsOf(tier)
        if (_state.value.wallet.wouldExceedSelfLimit(priceUnits, monthKey())) {
            _storeMessage.value =
                "That would go past the monthly limit you set. You can change it below."
            return
        }

        _purchasing.value = true
        viewModelScope.launch {
            when (val result = gateway.purchase(tier)) {
                is PurchaseResult.Success -> {
                    _state.update {
                        it.copy(
                            wallet = it.wallet.recordPurchase(
                                tier = result.tier,
                                priceUnits = result.priceUnits,
                                monthKey = monthKey(),
                            ),
                        )
                    }
                    _storeMessage.value =
                        "Thank you, genuinely. ${result.tier.totalStarlight} ${StoreCatalog.CURRENCY} added."
                    persist()
                }

                PurchaseResult.Cancelled -> Unit
                is PurchaseResult.Blocked -> _storeMessage.value = result.reason
                is PurchaseResult.Failed -> _storeMessage.value =
                    "That didn't go through, and you have not been charged. ${result.reason}"
            }
            _purchasing.value = false
        }
    }

    /** Spends Starlight on a time-saver. Never on story content. */
    fun redeem(offer: StoreOffer) {
        val wallet = _state.value.wallet
        if (!wallet.canAfford(offer.cost)) {
            _storeMessage.value = "Not quite enough ${StoreCatalog.CURRENCY} for that yet."
            return
        }

        val moment = now()
        _state.update { state ->
            val spent = state.copy(wallet = state.wallet.spend(offer.cost))
            when (offer.id) {
                "refill_moments" -> spent.copy(stamina = spent.stamina.refilled(moment))
                "boost_affection" -> spent.withBoost(
                    Boost(BoostKind.AFFECTION_DOUBLE, moment + 3600),
                    moment,
                )
                "boost_affection_day" -> spent.withBoost(
                    Boost(BoostKind.AFFECTION_DOUBLE, moment + 86_400),
                    moment,
                )
                else -> spent
            }
        }
        _storeMessage.value = "${offer.title} — done."
        persist()
    }

    /** Lets the player cap their own monthly spending. Null clears it. */
    fun setMonthlyLimit(limitUnits: Int?) {
        _state.update { it.copy(wallet = it.wallet.copy(selfImposedMonthlyLimit = limitUnits)) }
        _storeMessage.value = if (limitUnits == null) {
            "Monthly limit removed."
        } else {
            "Monthly limit set."
        }
        persist()
    }

    fun dismissStoreMessage() {
        _storeMessage.value = null
    }

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
        _playback.value = null
        persist()
        _screen.value = Screen.Overworld
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
