package com.deepuniverse.core.game

import com.deepuniverse.core.character.CharacterAppearance
import com.deepuniverse.core.character.Pronouns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class StoryEngineTest {

    private val player = CharacterAppearance(name = "Vesper", pronouns = Pronouns.THEY)
    private val newGame = GameState(player = player, characterCreated = true)

    private fun scene(
        id: String,
        loveInterestId: String = "lyra",
        requiredLevel: AffectionLevel = AffectionLevel.STRANGER,
        requiresFlags: Set<String> = emptySet(),
        beats: List<Beat>,
    ) = Scene(
        id = id,
        loveInterestId = loveInterestId,
        title = id,
        summary = "",
        requiredLevel = requiredLevel,
        requiresFlags = requiresFlags,
        beats = beats,
    )

    // ------------------------------------------------------------ playback

    @Test
    fun `a scene plays its beats in order and then ends`() {
        val s = scene(
            "a",
            beats = listOf(
                Beat.Narrate("The bay is dark."),
                Beat.Say(Speaker.Partner, "You're late."),
                Beat.Say(Speaker.Player, "I'm here."),
            ),
        )
        var playback = StoryEngine(listOf(s)).start(newGame, s)

        val first = playback.frame() as StoryFrame.Line
        assertTrue(first.isNarration)
        assertEquals("The bay is dark.", first.text)

        playback = playback.advance()
        val second = playback.frame() as StoryFrame.Line
        assertEquals("Lyra Vance", second.speakerName)
        assertFalse(second.isPlayer)

        playback = playback.advance()
        val third = playback.frame() as StoryFrame.Line
        assertEquals("Vesper", third.speakerName)
        assertTrue(third.isPlayer)

        playback = playback.advance()
        assertTrue(playback.isFinished)
        assertTrue(playback.frame() is StoryFrame.Ended)
    }

    @Test
    fun `advancing past the end is harmless`() {
        val s = scene("a", beats = listOf(Beat.Narrate("Only line.")))
        var playback = StoryEngine(listOf(s)).start(newGame, s)
        repeat(5) { playback = playback.advance() }
        assertTrue(playback.isFinished)
    }

    @Test
    fun `a finished scene is recorded in the save state`() {
        val s = scene("a", beats = listOf(Beat.Narrate("Line.")))
        val playback = StoryEngine(listOf(s)).start(newGame, s).advance()
        assertTrue(playback.state.completedScenes.contains("a"))
    }

    // ------------------------------------------------------------ choices

    @Test
    fun `a choice grants affection, sets its flag and shows its reply`() {
        val s = scene(
            "a",
            beats = listOf(
                Beat.Ask(
                    "She's grinning.",
                    listOf(
                        Choice("Smile back", affection = 3, reply = "\"Good answer.\"", setsFlag = "smiled"),
                        Choice("Say nothing", affection = 1),
                    ),
                ),
                Beat.Narrate("The bay hums."),
            ),
        )
        var playback = StoryEngine(listOf(s)).start(newGame, s)

        val question = playback.frame() as StoryFrame.Question
        assertEquals(listOf("Smile back", "Say nothing"), question.options)
        assertFalse(playback.canAdvance, "A question must not be skippable")

        playback = playback.choose(0)
        assertEquals(3, playback.state.affectionFor("lyra"))
        assertTrue(playback.state.flags.contains("smiled"))

        val reply = playback.frame() as StoryFrame.Line
        assertEquals("\"Good answer.\"", reply.text)
        assertEquals("Lyra Vance", reply.speakerName)

        playback = playback.advance()
        assertEquals("The bay hums.", (playback.frame() as StoryFrame.Line).text)
    }

    @Test
    fun `a choice with no reply moves straight on`() {
        val s = scene(
            "a",
            beats = listOf(
                Beat.Ask("Well?", listOf(Choice("Nod", affection = 2))),
                Beat.Narrate("After."),
            ),
        )
        val playback = StoryEngine(listOf(s)).start(newGame, s).choose(0)
        assertEquals("After.", (playback.frame() as StoryFrame.Line).text)
        assertEquals(2, playback.state.affectionFor("lyra"))
    }

    @Test
    fun `an out of range choice is ignored rather than crashing`() {
        val s = scene("a", beats = listOf(Beat.Ask("Well?", listOf(Choice("Nod")))))
        val playback = StoryEngine(listOf(s)).start(newGame, s)
        assertEquals(playback, playback.choose(7))
    }

    @Test
    fun `affection earned in a scene is reported at the end`() {
        val s = scene(
            "a",
            beats = listOf(
                Beat.Ask("One", listOf(Choice("Yes", affection = 4))),
                Beat.Ask("Two", listOf(Choice("Yes", affection = 3))),
            ),
        )
        var playback = StoryEngine(listOf(s)).start(newGame, s).choose(0).choose(0)
        if (!playback.isFinished) playback = playback.advance()
        assertEquals(StoryFrame.Ended(gainedAffection = 7), playback.frame())
    }

    @Test
    fun `affection never falls below zero`() {
        val s = scene("a", beats = listOf(Beat.Ask("Well?", listOf(Choice("Insult her", affection = -9)))))
        val playback = StoryEngine(listOf(s)).start(newGame, s).choose(0)
        assertEquals(0, playback.state.affectionFor("lyra"))
    }

    // ------------------------------------------------------------ gating

    @Test
    fun `scenes unlock as the bond deepens`() {
        val intro = scene("intro", beats = listOf(Beat.Narrate("Hi")))
        val later = scene(
            "later",
            requiredLevel = AffectionLevel.CLOSE,
            beats = listOf(Beat.Narrate("Later")),
        )
        val engine = StoryEngine(listOf(intro, later))

        assertEquals(listOf("intro"), engine.availableScenes(newGame, "lyra").map { it.id })
        assertEquals(listOf("later"), engine.lockedScenes(newGame, "lyra").map { it.id })

        val close = newGame.withAffection("lyra", AffectionLevel.CLOSE.minPoints)
        assertEquals(AffectionLevel.CLOSE, close.levelFor("lyra"))
        assertEquals(setOf("intro", "later"), engine.availableScenes(close, "lyra").map { it.id }.toSet())
        assertTrue(engine.lockedScenes(close, "lyra").isEmpty())
    }

    @Test
    fun `flag-gated scenes stay hidden until the flag is set`() {
        val secret = scene(
            "secret",
            requiresFlags = setOf("told_lyra_truth"),
            beats = listOf(Beat.Narrate("Secret")),
        )
        val engine = StoryEngine(listOf(secret))
        assertTrue(engine.availableScenes(newGame, "lyra").isEmpty())
        assertEquals(
            listOf("secret"),
            engine.availableScenes(newGame.withFlag("told_lyra_truth"), "lyra").map { it.id },
        )
    }

    @Test
    fun `the next scene skips ones already played`() {
        val a = scene("a", beats = listOf(Beat.Narrate("A")))
        val b = scene("b", beats = listOf(Beat.Narrate("B")))
        val engine = StoryEngine(listOf(a, b))
        assertEquals("a", engine.nextScene(newGame, "lyra")?.id)
        assertEquals("b", engine.nextScene(newGame.withCompletedScene("a"), "lyra")?.id)
        val allDone = newGame.withCompletedScene("a").withCompletedScene("b")
        assertEquals(null, engine.nextScene(allDone, "lyra"))
    }

    @Test
    fun `routes are independent of each other`() {
        val engine = StoryEngine()
        val state = newGame.withAffection("lyra", 30)
        assertEquals(AffectionLevel.CLOSE, state.levelFor("lyra"))
        assertEquals(AffectionLevel.STRANGER, state.levelFor("kaito"))
    }

    // ------------------------------------------------------------ text

    @Test
    fun `story text adapts to the player's name and pronouns`() {
        val line = "{name} said {they} would come, and {their} word is good. {They} {are} here."
        assertEquals(
            "Vesper said they would come, and their word is good. They are here.",
            TextTemplate.render(line, player),
        )
        assertEquals(
            "Rae said he would come, and his word is good. He is here.",
            TextTemplate.render(line, CharacterAppearance(name = "Rae", pronouns = Pronouns.HE)),
        )
        assertEquals(
            "Mei said she would come, and her word is good. She is here.",
            TextTemplate.render(line, CharacterAppearance(name = "Mei", pronouns = Pronouns.SHE)),
        )
    }

    @Test
    fun `player name is substituted into shipped dialogue`() {
        val engine = StoryEngine()
        val s = engine.sceneById("lyra_01_hangar") ?: fail("Missing shipped scene")
        var playback = engine.start(newGame, s)
        val texts = buildList {
            while (!playback.isFinished) {
                (playback.frame() as? StoryFrame.Line)?.let { add(it.text) }
                playback = if (playback.canAdvance) playback.advance() else playback.choose(0)
            }
        }
        assertTrue(texts.any { "Vesper" in it }, "Expected the player's name in the scene")
        assertTrue(texts.none { "{" in it }, "No unsubstituted template tokens should reach the UI")
    }

    // ------------------------------------------------------------ shipped content

    @Test
    fun `every shipped scene belongs to a real love interest and is playable`() {
        val engine = StoryEngine()
        for (scene in StoryLibrary.scenes) {
            Cast.byId(scene.loveInterestId) // throws if the id is a typo
            assertTrue(scene.beats.isNotEmpty(), "${scene.id} has no beats")
            for (beat in scene.beats) {
                if (beat is Beat.Ask) {
                    assertTrue(beat.options.isNotEmpty(), "${scene.id} has an empty choice")
                }
            }
            // Playing it through by always taking the first option must terminate.
            var playback = engine.start(newGame, scene)
            var guard = 0
            while (!playback.isFinished && guard++ < 500) {
                playback = if (playback.canAdvance) playback.advance() else playback.choose(0)
            }
            assertTrue(playback.isFinished, "${scene.id} did not finish")
        }
    }

    @Test
    fun `every cast member has an opening scene`() {
        val engine = StoryEngine()
        for (member in Cast.all) {
            assertTrue(
                engine.availableScenes(newGame, member.id).isNotEmpty(),
                "${member.name} has no scene a new player can reach",
            )
        }
    }

    @Test
    fun `scene ids are unique`() {
        val ids = StoryLibrary.scenes.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Duplicate scene ids: $ids")
    }
}
