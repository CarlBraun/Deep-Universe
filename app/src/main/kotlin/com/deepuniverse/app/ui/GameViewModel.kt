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
import com.deepuniverse.core.puzzle.CookingGame
import com.deepuniverse.core.puzzle.Minesweeper
import com.deepuniverse.core.puzzle.PuzzleInvite
import com.deepuniverse.core.puzzle.PuzzleKind
import com.deepuniverse.core.puzzle.PuzzleOutcome
import com.deepuniverse.core.puzzle.Puzzles
import com.deepuniverse.core.puzzle.SquirrelHunt
import com.deepuniverse.core.store.AdGateway
import com.deepuniverse.core.store.AdResult
import com.deepuniverse.core.store.NoOpAdGateway
import com.deepuniverse.core.store.PuzzleRetry
import com.deepuniverse.core.photo.AnalysisNote
import com.deepuniverse.core.photo.AnalysisResult
import com.deepuniverse.core.world.Direction
import com.deepuniverse.core.world.MoveResult
import com.deepuniverse.core.world.NpcSpawn
import com.deepuniverse.core.world.WorldEngine
import com.deepuniverse.core.world.WorldPosition
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How long one tile of an auto-walk takes.
 *
 * Matched to the overworld's step animation, so the sprite arrives on a tile exactly as it starts
 * moving to the next one and a long walk reads as one continuous stride.
 */
private const val WALK_STEP_MILLIS = 150L

/** Which screen is on top. Kept as a small stack so Back always has somewhere sensible to go. */
sealed interface Screen {
    data object Loading : Screen
    data object Title : Screen
    data object Creator : Screen

    /** The walk-around world. This is where the player spends most of their time. */
    data object Overworld : Screen

    /** The journal: the whole cast and how close you are to each of them. */
    data object Home : Screen

    /** Support the game, and spend Stars on time-savers. */
    data object Store : Screen

    /** A minigame in progress with somebody. */
    data object Puzzle : Screen
    data class Route(val loveInterestId: String) : Screen
    data object Story : Screen
}

/**
 * A minigame in progress.
 *
 * One subclass per game, each holding the pure state machine from `core`. The screen switches on
 * this and does nothing else — every rule lives in the game object it wraps.
 */
sealed interface PuzzleState {
    val kind: PuzzleKind
    val loveInterestId: String
    val outcome: PuzzleOutcome

    data class Sweep(
        override val loveInterestId: String,
        val board: Minesweeper,
        /** True while the player has flag mode latched on. */
        val flagging: Boolean = false,
    ) : PuzzleState {
        override val kind = PuzzleKind.MINESWEEPER
        override val outcome get() = board.outcome
    }

    data class Hunt(
        override val loveInterestId: String,
        val hunt: SquirrelHunt,
    ) : PuzzleState {
        override val kind = PuzzleKind.SQUIRREL_HUNT
        override val outcome get() = hunt.outcome
    }

    data class Cook(
        override val loveInterestId: String,
        val game: CookingGame,
    ) : PuzzleState {
        override val kind = PuzzleKind.COOKING
        override val outcome get() = game.outcome
    }
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

    /** Swapped for a real ad network when the game ships. The stub shows nothing and blocks nobody. */
    private val ads: AdGateway = NoOpAdGateway()

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

    private val _puzzle = MutableStateFlow<PuzzleState?>(null)
    val puzzle: StateFlow<PuzzleState?> = _puzzle.asStateFlow()

    private val _adReady = MutableStateFlow(false)
    val adReady: StateFlow<Boolean> = _adReady.asStateFlow()

    /** Where the player is standing. Lives in [state] so it is part of the save. */
    val worldPosition: StateFlow<WorldPosition>
        get() = _worldPosition
    private val _worldPosition = MutableStateFlow(GameState().world)

    /** The walk-to-a-tapped-tile coroutine, so the stick or a new tap can cancel it. */
    private var walkJob: Job? = null

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

