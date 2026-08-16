package com.deepuniverse.core.game

/**
 * The shipped story content.
 *
 * Scenes are plain data, so adding to the game is adding to this list — no engine change, no UI
 * change. Keeping content out of the engine is what lets `StoryEngineTest` exercise progression
 * against tiny synthetic scenes instead of the real script.
 */
object StoryLibrary {

    // ------------------------------------------------------------------ Lyra

    private val lyraIntro = Scene(
        id = "lyra_01_hangar",
        loveInterestId = "lyra",
        title = "Hard Dock",
        summary = "Someone lands an interceptor in a bay that was closed for maintenance.",
        beats = listOf(
            Beat.Narrate(
                "Bay Four is supposed to be sealed. The alarm says otherwise, and so does the " +
                    "interceptor currently cooling on the deck, ticking like it resents you.",
            ),
            Beat.Say(Speaker.Partner, "Don't. Whatever you're about to write in that report — don't."),
            Beat.Narrate(
                "She drops out of the cockpit without using the ladder, and lands like the deck " +
                    "owes her money.",
            ),
            Beat.Say(Speaker.Partner, "You're the new resonance pilot. {name}, right? I've heard things."),
            Beat.Ask(
                prompt = "She's grinning. It's not entirely friendly.",
                options = listOf(
                    Choice(
                        text = "\"Good things, I hope.\"",
                        affection = 3,
                        reply = "\"Interesting things. Better than good.\"",
                    ),
                    Choice(
                        text = "\"Bay Four is closed, Lieutenant.\"",
                        affection = 1,
                        reply = "\"Bay Four is closed. I'm not in Bay Four, I'm above it. Technically.\"",
                    ),
                    Choice(
                        text = "Say nothing. Look at the scorch marks on her hull.",
                        affection = 2,
                        reply = "\"...Yeah. That's why I didn't want the report.\"",
                        setsFlag = "lyra_saw_damage",
                    ),
                ),
            ),
            Beat.Say(
                Speaker.Partner,
                "Look. Something out there in the Drift moves wrong. I chased it, it chased back.",
            ),
            Beat.Say(Speaker.Player, "And you came here instead of medical."),
            Beat.Say(
                Speaker.Partner,
                "I came here because you're the only one on this station who can hear the Drift " +
                    "sing. I need to know if I'm losing it.",
            ),
            Beat.Ask(
                prompt = "She's asking seriously. That costs her something.",
                options = listOf(
                    Choice(
                        text = "\"You're not. I've been hearing it for a week.\"",
                        affection = 4,
                        reply = "\"...Okay. Okay, that's worse, but I'll take it.\"",
                        setsFlag = "told_lyra_truth",
                    ),
                    Choice(
                        text = "\"Get checked out first. Then I'll listen.\"",
                        affection = 2,
                        reply = "\"Bossy. Fine. But you're coming with me.\"",
                    ),
                ),
            ),
            Beat.Narrate(
                "She wipes her hands on her flight suit, and for a second the swagger isn't there " +
                    "at all.",
            ),
            Beat.Say(Speaker.Partner, "Thanks, {name}. Don't make it weird."),
        ),
    )

