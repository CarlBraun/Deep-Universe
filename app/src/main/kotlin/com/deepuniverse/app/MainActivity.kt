package com.deepuniverse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deepuniverse.app.ui.GameViewModel
import com.deepuniverse.app.ui.Screen
import com.deepuniverse.app.ui.creator.CharacterCreatorScreen
import com.deepuniverse.app.ui.home.HomeScreen
import com.deepuniverse.app.ui.overworld.OverworldScreen
import com.deepuniverse.app.ui.puzzle.PuzzleScreen
import com.deepuniverse.app.ui.store.StoreScreen
import com.deepuniverse.app.ui.route.RouteScreen
import com.deepuniverse.app.ui.story.StoryScreen
import com.deepuniverse.app.ui.theme.DeepUniverseTheme
import com.deepuniverse.app.ui.theme.MutedStar
import com.deepuniverse.core.game.Cast

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DeepUniverseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // targetSdk 35 makes edge-to-edge mandatory, so the window extends behind the
                    // status and navigation bars. Without this the creator's tabs would sit under
                    // the clock and its confirm button under the gesture bar.
                    Box(Modifier.safeDrawingPadding()) {
                        DeepUniverseApp(onFinish = { finish() })
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepUniverseApp(onFinish: () -> Unit) {
    val viewModel: GameViewModel = viewModel()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val worldPosition by viewModel.worldPosition.collectAsStateWithLifecycle()
    val overworldMessage by viewModel.overworldMessage.collectAsStateWithLifecycle()
    val reward by viewModel.reward.collectAsStateWithLifecycle()
    val storeMessage by viewModel.storeMessage.collectAsStateWithLifecycle()
    val purchasing by viewModel.purchasing.collectAsStateWithLifecycle()
    val puzzle by viewModel.puzzle.collectAsStateWithLifecycle()
    val adReady by viewModel.adReady.collectAsStateWithLifecycle()

    BackHandler(enabled = true) {
        if (!viewModel.goBack()) onFinish()
    }

    when (val current = screen) {
        Screen.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        Screen.Title -> TitleScreen(
            hasCharacter = state.characterCreated,
            playerName = state.player.name,
            onContinue = { viewModel.openOverworld() },
            onNewCharacter = { viewModel.openCreator(fromExistingCharacter = false) },
        )

        Screen.Overworld -> OverworldScreen(
            position = worldPosition,
            player = state.player,
            // Recomputed whenever the player turns or steps, which is exactly when it can change.
            facingNpc = remember(worldPosition) { viewModel.facingNpc() },
            message = overworldMessage,
            reward = reward,
            momentsLeft = state.stamina.available,
            momentsMax = state.stamina.max,
            stars = state.wallet.stars,
            boosted = viewModel.isBoosted(),
            puzzleLabel = remember(worldPosition) {
                viewModel.facingNpc()?.let { viewModel.puzzleKindFor(it.loveInterestId).label }
            },
            onMove = viewModel::move,
            onWalkTo = viewModel::walkTo,
            onInteract = viewModel::interact,
            onPlayPuzzle = {
                viewModel.facingNpc()?.let { viewModel.startPuzzle(it.loveInterestId) }
            },
            onDismissMessage = viewModel::dismissOverworldMessage,
            onDismissReward = viewModel::dismissReward,
            onOpenJournal = viewModel::openHome,
            onOpenStore = viewModel::openStore,
        )

        Screen.Puzzle -> {
            val active = puzzle
            if (active == null) {
                LaunchedEffect(Unit) { viewModel.openOverworld() }
            } else {
                PuzzleScreen(
                    puzzle = active,
                    member = Cast.byId(active.loveInterestId),
                    stars = state.wallet.stars,
                    adReady = adReady,
                    onRevealCell = viewModel::revealCell,
                    onFlagCell = viewModel::flagCell,
                    onSearch = viewModel::searchBush,
                    onCookTick = viewModel::tickPot,
                    onRetryWithStars = viewModel::retryPuzzleWithStars,
                    onRetryWithAd = viewModel::retryPuzzleWithAd,
                    onLeave = viewModel::leavePuzzle,
                )
            }
        }

        Screen.Store -> StoreScreen(
            state = state,
            purchasing = purchasing,
            message = storeMessage,
            onBuy = viewModel::buy,
            onRedeem = viewModel::redeem,
            onSetLimit = viewModel::setMonthlyLimit,
            onDismissMessage = viewModel::dismissStoreMessage,
            onBack = viewModel::openOverworld,
        )

        Screen.Creator -> CharacterCreatorScreen(
            appearance = draft,
            photo = photo,
            onParamChange = viewModel::setParam,
            onParamReset = viewModel::resetParam,
            onName = viewModel::setName,
            onPronouns = viewModel::setPronouns,
            onPresentation = viewModel::setPresentation,
            onHairStyle = viewModel::setHairStyle,
            onSkinColor = viewModel::setSkinColor,
            onHairColor = viewModel::setHairColor,
            onEyeColor = viewModel::setEyeColor,
            onPreset = viewModel::applyPreset,
            onPhotoUri = viewModel::generateFromPhoto,
            onPhotoStrength = viewModel::setPhotoStrength,
            onDismissPhotoError = viewModel::dismissPhotoError,
            onDone = viewModel::saveCharacter,
        )

        Screen.Home -> HomeScreen(
            state = state,
            onOpenRoute = viewModel::openRoute,
            onEditCharacter = { viewModel.openCreator(fromExistingCharacter = true) },
            onBack = viewModel::openOverworld,
        )

        is Screen.Route -> RouteScreen(
            member = Cast.byId(current.loveInterestId),
            state = state,
            available = viewModel.availableScenes(current.loveInterestId),
            locked = viewModel.lockedScenes(current.loveInterestId),
            onPlayScene = viewModel::startScene,
            onBack = viewModel::openHome,
        )

        Screen.Story -> {
            val active = playback
            if (active == null) {
                // The scene was cleared underneath us (process death, or a race with Back).
                // Recovering has to happen as an effect, not during composition, or the
                // state change would retrigger this composition before it finished.
                LaunchedEffect(Unit) { viewModel.openOverworld() }
            } else {
                StoryScreen(
                    playback = active,
                    member = active.scene.loveInterestId?.let { Cast.byId(it) },
                    player = state.player,
                    onAdvance = viewModel::advanceStory,
                    onChoose = viewModel::chooseStoryOption,
                    onExit = viewModel::endScene,
                )
            }
        }
    }
}

@Composable
private fun TitleScreen(
    hasCharacter: Boolean,
    playerName: String,
    onContinue: () -> Unit,
    onNewCharacter: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("DEEP", style = MaterialTheme.typography.displaySmall)
            Text("UNIVERSE", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(10.dp))
            Text(
                "Aurora-9 is listening. So is something else.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedStar,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))

            if (hasCharacter) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Continue as $playerName")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onNewCharacter) { Text("Create a new character") }
            } else {
                Button(
                    onClick = onNewCharacter,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Create your character")
                }
            }
        }
    }
}
