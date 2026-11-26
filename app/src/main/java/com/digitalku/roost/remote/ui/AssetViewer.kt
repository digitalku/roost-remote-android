package com.digitalku.roost.remote.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.digitalku.roost.remote.net.AssetCard
import com.digitalku.roost.remote.net.AssetDetail
import com.digitalku.roost.remote.net.Rate
import com.digitalku.roost.remote.net.Restore
import okhttp3.OkHttpClient

/**
 * One asset at a time, swiped through in the grid's order, with the rating, the
 * details and the trash over it. A tap hides and shows what is over it.
 *
 * [onClose] is told the index it closed on, so the grid can bring it into view.
 */
@Composable
fun AssetViewer(model: LibraryModel, start: Int, onClose: (Int) -> Unit) {
    val pager = rememberPagerState(initialPage = start) { model.cards.size }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }

    BackHandler { onClose(pager.currentPage) }

    LaunchedEffect(model.cards.isEmpty()) { if (model.cards.isEmpty()) onClose(0) }
    LaunchedEffect(pager.currentPage, model.cards.size) {
        if (pager.currentPage >= model.cards.size - 5) model.more()
    }

    val card = model.cards.getOrNull(pager.currentPage)
    LaunchedEffect(card?.id, model.detailVersion) { card?.let { model.want(it.id) } }
    val detail = card?.let { model.details[it.id] }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            key = { model.cards.getOrNull(it)?.id ?: it },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val shown = model.cards.getOrNull(page) ?: return@HorizontalPager
            LaunchedEffect(shown.id, model.detailVersion) { model.want(shown.id) }
            Page(
                card = shown,
                detail = model.details[shown.id],
                model = model,
                current = page == pager.currentPage,
                onTap = { chrome = !chrome },
            )
        }

        if (chrome && card != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onClose(pager.currentPage) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    card.name,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { info = true }, enabled = detail != null) {
                    Icon(Icons.Filled.Info, contentDescription = "Details", tint = Color.White)
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Stars(detail?.rating ?: card.rating, Modifier.weight(1f)) { stars ->
                    model.edit(Rate(listOf(card.id), stars))
                }
                if (detail?.trashedAt != null) {
                    IconButton(onClick = { model.edit(Restore(listOf(card.id)), "Restored") }) {
                        Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = Color.White)
                    }
                } else {
                    IconButton(onClick = { model.trash(listOf(card.id)) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Move to the trash", tint = Color.White)
                    }
                }
            }
        }
    }

    if (info && detail != null) {
        DetailSheet(model, detail, onDismiss = { info = false })
    }
}

/**
 * Five stars. Tapping the rating it already has clears it, which is how the
 * window's control does it: unrated is `null`, not zero (`DATA_MODEL.md`).
 */
@Composable
fun Stars(rating: Int?, modifier: Modifier = Modifier, tint: Color = Color.White, onRate: (Int?) -> Unit) {
    Row(modifier) {
        for (star in 1..5) {
            val filled = rating != null && star <= rating
            IconButton(onClick = { onRate(if (rating == star) null else star) }) {
                Icon(
                    if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "$star star" + if (star == 1) "" else "s",
                    tint = if (filled) MaterialTheme.colorScheme.primary else tint,
                )
            }
        }
    }
}

@Composable
private fun Page(
    card: AssetCard,
    detail: AssetDetail?,
    model: LibraryModel,
    current: Boolean,
    onTap: () -> Unit,
) {
    val session = model.session
    when {
        (card.kind == "video" || card.kind == "audio") && current && detail != null ->
            Player(session.file(card.id, detail.contentHash), model.http)
        card.kind == "image" -> Zoomable(onTap) {
            // The thumbnail at once, the file over it as it arrives.
            AsyncImage(
                model = session.thumbnail(card, 512),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (detail != null) {
                AsyncImage(
                    model = if (detail.browserDrawable) {
                        session.file(card.id, detail.contentHash)
                    } else {
                        session.render(card.id)
                    },
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        else -> Box(
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = session.thumbnail(card, 512),
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (card.kind == "video" || card.kind == "audio") {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
        }
    }
}

/**
 * Pinch to zoom and double-tap to zoom, and a drag that moves the picture only
 * while it is zoomed, so that the pager still swipes at the picture's own size.
 */
@Composable
private fun Zoomable(onTap: () -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { _, zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan * scale
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .transformable(state, canPan = { scale > 1f })
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) { content() }
}

/** The file streamed with `Range` (RR-13), through the one client and its token. */
@OptIn(UnstableApi::class)
@Composable
private fun Player(url: String, http: OkHttpClient) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(http)))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