    private val lyraClose = Scene(
        id = "lyra_02_nightflight",
        loveInterestId = "lyra",
        title = "Night Flight",
        summary = "Lyra signs out a two-seater at 0300 and puts your name on the manifest.",
        requiredLevel = AffectionLevel.CLOSE,
        beats = listOf(
            Beat.Narrate("Your terminal wakes you at 0257. One line: *Bay Two. Bring a jacket.*"),
            Beat.Say(Speaker.Partner, "You came. I had a whole speech ready for if you didn't."),
            Beat.Say(Speaker.Player, "Let's hear it anyway."),
            Beat.Say(Speaker.Partner, "Absolutely not. Get in."),
            Beat.Narrate(
                "Aurora-9 falls away behind you. She kills the running lights, and the Drift opens " +
                    "up ahead — a slow violet tide, folding over itself, singing in a register you " +
                    "feel in your teeth.",
            ),
            Beat.Say(Speaker.Partner, "This is what I couldn't put in the report."),
            Beat.Ask(
                prompt = "She isn't looking at the Drift. She's looking at you.",
                options = listOf(
                    Choice(
                        text = "\"It's beautiful.\"",
                        affection = 4,
                        reply = "\"Yeah,\" she says, still not looking away. \"It really is.\"",
                    ),
                    Choice(
                        text = "\"You brought me out here to see if I'd be afraid.\"",
                        affection = 5,
                        reply = "\"I brought you out here because I'm afraid. Alone was worse.\"",
                        setsFlag = "lyra_admitted_fear",
                    ),
                    Choice(
                        text = "Reach over and take her hand.",
                        affection = 6,
                        reply = "She goes very still. Then her fingers close around yours, hard.",
                        setsFlag = "lyra_held_hand",
                    ),
                ),
            ),
            Beat.Say(
                Speaker.Partner,
                "Whatever's coming out of that thing — I'm flying at it. That's not going to change.",
            ),
            Beat.Say(Speaker.Player, "I know."),
            Beat.Say(Speaker.Partner, "So the only question is whether you're in the other seat."),
        ),
    )

    // ------------------------------------------------------------------ Nadia

    private val nadiaIntro = Scene(
        id = "nadia_01_lab",
        loveInterestId = "nadia",
        title = "Specimen Nine",
        summary = "The xenobiology lab is dark, and something in it is humming back at you.",
        beats = listOf(
            Beat.Narrate(
                "The lab runs on emergency lighting after hours. In the ninth tank, something the " +
                    "colour of deep water turns to face you before you knock.",
            ),
            Beat.Say(Speaker.Partner, "It noticed you three corridors ago. Come in, don't apologise."),
            Beat.Say(Speaker.Player, "How do you know I was going to apologise?"),
            Beat.Say(
                Speaker.Partner,
                "Everyone does. Then they ask if it's dangerous. Then they leave.",
            ),
            Beat.Ask(
                prompt = "She hasn't looked up from her notes once.",
                options = listOf(
                    Choice(
                        text = "\"Is it dangerous?\"",
                        affection = 1,
                        reply = "\"There it is.\" A small, amused breath. \"No. It's lonely.\"",
                    ),
                    Choice(
                        text = "\"What's it saying?\"",
                        affection = 4,
                        reply = "Now she looks up. \"...Say that again.\"",
                        setsFlag = "nadia_asked_saying",
                    ),
                    Choice(
                        text = "\"Do you want help, or company?\"",
                        affection = 3,
                        reply = "\"I genuinely don't know. Stay, and we'll find out.\"",
                    ),
                ),
            ),
            Beat.Narrate(
                "She sets the tablet down. It is, you suspect, the most significant thing she has " +
                    "done all week.",
            ),
            Beat.Say(
                Speaker.Partner,
                "Nine has been repeating a nine-second pattern for eleven days. Nobody hears it as " +
                    "language. You just did.",
            ),
            Beat.Say(Speaker.Player, "I hear it in the Drift too."),
            Beat.Say(
                Speaker.Partner,
                "Then I'd like you back here tomorrow, {name}. Bring nothing. Just your ears.",
            ),
        ),
    )

    // ------------------------------------------------------------------ Rook

