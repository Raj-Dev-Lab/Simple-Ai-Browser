package com.simpleaibrowser.sab.ui.screens

import android.app.Activity
import android.net.Uri
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.simpleaibrowser.sab.core.browser.SabViewerRequest
import com.simpleaibrowser.sab.ui.theme.JetBrainsMonoFamily
import com.simpleaibrowser.sab.ui.theme.LocalSabColors
import kotlinx.coroutines.delay
import java.util.Locale

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun SabMediaPlayerOverlay(
    request: SabViewerRequest.Media,
    onShareUri: (String, String) -> Unit,
    onOpenExternal: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sabColors = LocalSabColors.current

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember(request) { mutableStateOf<String?>(null) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableFloatStateOf(0f) }

    var videoWidth by remember { mutableIntStateOf(16) }
    var videoHeight by remember { mutableIntStateOf(9) }
    var showHud by remember { mutableStateOf(true) }
    var immersiveVideo by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var loopOne by remember { mutableStateOf(false) }
    var speedIndex by remember { mutableIntStateOf(2) } // 1.0f

    val exoPlayer = remember(request) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(request.uri)))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playerError = error.localizedMessage ?: "Unsupported media codec or stream unreachable"
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Track real playback position smoothly while playing
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val d = exoPlayer.duration
                if (d > 0L) durationMs = d
            }
            delay(250L)
        }
    }

    // Auto-hide video HUD after inactivity while playing
    LaunchedEffect(showHud, isPlaying, request.isVideo) {
        if (request.isVideo && showHud && isPlaying && playerError == null) {
            delay(3_600L)
            showHud = false
        }
    }

    // Toggle system bars in immersive video fullscreen mode
    DisposableEffect(immersiveVideo) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersiveVideo) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val displayPosMs = if (isScrubbing) scrubMs.toLong() else positionMs.coerceAtMost(safeDuration)
    val currentSpeed = PLAYBACK_SPEEDS[speedIndex]

    Surface(
        color = Color(0xFF06080E),
        modifier = Modifier
            .fillMaxSize()
            .testTag("sab_media_player_overlay")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (request.isVideo) {
                        showHud = !showHud
                    }
                }
        ) {
            if (request.isVideo && playerError == null) {
                val aspect = (videoWidth.toFloat() / videoHeight.coerceAtLeast(1).toFloat())
                    .coerceIn(0.4f, 2.6f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .align(Alignment.Center)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { sv ->
                            exoPlayer.setVideoSurfaceView(sv)
                        },
                        onRelease = { sv ->
                            exoPlayer.clearVideoSurfaceView(sv)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (!request.isVideo && playerError == null) {
                // Crafted Audio Studio Centerpiece
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    Color(0xFF06080E)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(132.dp)
                                .clip(CircleShape)
                                .background(sabColors.chromeSurfaceElevated)
                                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Audiotrack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                        Text(
                            text = request.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = (request.mimeType ?: "Audio Stream") +
                                if (isPlaying) " • Playing ${currentSpeed}x" else " • Paused",
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            color = sabColors.subtleText
                        )
                    }
                }
            }

            if (isBuffering && playerError == null) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (playerError != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(28.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(sabColors.chromeSurfaceElevated)
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Playback Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = playerError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = sabColors.subtleText
                    )
                    val extTarget = request.fallbackExternalUri ?: request.shareUri ?: request.uri
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Close")
                        }
                        Button(onClick = { onOpenExternal(extTarget, request.mimeType) }) {
                            Text("Open External")
                        }
                    }
                }
            }

            // Player HUD Controls (Always visible for Audio; toggleable for Video)
            AnimatedVisibility(
                visible = !request.isVideo || showHud || playerError != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top Gradient Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xDD05070D), Color.Transparent)
                                )
                            )
                            .then(if (!immersiveVideo) Modifier.statusBarsPadding() else Modifier)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("media_close_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close player", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = request.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (request.isVideo) "Built-in Video Player • ExoPlayer"
                                else "Built-in Audio Player • ExoPlayer",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = Color(0xFFB3BED4)
                            )
                        }
                        val shareTarget = request.shareUri ?: request.uri
                        IconButton(onClick = { onShareUri(shareTarget, request.title) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share media", tint = Color.White)
                        }
                        val extTarget = request.fallbackExternalUri ?: request.shareUri ?: request.uri
                        IconButton(onClick = { onOpenExternal(extTarget, request.mimeType) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open externally", tint = Color.White)
                        }
                    }

                    // Bottom Gradient Playback Deck
                    if (playerError == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xEE05070D))
                                    )
                                )
                                .then(if (!immersiveVideo) Modifier.navigationBarsPadding() else Modifier)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Seek Slider + Mono Timestamps
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatPlayerTime(displayPosMs),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                                Slider(
                                    value = displayPosMs.toFloat(),
                                    onValueChange = { next ->
                                        isScrubbing = true
                                        scrubMs = next
                                    },
                                    onValueChangeFinished = {
                                        exoPlayer.seekTo(scrubMs.toLong())
                                        positionMs = scrubMs.toLong()
                                        isScrubbing = false
                                    },
                                    valueRange = 0f..safeDuration.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (durationMs > 0L) formatPlayerTime(durationMs) else "--:--",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = Color(0xFFB3BED4)
                                )
                            }

                            // Transport Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Speed & Mute & Loop group
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            isMuted = !isMuted
                                            exoPlayer.volume = if (isMuted) 0f else 1f
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                            else Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = if (isMuted) "Unmute" else "Mute",
                                            tint = if (isMuted) MaterialTheme.colorScheme.error else Color.White
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .clickable {
                                                speedIndex = (speedIndex + 1) % PLAYBACK_SPEEDS.size
                                                exoPlayer.setPlaybackSpeed(PLAYBACK_SPEEDS[speedIndex])
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Speed,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "${currentSpeed}x",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    }
                                }

                                // Rewind / Play-Pause / Forward group
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                            exoPlayer.seekTo(target)
                                            positionMs = target
                                        }
                                    ) {
                                        Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                                    }

                                    FilledIconButton(
                                        onClick = {
                                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                                exoPlayer.seekTo(0L)
                                                exoPlayer.play()
                                            } else if (exoPlayer.isPlaying) {
                                                exoPlayer.pause()
                                            } else {
                                                exoPlayer.play()
                                            }
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .size(54.dp)
                                            .testTag("media_play_pause_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val maxPos = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                                            val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxPos)
                                            exoPlayer.seekTo(target)
                                            positionMs = target
                                        }
                                    ) {
                                        Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                                    }
                                }

                                // Loop & Fullscreen group
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            loopOne = !loopOne
                                            exoPlayer.repeatMode = if (loopOne) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (loopOne) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                            contentDescription = "Loop playback",
                                            tint = if (loopOne) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    }

                                    if (request.isVideo) {
                                        IconButton(onClick = { immersiveVideo = !immersiveVideo }) {
                                            Icon(
                                                imageVector = if (immersiveVideo) Icons.Filled.FullscreenExit
                                                else Icons.Filled.Fullscreen,
                                                contentDescription = "Toggle fullscreen",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}
