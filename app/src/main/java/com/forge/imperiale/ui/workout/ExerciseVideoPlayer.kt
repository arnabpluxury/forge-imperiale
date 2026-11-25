package com.forge.imperiale.ui.workout

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.forge.imperiale.data.local.entity.ExerciseEntity
import com.forge.imperiale.data.local.entity.VideoStreamType
import com.forge.imperiale.ui.components.LinktreeBentoCard
import com.forge.imperiale.ui.components.LinktreeMonoChip
import com.forge.imperiale.ui.components.LinktreeOutlinePillButton
import com.forge.imperiale.ui.theme.ForgePalette
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Smart 16:9 Exercise Guide Video Player (Section 7.3 + Line 18):
 * - Automatically streams & loops ONLY the exact exercise movement segment (loopStartSec to loopEndSec)
 *   via Media3 ExoPlayer (`ClippingConfiguration` + `REPEAT_MODE_ONE`) for direct MP4/HLS streams,
 *   OR via official embedded YouTube IFrame API (`android-youtube-player`) with automatic loop seeking.
 * - Supports Play/Pause, Loop toggle, Mute/Unmute, 0.5x / 1x form-study speed, Backup stream switch,
 *   and Fullscreen mode.
 */
@Composable
fun ExerciseVideoPlayer(
    exercise: ExerciseEntity,
    forceSlowSpeed: Boolean = false,
    onOpenVideoManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    var useBackupYoutube by remember(exercise.id) { mutableStateOf(false) }
    var isPlaying by rememberSaveable(exercise.id) { mutableStateOf(true) }
    var isLooping by rememberSaveable { mutableStateOf(true) }
    var isMuted by rememberSaveable { mutableStateOf(true) }
    var slowHalfSpeed by rememberSaveable(exercise.id) { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(forceSlowSpeed) {
        if (forceSlowSpeed) slowHalfSpeed = true
    }

    val effectiveType = when {
        useBackupYoutube && exercise.backupYoutubeId.isNotBlank() -> VideoStreamType.YOUTUBE
        else -> exercise.videoType
    }
    val effectiveRef = when {
        useBackupYoutube && exercise.backupYoutubeId.isNotBlank() -> exercise.backupYoutubeId
        else -> exercise.videoRef
    }
    val startSec = if (useBackupYoutube) exercise.backupStartSec else exercise.loopStartSec
    val endSec = if (useBackupYoutube) exercise.backupEndSec else exercise.loopEndSec
    val playbackSpeed = if (slowHalfSpeed) 0.5f else 1.0f

    if (effectiveType == VideoStreamType.NONE || effectiveRef.isBlank()) {
        LinktreeBentoCard(modifier = modifier) {
            LinktreeMonoChip("FORM GUIDE CUES", ForgePalette.Forest, ForgePalette.Chartreuse)
            Spacer(modifier = Modifier.height(8.dp))
            Text(exercise.cues, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(10.dp))
            LinktreeOutlinePillButton("Add Video Link", onClick = onOpenVideoManager)
        }
        return
    }

    @Composable
    fun PlayerSurface(inDialog: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(if (inDialog) 0.dp else 24.dp))
                .background(ForgePalette.TrueBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(ForgePalette.TrueBlack)
            ) {
                if (effectiveType == VideoStreamType.MP4) {
                    ExoClipLoopSurface(
                        url = effectiveRef,
                        startSec = startSec,
                        endSec = endSec,
                        isPlaying = isPlaying,
                        isLooping = isLooping,
                        isMuted = isMuted,
                        speed = playbackSpeed,
                        onStreamError = {
                            if (exercise.backupYoutubeId.isNotBlank()) {
                                useBackupYoutube = true
                            }
                        }
                    )
                } else {
                    YouTubeSegmentLoopSurface(
                        videoId = effectiveRef,
                        startSec = startSec,
                        endSec = endSec,
                        isPlaying = isPlaying,
                        isLooping = isLooping,
                        isMuted = isMuted,
                        slowHalfSpeed = slowHalfSpeed
                    )
                }

                LinktreeMonoChip(
                    text = if (slowHalfSpeed) "0.5X FORM LOOP" else "AUTO-LOOP GUIDE",
                    backgroundColor = ForgePalette.TrueBlack.copy(alpha = 0.72f),
                    contentColor = ForgePalette.Chartreuse,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
            }

            // Player Tactile Control Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E110E))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isPlaying = !isPlaying }) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause video" else "Play video",
                            tint = ForgePalette.Chartreuse
                        )
                    }
                    IconButton(onClick = { isLooping = !isLooping }) {
                        Icon(
                            imageVector = Icons.Default.Loop,
                            contentDescription = "Toggle loop",
                            tint = if (isLooping) ForgePalette.Canopy else Color.Gray
                        )
                    }
                    IconButton(onClick = { isMuted = !isMuted }) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute video" else "Mute video",
                            tint = Color.White
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { slowHalfSpeed = !slowHalfSpeed }) {
                        Text(
                            text = if (slowHalfSpeed) "0.5x" else "1.0x",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (slowHalfSpeed) ForgePalette.Chartreuse else Color.White,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (exercise.backupYoutubeId.isNotBlank() && exercise.videoType == VideoStreamType.MP4) {
                        IconButton(onClick = { useBackupYoutube = !useBackupYoutube }) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Switch stream source",
                                tint = ForgePalette.Sky
                            )
                        }
                    }
                    IconButton(onClick = { isFullscreen = !inDialog }) {
                        Icon(
                            imageVector = if (inDialog) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (inDialog) "Exit fullscreen" else "Fullscreen",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ForgePalette.TrueBlack),
                contentAlignment = Alignment.Center
            ) {
                PlayerSurface(inDialog = true)
            }
        }
    } else {
        Box(modifier = modifier) {
            PlayerSurface(inDialog = false)
        }
    }
}