        is Screen.Puzzle -> {
            _puzzle.value = null
            _screen.value = Screen.Overworld
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
     * Takes one step in [direction], from the stick.
     *
     * Any auto-walk in progress is abandoned: the stick is the player taking the wheel back, and
     * having the character continue towards a tile they tapped ten seconds ago would be the single
     * most infuriating thing the controls could do.
     */
    fun move(direction: Direction) {
        if (_screen.value != Screen.Overworld) return
        walkJob?.cancel()
        walkJob = null
        _overworldMessage.value = null
        step(direction)
    }

    /**
     * One tile of movement — turning *and* stepping.
     *
     * The engine treats a turn as a whole move, the way the classic games do, which is what lets you
     * face someone standing beside you without walking into them. On a touch stick that rule reads
     * as lag: you lean towards a door, the character pivots, and nothing else happens until the next
     * repeat. So the turn is absorbed here, at the call site, rather than changed in the engine —
     * pointing at a door walks towards it immediately, and the engine's semantics (which the world
     * tests depend on) are untouched.
     *
     * The save is only written when the player changes area rather than on every tile. Walking is
     * the most frequent thing in the game, and writing a file thirty times crossing a clearing
     * would be wasteful; an area boundary is a natural, cheap checkpoint.
     *
     * @return true when the player actually moved, so an auto-walk can tell it has stalled.
     */
    private fun step(direction: Direction): Boolean {
        val flags = _state.value.flags
        val before = _worldPosition.value
        var result = WorldEngine.move(before, direction, flags)
        var turnedFirst = false
        if (result is MoveResult.Turned) {
            turnedFirst = true
            result = WorldEngine.move(result.position, direction, flags)
        }

        return when (val outcome = result) {
            is MoveResult.Turned -> {
                _worldPosition.value = outcome.position
                false
            }

            is MoveResult.Walked -> {
                _worldPosition.value = outcome.position
                true
            }

            is MoveResult.Blocked -> {
                // Standing on a closed exit should explain itself rather than feel like a wall.
                _worldPosition.value = outcome.position
                val steppedOnto = outcome.position.x != before.x || outcome.position.y != before.y
                // Bumping a wall on the very press that turned you is not worth a line of dialogue —
                // you asked to look that way and you are now looking that way. A locked door is,
                // and a locked door is the case where the block still moved you onto a tile.
                if ((steppedOnto || !turnedFirst) && outcome.blockedBy.length > 12) {
                    _overworldMessage.value = "You are looking at ${outcome.blockedBy}."
                }
                steppedOnto
            }

            is MoveResult.Travelled -> {
                _worldPosition.value = outcome.position
                _state.update { it.copy(world = outcome.position) }
                persist()
                // Some places have something to say the first time you walk into them. This is how
                // the ship in the bracken finds the player rather than the other way round.
                storyEngine.sceneTriggeredBy(outcome.position.areaId, _state.value)
                    ?.let { startScene(it) }
                true
            }
        }
    }

    /**
     * Walks to a tapped tile, one step at a time.
     *
     * Aiming a stick at a specific doorway on a phone is genuinely hard, so the primary way to get
     * anywhere is to point at it. Tapping a person means "go and talk to them" — the route ends
     * beside them, facing them, with the talk button already lit.
     */
    fun walkTo(x: Int, y: Int) {
        if (_screen.value != Screen.Overworld) return
        val route = WorldEngine.path(_worldPosition.value, x, y, _state.value.flags)
        if (route == null) {
            _overworldMessage.value = "There's no way through to there."
            return
        }

        walkJob?.cancel()
        _overworldMessage.value = null
        if (route.isEmpty()) return

        walkJob = viewModelScope.launch {
            route.forEachIndexed { index, direction ->
                // Walking into a door mid-route starts a scene; the rest of the path belongs to the
                // clearing we just left, so it is abandoned rather than played out underneath it.
                if (_screen.value != Screen.Overworld) return@launch
                val moved = step(direction)
                // The last step of a route to a person is a turn, not a move, and is meant to fail
                // to advance. Anywhere else, not moving means the route is stale — stop rather than
                // grind into whatever appeared in the way.
                if (!moved && index != route.lastIndex) return@launch
                delay(WALK_STEP_MILLIS)
            }
            walkJob = null
        }
    }

    /** Stops an auto-walk — used when a scene, a puzzle or a menu takes over. */
    fun stopWalking() {
        walkJob?.cancel()
        walkJob = null
    }

    /**
     * Talks to whoever the player is facing.
     *
     * If they have a scene ready, it starts. If not, they say something in passing rather than
     * nothing at all — walking up to someone should never feel like hitting a wall.
     */
    fun interact() {
        val npc = facingNpc() ?: return
        stopWalking()
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

    // ------------------------------------------------------------------ puzzles

    /** The game this character wants to play, so the world button can name it. */
    fun puzzleKindFor(loveInterestId: String): PuzzleKind = PuzzleInvite.kindFor(loveInterestId)

    /**
     * Starts a minigame with whoever the player is facing.
     *
     * The moment is spent up front. A loss therefore costs something, which is what gives the retry
     * offer any meaning — but it costs a moment, never Stars, and never automatically.
     */
    fun startPuzzle(loveInterestId: String) {
        stopWalking()
        val moment = now()
        if (!PuzzleInvite.canStart(_state.value, moment)) {
            _overworldMessage.value = "You're out of moments. Come back in a few minutes."
            return
        }

        _state.value = PuzzleInvite.start(_state.value, moment)
        _puzzle.value = freshPuzzle(loveInterestId, moment.toInt())
        _screen.value = Screen.Puzzle
        persist()

        viewModelScope.launch { _adReady.value = ads.isRewardedAdReady() }
    }

    private fun freshPuzzle(loveInterestId: String, seed: Int): PuzzleState {
        val rank = _state.value.rankFor(loveInterestId)
        return when (PuzzleInvite.kindFor(loveInterestId)) {
            PuzzleKind.MINESWEEPER -> PuzzleState.Sweep(
                loveInterestId = loveInterestId,
                board = Minesweeper.new(
                    width = 8,
                    height = 8,
                    mines = Puzzles.mineCountFor(rank),
                    seed = seed,
                ),
            )

            PuzzleKind.SQUIRREL_HUNT -> PuzzleState.Hunt(
                loveInterestId = loveInterestId,
                hunt = SquirrelHunt.new(width = 6, height = 6, attempts = 7, seed = seed),
            )

            PuzzleKind.COOKING -> PuzzleState.Cook(
                loveInterestId = loveInterestId,
                game = CookingGame.new(rank = rank, seed = seed),
            )
        }
    }

    fun revealCell(x: Int, y: Int) {
        val current = _puzzle.value as? PuzzleState.Sweep ?: return
        updatePuzzle(current.copy(board = current.board.reveal(x, y)))
    }

    fun flagCell(x: Int, y: Int) {
        val current = _puzzle.value as? PuzzleState.Sweep ?: return
        updatePuzzle(current.copy(board = current.board.toggleFlag(x, y)))
    }

    fun searchBush(x: Int, y: Int) {
        val current = _puzzle.value as? PuzzleState.Hunt ?: return
        updatePuzzle(current.copy(hunt = current.hunt.search(x, y)))
    }

    fun tickPot(deltaMillis: Long, holding: Boolean) {
        val current = _puzzle.value as? PuzzleState.Cook ?: return
        updatePuzzle(current.copy(game = current.game.step(deltaMillis, holding)))
    }

    /** Applies a puzzle's new state, awarding the win the moment it happens. */
    private fun updatePuzzle(next: PuzzleState) {
        val was = _puzzle.value?.outcome
        _puzzle.value = next
        if (was == PuzzleOutcome.IN_PROGRESS && next.outcome == PuzzleOutcome.WON) {
            val win = PuzzleInvite.win(_state.value, next.loveInterestId, next.kind, now())
            _state.value = win.state
            _reward.value = Reward(
                loveInterestId = next.loveInterestId,
                line = "You did that together.",
                points = win.points,
                boosted = isBoosted(),
                newRank = win.newRank,
                unlockedExpression = win.unlockedExpression,
            )
            persist()
        }
    }

    /** Pays Stars for an immediate second go. Waiting remains free. */
    fun retryPuzzleWithStars() {
        val current = _puzzle.value ?: return
        if (!_state.value.wallet.canAfford(PuzzleRetry.STAR_COST)) return
        _state.update { it.copy(wallet = it.wallet.spend(PuzzleRetry.STAR_COST)) }
        _puzzle.value = freshPuzzle(current.loveInterestId, now().toInt() + 1)
        persist()
    }

    /** Watches an ad for an immediate second go. A skipped or missing ad costs nothing. */
    fun retryPuzzleWithAd() {
        val current = _puzzle.value ?: return
        viewModelScope.launch {
            when (ads.showRewardedAd()) {
                AdResult.Watched -> _puzzle.value = freshPuzzle(current.loveInterestId, now().toInt() + 2)
                AdResult.Skipped -> Unit
                is AdResult.Unavailable -> {
                    _adReady.value = false
                    _overworldMessage.value = "No ad available right now."
                }
            }
        }
    }

    fun leavePuzzle() {
        _puzzle.value = null
        _screen.value = Screen.Overworld
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
                        "Thank you, genuinely. ${result.tier.totalStars} ${StoreCatalog.CURRENCY} added."
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