    private val rookIntro = Scene(
        id = "rook_01_docks",
        loveInterestId = "rook",
        title = "Nothing Declared",
        summary = "A salvage runner with an empty manifest and a full hold.",
        beats = listOf(
            Beat.Narrate(
                "The salvage berth smells like ozone and cold metal. The runner docked there is " +
                    "listed as carrying nothing at all.",
            ),
            Beat.Say(Speaker.Partner, "You're standing in my light."),
            Beat.Say(Speaker.Player, "Your manifest says empty."),
            Beat.Say(Speaker.Partner, "My manifest is a work of art. Don't ruin it."),
            Beat.Ask(
                prompt = "She still hasn't turned around.",
                options = listOf(
                    Choice(
                        text = "\"I'm not station security.\"",
                        affection = 3,
                        reply = "\"I know exactly what you are. That's the problem.\"",
                    ),
                    Choice(
                        text = "\"What did you find out there?\"",
                        affection = 4,
                        reply = "A long pause. \"Something that shouldn't have been floating.\"",
                        setsFlag = "rook_asked_cargo",
                    ),
                    Choice(
                        text = "Step out of her light. Wait.",
                        affection = 5,
                        reply = "She works for a full minute before she says, quietly: \"...Huh.\"",
                        setsFlag = "rook_waited",
                    ),
                ),
            ),
            Beat.Narrate("She finally turns. Her eyes do an inventory of you and give nothing back."),
            Beat.Say(
                Speaker.Partner,
                "Everyone on this station wants something from the Drift. Salvage, science, glory.",
            ),
            Beat.Say(Speaker.Partner, "What do you want, {name}?"),
            Beat.Ask(
                prompt = "It isn't a rhetorical question.",
                options = listOf(
                    Choice(
                        text = "\"To understand it.\"",
                        affection = 3,
                        reply = "\"Dangerous. At least it's honest.\"",
                    ),
                    Choice(
                        text = "\"To make it stop.\"",
                        affection = 4,
                        reply = "Something in her face shifts. \"...Yeah. Me too.\"",
                        setsFlag = "rook_shared_goal",
                    ),
                    Choice(
                        text = "\"I don't know yet.\"",
                        affection = 2,
                        reply = "\"Best answer I've had all year.\"",
                    ),
                ),
            ),
        ),
    )

    private val rookAcquainted = Scene(
        id = "rook_02_hold",
        loveInterestId = "rook",
        title = "What She Kept",
        summary = "Rook opens the hold she said was empty.",
        requiredLevel = AffectionLevel.ACQUAINTED,
        beats = listOf(
            Beat.Say(Speaker.Partner, "Don't touch anything. Don't log anything. Don't be clever."),
            Beat.Narrate(
                "The hold lights come up on a single object suspended in a stasis cradle: a shard " +
                    "of something that is not quite stone, humming the nine-second pattern.",
            ),
            Beat.Say(Speaker.Player, "That's the sound."),
            Beat.Say(Speaker.Partner, "You hear it too. That's why I let you in here."),
            Beat.Ask(
                prompt = "She's watching you more carefully than she's watching the shard.",
                options = listOf(
                    Choice(
                        text = "\"Why keep it? You could have sold this.\"",
                        affection = 3,
                        reply = "\"Because whoever buys it stops asking what it is.\"",
                    ),
                    Choice(
                        text = "\"You're scared of it.\"",
                        affection = 5,
                        reply = "\"I'm scared of what it's for.\" She doesn't deny the first part.",
                        setsFlag = "rook_admitted_fear",
                    ),
                ),
            ),
            Beat.Say(Speaker.Partner, "I've worked alone for nine years, {name}."),
            Beat.Say(Speaker.Partner, "Ask me to keep doing that. Go on."),
        ),
    )

    // ------------------------------------------------------------------ Kaito

