package com.beatsbybony.tvguide

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var web: YouTubeView
    private lateinit var guide: GuideView
    private lateinit var loader: ChannelLoader
    private var player: ExoPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var channels: List<Channel> = emptyList()
    private var playingIndex = -1
    private var currentProgram: Program? = null
    private var guideOpen = true
    private var ytActive = false
    private var videoAspect = 16f / 9f
    private var errorCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            guide.tick()
            handler.postDelayed(this, 1000)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) errorCount = 0
            if (playbackState == Player.STATE_ENDED) advance()
        }

        override fun onPlayerError(error: PlaybackException) {
            errorCount++
            if (errorCount > 5) {
                guide.showBanner("NO SIGNAL", "PLEASE STAND BY", 5000)
                player?.stop()
                return
            }
            val pl = player
            if (pl != null && pl.hasNextMediaItem()) {
                // one bad video in a music mix: skip to the next one
                pl.seekToNextMediaItem()
                pl.prepare()
                pl.playWhenReady = true
            } else {
                advance()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = currentProgram ?: return
            if (p.tracks.size > 1 && mediaItem != null && playingIndex >= 0) {
                val ch = channels[playingIndex]
                guide.showBanner(
                    "CH " + ch.number + "  " + ch.name,
                    "NOW PLAYING: " + mediaItem.mediaId.uppercase(Locale.US),
                    6000
                )
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                layoutVideo()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        channels = ChannelConfig.load(this)

        root = FrameLayout(this)
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(1, 1))
        web = YouTubeView(this)
        web.visibility = View.GONE
        root.addView(web, FrameLayout.LayoutParams(1, 1))
        guide = GuideView(this)
        guide.channels = channels
        root.addView(
            guide,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)

        guide.onLayoutChanged = { root.post { layoutVideo() } }
        web.onPlaying = { title -> onYoutubePlaying(title) }
        web.onFailed = { reason -> youtubeFailed(reason) }

        val exo = ExoPlayer.Builder(this).build()
        exo.setVideoSurfaceView(surface)
        exo.addListener(listener)
        player = exo

        loader = ChannelLoader { runOnUiThread { onChannelsUpdated() } }
        loader.load(channels)
        handler.post { onChannelsUpdated() }
        Weather.startPolling { guide.postInvalidate() }
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
        if (ytActive) {
            web.onResume()
            web.playVideo()
        } else if (playingIndex >= 0) {
            player?.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
        player?.playWhenReady = false
        if (ytActive) {
            web.pauseVideo()
            web.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        root.removeView(web)
        web.destroy()
    }

    // ---------- channels & playback ----------

    private fun onChannelsUpdated() {
        if (playingIndex < 0) {
            val first = channels.indexOfFirst { it.programs.isNotEmpty() }
            if (first >= 0) {
                guide.resetToNow(first)
                tune(first)
            }
        } else {
            // A YouTube channel that fell back to the Archive has just finished loading.
            val ch = channels[playingIndex]
            val cur = currentProgram
            if (ch.youtubeFailed && cur != null && cur.youtubeId != null &&
                ch.programs.isNotEmpty() && ch.programs[0].youtubeId == null
            ) {
                tune(playingIndex)
            }
        }
        guide.invalidate()
    }

    private fun tune(index: Int) {
        if (index < 0 || index >= channels.size) return
        val ch = channels[index]
        val now = System.currentTimeMillis()
        val a = ch.airingAt(now)
        if (a == null) {
            guide.showBanner(
                "CH " + ch.number + "  " + ch.name,
                if (ch.loaded) "NO SIGNAL" else "LOADING...",
                4000
            )
            return
        }
        playingIndex = index
        errorCount = 0
        val yt = a.program.youtubeId
        if (yt != null) {
            currentProgram = a.program
            player?.stop()
            setYoutubeActive(true)
            web.playPlaylist(yt)
        } else {
            setYoutubeActive(false)
            play(a.program, now - a.startMs)
        }
        guide.showBanner(
            "CH " + ch.number + "  " + ch.name,
            a.program.title.uppercase(Locale.US),
            5000
        )
    }

    private fun setYoutubeActive(active: Boolean) {
        val was = ytActive
        ytActive = active
        web.visibility = if (active) View.VISIBLE else View.GONE
        surface.visibility = if (active) View.GONE else View.VISIBLE
        if (was && !active) web.stop()
        layoutVideo()
    }

    private fun onYoutubePlaying(title: String) {
        if (!ytActive || playingIndex < 0 || title.isBlank()) return
        val ch = channels[playingIndex]
        guide.showBanner(
            "CH " + ch.number + "  " + ch.name,
            "NOW PLAYING: " + title.uppercase(Locale.US),
            6000
        )
    }

    /**
     * YouTube wouldn't play this playlist (usually because the uploader blocks embedding).
     * If the channel has another playlist, use that; otherwise fall back to Internet Archive mixes.
     */
    private fun youtubeFailed(reason: String) {
        if (!ytActive || playingIndex < 0) return
        val ch = channels[playingIndex]
        val cur = currentProgram
        val remaining = ArrayList<Program>()
        for (p in ch.programs) {
            if (p.youtubeId != null && (cur == null || p.id != cur.id)) remaining.add(p)
        }
        if (remaining.isNotEmpty()) {
            ch.programs = remaining
            tune(playingIndex)
            guide.showBanner(
                "CH " + ch.number + "  " + ch.name,
                "PLAYLIST SKIPPED (" + reason + ")",
                8000
            )
            return
        }
        if (ch.youtubeFailed) return
        ch.youtubeFailed = true
        setYoutubeActive(false)
        ch.programs = emptyList()
        ch.loaded = false
        guide.showBanner(
            "YOUTUBE FAILED: " + reason,
            "LOADING ARCHIVE VIDEOS INSTEAD...",
            10000
        )
        loader.loadArchiveFallback(ch)
        guide.invalidate()
    }

    private fun play(p: Program, offsetMs: Long) {
        currentProgram = p
        val pl = player ?: return
        // Work out which track of the block should be on right now, and how far in.
        var remaining = if (offsetMs > 0L) offsetMs else 0L
        var index = 0
        while (index < p.tracks.size - 1 && remaining >= p.tracks[index].durationMs) {
            remaining -= p.tracks[index].durationMs
            index++
        }
        val items = ArrayList<MediaItem>()
        for (t in p.tracks) {
            items.add(MediaItem.Builder().setUri(t.url).setMediaId(t.title).build())
        }
        pl.setMediaItems(items, index, remaining)
        pl.prepare()
        pl.playWhenReady = true
    }

    /** Moves on after a program ends or fails, keeping the channel "live". */
    private fun advance() {
        if (playingIndex < 0 || ytActive) return
        val ch = channels[playingIndex]
        val now = System.currentTimeMillis()
        val a = ch.airingAt(now) ?: return
        val cur = currentProgram
        if (cur != null && a.program.id == cur.id) {
            val list = ch.programs
            val i = list.indexOfFirst { it.id == cur.id }
            play(list[(i + 1) % list.size], 0L)
        } else {
            play(a.program, now - a.startMs)
        }
    }

    private fun surf(dir: Int) {
        if (channels.isEmpty() || playingIndex < 0) return
        var i = playingIndex
        for (n in 0 until channels.size) {
            i = (i + dir + channels.size) % channels.size
            if (channels[i].programs.isNotEmpty()) {
                tune(i)
                return
            }
        }
    }

    private fun setGuide(open: Boolean) {
        guideOpen = open
        guide.guideVisible = open
        if (open) guide.resetToNow(if (playingIndex >= 0) playingIndex else guide.selRow)
        layoutVideo()
        guide.invalidate()
    }

    private fun layoutVideo() {
        val gw = guide.width
        val gh = guide.height
        if (gw == 0 || gh == 0) return
        val box = if (guideOpen) Rect(guide.videoRect) else Rect(0, 0, gw, gh)
        val bw = box.width()
        val bh = box.height()
        if (bw <= 0 || bh <= 0) return
        val aspect = if (ytActive) 16f / 9f else videoAspect
        var w = bw.toFloat()
        var h = w / aspect
        if (h > bh) {
            h = bh.toFloat()
            w = h * aspect
        }
        val lp = FrameLayout.LayoutParams(w.toInt(), h.toInt())
        lp.leftMargin = box.left + (bw - w.toInt()) / 2
        lp.topMargin = box.top + (bh - h.toInt()) / 2
        if (ytActive) web.layoutParams = lp else surface.layoutParams = lp
    }

    // ---------- remote control ----------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // A held button must not toggle the guide back and forth.
        val isToggleKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU
        if (isToggleKey && event != null && event.repeatCount > 0) return true
        val handled = if (guideOpen) guideKey(keyCode) else tvKey(keyCode)
        return handled || super.onKeyDown(keyCode, event)
    }

    private fun guideKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> guide.moveRow(-1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> guide.moveRow(1)
            KeyEvent.KEYCODE_DPAD_LEFT -> guide.moveTime(-1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> guide.moveTime(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (guide.selRow != playingIndex) tune(guide.selRow)
                if (playingIndex >= 0) setGuide(false)
            }
            KeyEvent.KEYCODE_MENU -> if (playingIndex >= 0) setGuide(false)
            KeyEvent.KEYCODE_BACK -> finish()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> togglePause()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> skip(1)
            KeyEvent.KEYCODE_MEDIA_REWIND -> skip(-1)
            else -> return false
        }
        return true
    }

    private fun tvKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> surf(-1)
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> surf(1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> setGuide(true)
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> togglePause()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> skip(1)
            KeyEvent.KEYCODE_MEDIA_REWIND -> skip(-1)
            else -> return false
        }
        return true
    }

    private fun togglePause() {
        if (ytActive) {
            web.togglePause()
            return
        }
        val pl = player ?: return
        pl.playWhenReady = !pl.playWhenReady
    }

    /** Fast-forward / rewind: next or previous video on YouTube channels, +/-30s elsewhere. */
    private fun skip(dir: Int) {
        if (ytActive) {
            if (dir > 0) web.next() else web.prev()
            return
        }
        val pl = player ?: return
        val target = pl.currentPosition + dir * 30000L
        pl.seekTo(if (target > 0L) target else 0L)
    }
}
