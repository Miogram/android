package app.miogram.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import app.miogram.feature.common.WorkViewModel
import app.miogram.feature.links.LinksViewModel
import app.miogram.feature.media.DownloadsViewModel
import app.miogram.feature.media.MusicViewModel
import app.miogram.nav.Navigator
import app.miogram.nav.Route
import cn.ellerotta.core.ui.components.common.ErrorSnackbar
import cn.ellerotta.core.ui.components.music.LocalMusicCoverAnchor
import cn.ellerotta.core.ui.components.music.MusicCoverAnchor
import app.miogram.feature.chats.RemovalsViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavHost(navigator: Navigator, version: String, onExit: () -> Unit) {
    val activity = LocalActivity.current as ComponentActivity
    val step = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val stepFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val back = { if (!navigator.back()) onExit() }
    val links = hiltViewModel<LinksViewModel>(activity)
    val downloads = hiltViewModel<DownloadsViewModel>(activity)
    val music = hiltViewModel<MusicViewModel>(activity)
    val removals = hiltViewModel<RemovalsViewModel>(activity)
    val entries = entryProvider<Route> {
        authEntries(activity, navigator, version, back)
        chatsEntries(activity, navigator, version, back, links::openSideMenuApp)
        chatEntries(navigator, back, links::open)
        profileEntries(navigator, back, links::openMainApp, links::open)
        groupsEntries(navigator, back)
        foldersEntries(navigator, back)
        logEntries(back)
        storiesEntries(navigator, back, links::open)
    }
    val cover = remember { MusicCoverAnchor() }
    CompositionLocalProvider(LocalMusicCoverAnchor provides cover) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { back() },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                transitionSpec = { forward(step, stepFade) },
                popTransitionSpec = { backward(step, stepFade) },
                predictivePopTransitionSpec = { backward(step, stepFade) },
                entryProvider = { route ->
                    val entry = entries(route)
                    NavEntry(route, entry.contentKey, entry.metadata + (ROUTE to route)) { entry.Content() }
                },
            )
            LinkHost(links)
            DownloadsHost(downloads)
            RemovalHost(removals)
            MusicHost(music, navigator)
        }
    }
}

@Composable
internal fun Work(model: WorkViewModel, content: @Composable (busy: Boolean) -> Unit) {
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        content(busy > 0)
        error?.let { ErrorSnackbar(it, model::dismissError, Modifier.align(Alignment.BottomCenter)) }
    }
}

private enum class Motion(
    val enter: (Spatial, Effects) -> EnterTransition,
    val exit: (Spatial, Effects) -> ExitTransition,
    val popEnter: (Spatial, Effects) -> EnterTransition,
    val popExit: (Spatial, Effects) -> ExitTransition,
) {
    Auth(
        enter = { s, f -> fadeIn(f) + slideInHorizontally(s) { -it } },
        exit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { -it } },
        popEnter = { s, f -> fadeIn(f) + slideInHorizontally(s) { -it } },
        popExit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { -it } },
    ),
    Chats(
        enter = { _, f -> fadeIn(f) },
        exit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { -it / 5 } },
        popEnter = { s, f -> fadeIn(f) + slideInHorizontally(s) { -it / 5 } },
        popExit = { _, f -> fadeOut(f) },
    ),
    Contacts(
        enter = { s, f -> fadeIn(f) + slideInHorizontally(s) { it / 3 } },
        exit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { -it / 3 } },
        popEnter = { s, f -> fadeIn(f) + slideInHorizontally(s) { -it / 5 } },
        popExit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { it / 3 } },
    ),
    ArchivedChats(
        enter = { s, f -> fadeIn(f) + slideInHorizontally(s) { it / 3 } },
        exit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { it / 3 } },
        popEnter = { s, f -> fadeIn(f) + slideInHorizontally(s) { -it / 5 } },
        popExit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { it / 3 } },
    ),
    Chat(
        enter = { s, f -> fadeIn(f) + slideInHorizontally(s) { it / 3 } },
        exit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { it / 3 } },
        popEnter = { s, f -> fadeIn(f) + slideInHorizontally(s) { -it / 5 } },
        popExit = { s, f -> fadeOut(f) + slideOutHorizontally(s) { it / 3 } },
    ),
    Stories(
        enter = { s, f -> fadeIn(f) + slideInVertically(s) { it / 5 } },
        exit = { _, f -> fadeOut(f) },
        popEnter = { _, f -> fadeIn(f) },
        popExit = { s, f -> fadeOut(f) + slideOutVertically(s) { it / 5 } },
    ),
}

private typealias Spatial = FiniteAnimationSpec<IntOffset>
private typealias Effects = FiniteAnimationSpec<Float>

private const val ROUTE = "app.miogram.route"

private val Scene<Route>.route: Route? get() = metadata[ROUTE] as? Route

private fun motionOf(route: Route?): Motion = when (route) {
    Route.Splash, Route.Welcome, Route.Phone, Route.QrLogin, Route.Code, Route.Password, Route.SignUp -> Motion.Auth
    Route.Chats -> Motion.Chats
    Route.Archive -> Motion.ArchivedChats
    is Route.ShareTarget, is Route.AttachTarget, Route.BlockPicker -> Motion.Contacts
    is Route.Chat, is Route.Topics, is Route.Media, is Route.PeerProfile, is Route.InviteLinks, is Route.JoinRequests,
    is Route.Members, Route.NewGroupMembers, is Route.NewGroup, Route.NewChannel, is Route.ChannelType, is Route.AddMembers, is Route.EditChat,
    Route.Sessions, Route.Folders, is Route.FolderEdit, Route.Privacy, Route.Blocked, is Route.Wallpapers, Route.EditProfile, is Route.AvatarCrop, is Route.Scheduled, is Route.NewPoll, is Route.SendLocation, is Route.Place, is Route.ShareContact, Route.TwoStep, Route.Notifications, Route.DataStorage, Route.Log, null -> Motion.Chat
    is Route.Stories, is Route.StoryComposer -> Motion.Stories
}

private val Scene<Route>.motion: Motion get() = motionOf(route)

private val Scene<Route>.isLoginStep: Boolean get() = route != Route.Splash && motion == Motion.Auth

private fun AnimatedContentTransitionScope<Scene<Route>>.forward(step: FiniteAnimationSpec<IntOffset>, fade: FiniteAnimationSpec<Float>): ContentTransform = when {
    initialState.route == Route.Splash && targetState.isLoginStep -> EnterTransition.None togetherWith ExitTransition.None
    initialState.isLoginStep && targetState.isLoginStep ->
        (slideInHorizontally(step) { it } + fadeIn(fade)) togetherWith (slideOutHorizontally(step) { -it } + fadeOut(fade))
    else -> targetState.motion.enter(step, fade) togetherWith initialState.motion.exit(step, fade)
}

private fun AnimatedContentTransitionScope<Scene<Route>>.backward(step: FiniteAnimationSpec<IntOffset>, fade: FiniteAnimationSpec<Float>): ContentTransform =
    if (initialState.isLoginStep && targetState.isLoginStep) {
        (slideInHorizontally(step) { -it } + fadeIn(fade)) togetherWith (slideOutHorizontally(step) { it } + fadeOut(fade))
    } else {
        targetState.motion.popEnter(step, fade) togetherWith initialState.motion.popExit(step, fade)
    }