    private val kaitoIntro = Scene(
        id = "kaito_01_charts",
        loveInterestId = "kaito",
        title = "Dead Reckoning",
        summary = "The navigator has been awake for thirty hours and the charts are wrong.",
        beats = listOf(
            Beat.Narrate(
                "Navigation is a dim room full of light. Kaito Mori sits at the centre of it, " +
                    "surrounded by projected Drift charts that keep quietly disagreeing with " +
                    "themselves.",
            ),
            Beat.Say(Speaker.Partner, "Oh — sorry, I didn't — is it that late? It's that late."),
            Beat.Say(Speaker.Player, "It's early, actually."),
            Beat.Say(Speaker.Partner, "That's so much worse. Sorry. Hello. Hi."),
            Beat.Ask(
                prompt = "He's already tidying charts that don't need tidying.",
                options = listOf(
                    Choice(
                        text = "\"Show me what's bothering you.\"",
                        affection = 4,
                        reply = "He stops tidying. \"...Really? Okay. Okay, sit, sit.\"",
                        setsFlag = "kaito_shared_charts",
                    ),
                    Choice(
                        text = "\"When did you last sleep?\"",
                        affection = 3,
                        reply = "\"That's — a complicated question with a boring answer.\"",
                    ),
                    Choice(
                        text = "\"You don't have to apologise to me.\"",
                        affection = 3,
                        reply = "\"Sorr— ...noted. Thank you.\"",
                    ),
                ),
            ),
            Beat.Narrate(
                "He pulls a chart between you. Eleven days of Drift readings, and a nine-second " +
                    "ripple threaded through every one of them.",
            ),
            Beat.Say(
                Speaker.Partner,
                "The Drift used to be weather. Weather doesn't repeat itself to the second.",
            ),
            Beat.Say(Speaker.Player, "It's not weather. It's a voice."),
            Beat.Say(
                Speaker.Partner,
                "...I was hoping someone else would say that first. Thank you. Genuinely.",
            ),
        ),
    )

    private val kaitoAcquainted = Scene(
        id = "kaito_02_observation",
        loveInterestId = "kaito",
        title = "The Long Window",
        summary = "He asks you to look at something that isn't a chart.",
        requiredLevel = AffectionLevel.ACQUAINTED,
        beats = listOf(
            Beat.Say(Speaker.Partner, "I know it's your rest cycle. I practised asking. It went badly."),
            Beat.Narrate(
                "The observation deck is empty at this hour. He's brought two cups of something " +
                    "hot and a tablet he pointedly does not turn on.",
            ),
            Beat.Say(Speaker.Partner, "No charts tonight. I promised myself. Out loud. To a wall."),
            Beat.Ask(
                prompt = "He is trying very hard, and it shows.",
                options = listOf(
                    Choice(
                        text = "\"Then what are we doing here?\"",
                        affection = 2,
                        reply = "\"Sitting. With you. That was the entire plan. It's not a good plan.\"",
                    ),
                    Choice(
                        text = "\"It's a good plan, Kaito.\"",
                        affection = 5,
                        reply = "He goes pink to the ears and says nothing for a while.",
                        setsFlag = "kaito_reassured",
                    ),
                ),
            ),
            Beat.Narrate("Outside, the Drift turns over slowly, violet on black."),
            Beat.Say(
                Speaker.Partner,
                "I've charted every safe route out of this system. Eleven of them. I check nightly.",
            ),
            Beat.Say(Speaker.Player, "Why?"),
            Beat.Say(
                Speaker.Partner,
                "So that if it ever comes to it, I know exactly how to get you out. That's all. " +
                    "That's the whole thing I wanted to say.",
            ),
        ),
    )

    // ------------------------------------------------------------------ Sev

