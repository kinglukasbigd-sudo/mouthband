package com.mouthband.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mouthband.BuildConfig
import com.mouthband.model.CoachTarget
import com.mouthband.model.TrackType
import com.mouthband.ui.arrange.ArrangeDestination
import com.mouthband.ui.coach.StepDestination
import com.mouthband.ui.common.appContainer
import com.mouthband.ui.components.easeOut
import com.mouthband.ui.export.ExportDestination
import com.mouthband.ui.gallery.GalleryDestination
import com.mouthband.ui.home.HomeDestination
import com.mouthband.ui.licenses.LicensesScreen
import com.mouthband.ui.onboarding.OnboardingDestination
import com.mouthband.ui.settings.CalibrationDestination
import com.mouthband.ui.settings.NudgeDestination
import com.mouthband.ui.settings.SettingsDestination
import com.mouthband.ui.settings.SettingsNavigation
import com.mouthband.ui.teach.TeachDestination
import com.mouthband.ui.theme.LocalReduceMotion
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.vibe.VibeDestination

private const val TRANSITION_MS = 220

/** The screen underneath moves a quarter of the width (parallax). */
private const val PARALLAX = 4

/** The app's navigation graph. Start: onboarding on first launch, then Home. */
@Composable
fun MouthBandNavHost(shell: AppShell) {
    val container = appContainer()
    val onboardingDone by produceState<Boolean?>(initialValue = null) {
        value = container.settings.flagsNow().onboardingDone
    }
    val done = onboardingDone
    if (done == null) {
        Box(Modifier.fillMaxSize().background(MbColors.Cream))
        return
    }
    val nav = rememberNavController()
    val duration = if (LocalReduceMotion.current) 0 else TRANSITION_MS
    val activity = LocalActivity.current
    val entry by nav.currentBackStackEntryAsState()
    BackHandler(enabled = entry != null && nav.previousBackStackEntry == null) { activity?.moveTaskToBack(true) }
    NavHost(
        navController = nav,
        startDestination = if (done) HomeRoute else OnboardingRoute,
        // Forward: the new screen slides in from the right over the old one, which drifts left.
        // Back (also the predictive back gesture): the top screen follows the finger to the right
        // and reveals the previous screen, which drifts back in. No cross-fades of two full screens.
        enterTransition = { slideIntoContainer(SlideDirection.Start, easeOut(duration)) },
        exitTransition = {
            slideOutOfContainer(SlideDirection.Start, easeOut(duration)) { it / PARALLAX }
        },
        popEnterTransition = {
            slideIntoContainer(SlideDirection.End, easeOut(duration)) { it / PARALLAX }
        },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, easeOut(duration)) },
    ) {
        composable<OnboardingRoute> {
            OnboardingDestination(onFinished = {
                nav.navigate(HomeRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            })
        }
        // Home stays still under the next screen (no parallax): the banner beneath it is a separate
        // view that would otherwise have to follow every frame of the slide.
        composable<HomeRoute>(
            exitTransition = { ExitTransition.KeepUntilTransitionsFinished },
            popEnterTransition = { EnterTransition.None },
        ) {
            HomeDestination(
                onOpen = { id, target -> nav.navigateCoach(id, target) },
                onSettings = { nav.navigate(SettingsRoute) },
                onOpenGallery = if (BuildConfig.DEBUG) ({ nav.navigate(GalleryRoute) }) else null,
                banner = shell.homeBanner,
            )
        }
        composable<VibeRoute> { entry ->
            val route = entry.toRoute<VibeRoute>()
            VibeDestination(
                songId = route.songId,
                onNext = { nav.navigateCoach(route.songId, CoachTarget.Record(TrackType.BEAT)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable<StepRoute> { entry ->
            val route = entry.toRoute<StepRoute>()
            StepDestination(
                songId = route.songId,
                type = route.track,
                edit = route.edit,
                fromArrange = route.fromArrange,
                onNext = { target -> nav.navigateCoach(route.songId, target) },
                onTeach = { nav.navigate(TeachRoute(route.songId)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable<TeachRoute> { TeachDestination(onClose = { nav.popBackStack() }) }
        composable<ArrangeRoute> { entry ->
            val route = entry.toRoute<ArrangeRoute>()
            ArrangeDestination(
                songId = route.songId,
                onExport = { nav.navigateCoach(route.songId, CoachTarget.Export) },
                onEditTrack = { type -> nav.navigate(StepRoute(route.songId, type, edit = true, fromArrange = true)) },
                onRecordTrack = { type -> nav.navigate(StepRoute(route.songId, type, fromArrange = true)) },
                onBack = { nav.popBackStack() },
            )
        }
        composable<ExportRoute> { entry ->
            val route = entry.toRoute<ExportRoute>()
            ExportDestination(
                songId = route.songId,
                onExported = shell.onExportFinished,
                onBack = { nav.backToArrange(route.songId) },
                onHome = { nav.popBackStack(HomeRoute, inclusive = false) },
            )
        }
        composable<SettingsRoute> {
            SettingsDestination(
                SettingsNavigation(
                    onBack = { nav.popBackStack() },
                    onCalibrate = { nav.navigate(CalibrationRoute) },
                    onNudge = { nav.navigate(NudgeRoute) },
                    onTeach = { nav.navigate(TeachRoute()) },
                    onLicenses = { nav.navigate(LicensesRoute) },
                    onPrivacy = shell.onPrivacyOptions,
                ),
                privacyRequired = shell.privacyOptionsRequired,
            )
        }
        composable<CalibrationRoute> { CalibrationDestination(onBack = { nav.popBackStack() }) }
        composable<NudgeRoute> { NudgeDestination(onBack = { nav.popBackStack() }) }
        composable<LicensesRoute> { LicensesScreen(onBack = { nav.popBackStack() }) }
        // Debug builds only: in release the route does not exist and R8 removes the gallery and
        // the demo-song tool.
        if (BuildConfig.DEBUG) {
            composable<GalleryRoute> { GalleryDestination(onBack = { nav.popBackStack() }) }
        }
    }
}

/** Opens a coach screen for a song. */
fun NavHostController.navigateCoach(songId: Long, target: CoachTarget) {
    val route: Any = when (target) {
        CoachTarget.Vibe -> VibeRoute(songId)
        is CoachTarget.Record -> StepRoute(songId, target.track)
        CoachTarget.Arrange -> ArrangeRoute(songId)
        CoachTarget.Export -> ExportRoute(songId)
    }
    navigate(route) { launchSingleTop = true }
}

/** What the activity provides to the graph: the Home ad banner and consent hooks (spec §5). */
/** Export's back goes to Arrange, also when the song was resumed straight at Export from Home. */
private fun NavHostController.backToArrange(songId: Long) {
    if (previousBackStackEntry?.destination?.hasRoute<ArrangeRoute>() == true) {
        popBackStack()
    } else {
        navigate(ArrangeRoute(songId)) { popUpTo<ExportRoute> { inclusive = true } }
    }
}

class AppShell(
    val homeBanner: (@Composable () -> Unit)?,
    val onExportFinished: () -> Unit,
    val onPrivacyOptions: () -> Unit,
    val privacyOptionsRequired: Boolean,
)
