package com.mouthband.ui.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.mouthband.R
import com.mouthband.ui.common.appContainer
import kotlinx.coroutines.launch

/** Debug-only gallery route, with the demo-song tool for trying Arrange and Export on an emulator. */
@Composable
fun GalleryDestination(onBack: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.gallery_demo_title)
    ComponentGallery(
        onBack = onBack,
        onDemoSong = {
            scope.launch {
                val seeder = DemoSongSeeder(
                    container.repository,
                    container.trackRepository,
                    container.loopRenderer,
                    container.engine.sampleRate,
                )
                seeder.create(title)
                onBack()
            }
        },
    )
}