    private val sevIntro = Scene(
        id = "sev_01_briefing",
        loveInterestId = "sev",
        title = "Standing Orders",
        summary = "The commander has questions about a pilot who hears things.",
        beats = listOf(
            Beat.Narrate(
                "The commander's office has one chair on your side of the desk and nothing else. " +
                    "No photographs. No commendations. A single sealed file with your name on it.",
            ),
            Beat.Say(Speaker.Partner, "Sit. This is not disciplinary."),
            Beat.Say(Speaker.Player, "It has the furniture of disciplinary."),
            Beat.Say(Speaker.Partner, "Yes. Requisitions has been unhelpful."),
            Beat.Ask(
                prompt = "There is, very briefly, something that could be humour.",
                options = listOf(
                    Choice(
                        text = "\"Was that a joke, Commander?\"",
                        affection = 4,
                        reply = "\"It was an observation about furniture. Interpret it freely.\"",
                        setsFlag = "sev_joke",
                    ),
                    Choice(
                        text = "\"Then what is this?\"",
                        affection = 2,
                        reply = "\"A conversation I would prefer not to be having on record.\"",
                    ),
                ),
            ),
            Beat.Say(
                Speaker.Partner,
                "Three of my crew have filed reports referencing a repeating signal in the Drift. " +
                    "All three cite you as corroboration.",
            ),
            Beat.Say(Speaker.Player, "And you don't believe it."),
            Beat.Say(
                Speaker.Partner,
                "I heard it eleven days ago, alone, on the command deck. I filed nothing.",
            ),
            Beat.Ask(
                prompt = "He says it flatly, the way people say things they've rehearsed.",
                options = listOf(
                    Choice(
                        text = "\"Why tell me?\"",
                        affection = 4,
                        reply = "\"Because you are the only person aboard who will not ask me to explain it.\"",
                    ),
                    Choice(
                        text = "\"That was eleven days of carrying it alone.\"",
                        affection = 5,
                        reply = "A long silence. \"...Yes. It was.\"",
                        setsFlag = "sev_seen",
                    ),
                ),
            ),
            Beat.Say(Speaker.Partner, "You will report anything you hear. To me. Directly."),
            Beat.Say(Speaker.Partner, "That is a standing order, {name}. It is also a request."),
        ),
    )

    // ------------------------------------------------------------------ Idris

    private val idrisIntro = Scene(
        id = "idris_01_reactor",
        loveInterestId = "idris",
        title = "Percussive Maintenance",
        summary = "Engineering is on fire. Slightly. On purpose. Allegedly.",
        beats = listOf(
            Beat.Narrate(
                "There is smoke in engineering, a cheerful alarm nobody is answering, and a pair of " +
                    "boots sticking out from under the coolant manifold.",
            ),
            Beat.Say(Speaker.Partner, "Whoever that is — pass me the wide spanner and don't look at panel six."),
            Beat.Ask(
                prompt = "Panel six is glowing.",
                options = listOf(
                    Choice(
                        text = "Pass the spanner. Don't look at panel six.",
                        affection = 4,
                        reply = "\"A person of taste. Marry me. Later. After the reactor.\"",
                        setsFlag = "idris_complicit",
                    ),
                    Choice(
                        text = "\"Panel six is glowing, Chief.\"",
                        affection = 3,
                        reply = "\"Panel six is *expressing itself*. Spanner, please.\"",
                    ),
                    Choice(
                        text = "Pull the coolant lever first.",
                        affection = 2,
                        reply = "\"— or that. That also works. Ruins the drama, but it works.\"",
                    ),
                ),
            ),
            Beat.Narrate(
                "He rolls out from under the manifold, grins up at you, and does not get up for a " +
                    "moment.",
            ),
            Beat.Say(Speaker.Partner, "You're the resonance pilot. The one who hears the Drift."),
            Beat.Say(Speaker.Player, "Word travels."),
            Beat.Say(
                Speaker.Partner,
                "Word sprints. Listen — the reactor's been humming a nine-second pattern for " +
                    "eleven days and I've rebuilt half of it looking for the fault.",
            ),
            Beat.Say(Speaker.Partner, "There is no fault, is there."),
            Beat.Ask(
                prompt = "The grin is still there. His eyes aren't in it.",
                options = listOf(
                    Choice(
                        text = "\"No. It's listening to something.\"",
                        affection = 4,
                        reply = "\"Right. Great. Love that. Absolutely going to sleep tonight.\"",
                    ),
                    Choice(
                        text = "\"You already knew that.\"",
                        affection = 5,
                        reply = "\"I did. I wanted to be wrong in company.\"",
                        setsFlag = "idris_honest",
                    ),
                ),
            ),
        ),
    )

    val scenes: List<Scene> = listOf(
        lyraIntro,
        lyraClose,
        nadiaIntro,
        rookIntro,
        rookAcquainted,
        kaitoIntro,
        kaitoAcquainted,
        sevIntro,
        idrisIntro,
    )
}
