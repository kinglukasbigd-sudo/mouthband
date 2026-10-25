package com.mouthband.ui.home

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mouthband.R
import com.mouthband.ui.components.AdBannerSlot
import com.mouthband.ui.components.EmptyState
import com.mouthband.ui.components.IconCircleButton
import com.mouthband.ui.components.MonoLabel
import com.mouthband.ui.components.PrimaryPillButton
import com.mouthband.ui.components.SectionHeading
import com.mouthband.ui.components.StepSegment
import com.mouthband.ui.icons.MbIcons
import com.mouthband.ui.theme.MbColors
import com.mouthband.ui.theme.MbDimens
import com.mouthband.ui.theme.MbTheme

/** One song card on Home. */
@Immutable
data class SongCardUi(
    val id: Long,
    val title: String,
    val meta: String,
    val segments: List<StepSegment>,
    val status: String,
    val progressDescription: String,
)

@Immutable
data class HomeUiState(
    val songs: List<SongCardUi> = emptyList(),
    val loading: Boolean = true,
)

/** Callbacks of the Home screen, grouped to keep the composable signature short. */
@Immutable
data class HomeActions(
    val onNewSong: () -> Unit,
    val onOpenSong: (Long) -> Unit,
    val onRename: (Long, String) -> Unit,
    val onDuplicate: (Long) -> Unit,
    val onDelete: (Long) -> Unit,
    val onSettings: () -> Unit,
    val onOpenGallery: (() -> Unit)? = null,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    banner: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuFor by rememberSaveable { mutableStateOf<Long?>(null) }
    // No background of its own: the app's cream lies beneath every screen, and the banner layer under
    // Home shows through the gap at the bottom.
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).widthIn(max = MbDimens.ContentMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = MbDimens.ScreenPadding,
                vertical = MbDimens.GapLarge,
            ),
            verticalArrangement = Arrangement.spacedBy(MbDimens.Gap),
        ) {
            item { HomeHeader(actions) }
            item { HomeHero(actions.onNewSong) }
            item {
                Spacer(Modifier.height(MbDimens.GapLarge))
                SectionHeading(
                    title = stringResource(R.string.home_your_songs),
                    trailing = if (state.loading) {
                        null
                    } else {
                        pluralStringResource(R.plurals.home_song_count, state.songs.size, state.songs.size)
                    },
                )
            }
            if (state.songs.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        body = stringResource(R.string.home_empty_body),
                        icon = MbIcons.Note,
                    )
                }
            }
            items(state.songs, key = { it.id }) { song ->
                SongCard(
                    song = song,
                    onOpen = { actions.onOpenSong(song.id) },
                    onMore = { menuFor = song.id },
                    // New, duplicated and deleted songs fade and slide into place.
                    modifier = Modifier.animateItem(),
                )
            }
        }
        Box(Modifier.navigationBarsPadding()) {
            AdBannerSlot(banner = banner)
        }
    }
    val target = state.songs.firstOrNull { it.id == menuFor }
    if (target != null) {
        SongActionsSheet(
            song = target,
            onDismiss = { menuFor = null },
            onRename = { title -> actions.onRename(target.id, title) },
            onDuplicate = { actions.onDuplicate(target.id) },
            onDelete = { actions.onDelete(target.id) },
        )
    }
}

@Composable
private fun HomeHeader(actions: HomeActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val gallery = actions.onOpenGallery
        val wordmarkModifier = if (gallery != null) {
            Modifier.combinedClickable(onClick = {}, onLongClick = gallery)
        } else {
            Modifier
        }
        Text(
            text = stringResource(R.string.app_name),
            style = MbTheme.type.wordmark,
            color = MbColors.Indigo,
            modifier = wordmarkModifier.weight(1f).semantics { heading() },
        )
        IconCircleButton(
            icon = MbIcons.Settings,
            contentDescription = stringResource(R.string.settings),
            onClick = actions.onSettings,
        )
    }
}

@Composable
private fun HomeHero(onNewSong: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MbDimens.GapLarge),
        modifier = Modifier.padding(top = MbDimens.ScreenPadding),
    ) {
        MonoLabel(stringResource(R.string.home_eyebrow), color = MbColors.Muted)
        Text(
            text = stringResource(R.string.home_headline),
            style = MbTheme.type.display,
            color = MbColors.Indigo,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.home_body), style = MbTheme.type.body, color = MbColors.InkSoft)
        PrimaryPillButton(
            text = stringResource(R.string.home_new_song),
            onClick = onNewSong,
            showArrow = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
