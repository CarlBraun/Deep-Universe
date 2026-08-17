package com.deepuniverse.core.game

/** What the UI should draw right now. */
sealed interface StoryFrame {

    data class Line(
        val speakerName: String?,
        val text: String,
        val isPlayer: Boolean,
        val isNarration: Boolean,
    ) : StoryFrame

    data class Question(val prompt: String, val options: List<String>) : StoryFrame

    /** The scene is over. [gainedAffection] is the total earned in this playthrough. */
    data class Ended(val gainedAffection: Int) : StoryFrame
}

/**
 * An in-progress scene.
 *
 * Immutable: every interaction returns a new playback. That keeps the engine trivially testable and
 * makes it a natural fit for Compose, which re-renders from a single state value — and it means a
 * mid-scene save is just serialising [state].
 */
data class ScenePlayback internal constructor(
    val scene: Scene,
    val state: GameState,
    private val index: Int,
    private val pendingReply: String?,
    private val gained: Int,
    val isFinished: Boolean,
) {
    private val loveInterest: LoveInterest? get() = scene.loveInterestId?.let { Cast.byId(it) }

    /** The frame to display. */
    fun frame(): StoryFrame {
        if (isFinished) return StoryFrame.Ended(gained)
        pendingReply?.let {
            return StoryFrame.Line(
                speakerName = loveInterest?.name,
                text = TextTemplate.render(it, state.player),
                isPlayer = false,
                isNarration = false,
            )
        }
        return when (val beat = scene.beats[index]) {
            is Beat.Narrate -> StoryFrame.Line(
                speakerName = null,
                text = TextTemplate.render(beat.text, state.player),
                isPlayer = false,
                isNarration = true,
            )

            is Beat.Say -> StoryFrame.Line(
                speakerName = speakerName(beat.speaker),
                text = TextTemplate.render(beat.text, state.player),
                isPlayer = beat.speaker is Speaker.Player,
                isNarration = false,
            )

            is Beat.Ask -> StoryFrame.Question(
                prompt = TextTemplate.render(beat.prompt, state.player),
                options = beat.options.map { TextTemplate.render(it.text, state.player) },
            )
        }
    }

    /** True when tapping the dialogue box should move the scene on. */
    val canAdvance: Boolean
        get() = !isFinished && (pendingReply != null || scene.beats[index] !is Beat.Ask)

    /** Advances past the current line. No-op at a choice point — call [choose] instead. */
    fun advance(): ScenePlayback {
        if (!canAdvance) return this
        if (pendingReply != null) return step(copy(pendingReply = null))
        return step(this)
    }

    /**
     * Picks option [optionIndex] at the current choice point, applying its affection and flag.
     * No-op if the current beat is not a question.
     */
    fun choose(optionIndex: Int): ScenePlayback {
        val beat = scene.beats.getOrNull(index) as? Beat.Ask ?: return this
        val choice = beat.options.getOrNull(optionIndex) ?: return this

        var nextState = state
        val routeOwner = scene.loveInterestId
        if (choice.affection != 0 && routeOwner != null) {
            nextState = nextState.withAffection(routeOwner, choice.affection)
        }
        choice.setsFlag?.let { nextState = nextState.withFlag(it) }

        val afterChoice = copy(
            state = nextState,
            gained = gained + choice.affection,
            pendingReply = choice.reply,
        )
        // With no reply to show, the choice itself consumes the beat.
        return if (choice.reply == null) step(afterChoice) else afterChoice
    }

    /** Moves to the next beat, ending the scene when the beats run out. */
    private fun step(from: ScenePlayback): ScenePlayback {
        val next = from.index + 1
        return if (next >= scene.beats.size) {
            from.copy(
                index = scene.beats.lastIndex,
                isFinished = true,
                state = from.state.withCompletedScene(scene.id),
            )
        } else {
            from.copy(index = next)
        }
    }

    private fun speakerName(speaker: Speaker): String? = when (speaker) {
        Speaker.Partner -> loveInterest?.name
        Speaker.Player -> state.player.name
        Speaker.Narrator -> null
        is Speaker.Other -> speaker.name
    }
}

/**
 * Decides which scenes are open to the player and starts them.
 *
 * Scenes unlock on bond tier rather than on a fixed order, so a player who spends every choice on
 * one character moves up that route quickly while the rest stay at their introduction — the same
 * structure Love and Deepspace-style games use to make routes feel chosen rather than queued.
 */
class StoryEngine(private val scenes: List<Scene> = StoryLibrary.scenes) {

    fun scenesFor(loveInterestId: String): List<Scene> =
        scenes.filter { it.loveInterestId == loveInterestId }

    /**
     * A scene that fires on walking into [areaId], if one is due.
     *
     * This is how the world tells its own story: the ship is found by a player on their way to the
     * beach, not by one who went looking for a menu entry.
     */
    fun sceneTriggeredBy(areaId: String, state: GameState): Scene? = scenes.firstOrNull {
        it.triggersInArea == areaId &&
            it.id !in state.completedScenes &&
            state.flags.containsAll(it.requiresFlags)
    }

    fun sceneById(id: String): Scene? = scenes.firstOrNull { it.id == id }

    /** Scenes on this route the player can play right now, unplayed ones first. */
    fun availableScenes(state: GameState, loveInterestId: String): List<Scene> {
        val level = state.levelFor(loveInterestId)
        return scenesFor(loveInterestId)
            .filter { it.requiredLevel.minPoints <= level.minPoints }
            .filter { state.flags.containsAll(it.requiresFlags) }
            .sortedBy { it.id in state.completedScenes }
    }

    /** The next unplayed scene on this route, or null when the route is exhausted for now. */
    fun nextScene(state: GameState, loveInterestId: String): Scene? =
        availableScenes(state, loveInterestId).firstOrNull { it.id !in state.completedScenes }

    /** Scenes still locked, with the tier that would open them — shown greyed out in the UI. */
    fun lockedScenes(state: GameState, loveInterestId: String): List<Scene> {
        val level = state.levelFor(loveInterestId)
        return scenesFor(loveInterestId).filter {
            it.requiredLevel.minPoints > level.minPoints || !state.flags.containsAll(it.requiresFlags)
        }
    }

    fun start(state: GameState, scene: Scene): ScenePlayback = ScenePlayback(
        scene = scene,
        state = state,
        index = 0,
        pendingReply = null,
        gained = 0,
        isFinished = false,
    )
}