@Composable
private fun ExoClipLoopSurface(
    url: String,
    startSec: Float,
    endSec: Float,
    isPlaying: Boolean,
    isLooping: Boolean,
    isMuted: Boolean,
    speed: Float,
    onStreamError: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(url, startSec, endSec) {
        ExoPlayer.Builder(context).build().apply {
            val builder = MediaItem.Builder().setUri(url)
            if (endSec > startSec && endSec > 0f) {
                builder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs((startSec * 1000f).toLong())
                        .setEndPositionMs((endSec * 1000f).toLong())
                        .build()
                )
            }
            setMediaItem(builder.build())
            repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (isMuted) 0f else 1f
            playbackParameters = PlaybackParameters(speed)
            playWhenReady = isPlaying
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    onStreamError()
                }
            })
            prepare()
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }
    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }
    LaunchedEffect(speed) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                player = exoPlayer
            }
        },
        update = { view ->
            if (view.player !== exoPlayer) view.player = exoPlayer
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun YouTubeSegmentLoopSurface(
    videoId: String,
    startSec: Float,
    endSec: Float,
    isPlaying: Boolean,
    isLooping: Boolean,
    isMuted: Boolean,
    slowHalfSpeed: Boolean
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var ytPlayerRef by remember(videoId) { mutableStateOf<YouTubePlayer?>(null) }

    LaunchedEffect(ytPlayerRef, isPlaying) {
        val p = ytPlayerRef ?: return@LaunchedEffect
        if (isPlaying) p.play() else p.pause()
    }
    LaunchedEffect(ytPlayerRef, isMuted) {
        val p = ytPlayerRef ?: return@LaunchedEffect
        if (isMuted) p.mute() else p.unMute()
    }
    LaunchedEffect(ytPlayerRef, slowHalfSpeed) {
        val p = ytPlayerRef ?: return@LaunchedEffect
        p.setPlaybackRate(
            if (slowHalfSpeed) PlayerConstants.PlaybackRate.RATE_0_5 else PlayerConstants.PlaybackRate.RATE_1
        )
    }

    androidx.compose.runtime.key(videoId) {
        var viewInstance by remember { mutableStateOf<YouTubePlayerView?>(null) }
        DisposableEffect(lifecycleOwner) {
            onDispose {
                viewInstance?.let { v ->
                    lifecycleOwner.lifecycle.removeObserver(v)
                    v.release()
                }
            }
        }
        AndroidView(
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    viewInstance = this
                    enableAutomaticInitialization = false
                    lifecycleOwner.lifecycle.addObserver(this)
                    val options = IFramePlayerOptions.Builder()
                        .controls(0)
                        .rel(0)
                        .build()
                    initialize(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                ytPlayerRef = youTubePlayer
                                if (isMuted) youTubePlayer.mute() else youTubePlayer.unMute()
                                youTubePlayer.loadVideo(videoId, startSec.coerceAtLeast(0f))
                                youTubePlayer.setPlaybackRate(
                                    if (slowHalfSpeed) PlayerConstants.PlaybackRate.RATE_0_5 else PlayerConstants.PlaybackRate.RATE_1
                                )
                            }

                            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                                if (isLooping && endSec > startSec && second >= endSec) {
                                    youTubePlayer.seekTo(startSec)
                                }
                            }

                            override fun onStateChange(
                                youTubePlayer: YouTubePlayer,
                                state: PlayerConstants.PlayerState
                            ) {
                                if (state == PlayerConstants.PlayerState.ENDED && isLooping) {
                                    youTubePlayer.seekTo(startSec)
                                    youTubePlayer.play()
                                }
                            }
                        },
                        options
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
