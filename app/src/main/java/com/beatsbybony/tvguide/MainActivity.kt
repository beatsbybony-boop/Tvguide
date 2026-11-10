package com.beatsbybony.tvguide

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.SurfaceView
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
    private lateinit var guide: GuideView
    private var player: ExoPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var channels: List<Channel> = emptyList()
    private var playingIndex = -1
    private var currentProgram: Program? = null
    private var guideOpen = true
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
            advance()
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
        guide = GuideView(this)
        guide.channels = channels
        root.addView(
            guide,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)

        guide.onLayoutChanged = { root.post { layoutVideo() } }

        val exo = ExoPlayer.Builder(this).build()
        exo.setVideoSurfaceView(surface)
        exo.addListener(listener)
        player = exo

        ChannelLoader { runOnUiThread { onChannelsUpdated() } }.load(channels)
        Weather.startPolling { guide.postInvalidate() }
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
        if (playingIndex >= 0) player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    // ---------- channels & playback ----------

    private fun onChannelsUpdated() {
        if (playingIndex < 0) {
            val first = channels.indexOfFirst { it.programs.isNotEmpty() }
            if (first >= 0) {
                guide.resetToNow(first)
                tune(first)
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
        play(a.program, now - a.startMs)
        guide.showBanner(
            "CH " + ch.number + "  " + ch.name,
            a.program.title.uppercase(Locale.US),
            5000
        )
    }

    private fun play(p: Program, offsetMs: Long) {
        currentProgram = p
        val pl = player ?: return
        pl.setMediaItem(MediaItem.fromUri(p.url), if (offsetMs > 0L) offsetMs else 0L)
        pl.prepare()
        pl.playWhenReady = true
    }

    /** Moves on after a program ends or fails, keeping the channel "live". */
    private fun advance() {
        if (playingIndex < 0) return
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
        var w = bw.toFloat()
        var h = w / videoAspect
        if (h > bh) {
            h = bh.toFloat()
            w = h * videoAspect
        }
        val lp = FrameLayout.LayoutParams(w.toInt(), h.toInt())
        lp.leftMargin = box.left + (bw - w.toInt()) / 2
        lp.topMargin = box.top + (bh - h.toInt()) / 2
        surface.layoutParams = lp
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
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekBy(30000L)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seekBy(-30000L)
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
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekBy(30000L)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seekBy(-30000L)
            else -> return false
        }
        return true
    }

    private fun togglePause() {
        val pl = player ?: return
        pl.playWhenReady = !pl.playWhenReady
    }

    private fun seekBy(deltaMs: Long) {
        val pl = player ?: return
        val target = pl.currentPosition + deltaMs
        pl.seekTo(if (target > 0L) target else 0L)
    }
}
