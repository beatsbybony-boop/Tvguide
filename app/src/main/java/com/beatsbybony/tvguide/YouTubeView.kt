package com.beatsbybony.tvguide

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Plays a YouTube playlist through YouTube's own embedded player (the official
 * IFrame API), shuffled and starting at a random video. Videos that can't be
 * embedded are skipped; if the whole playlist fails, [onFailed] is called with a
 * short reason so the app can fall back to another source.
 *
 * YouTube requires embedded players to identify the app: the page's base URL (which
 * becomes the HTTP Referer) must be https:// followed by the app ID.
 */
@SuppressLint("SetJavaScriptEnabled")
class YouTubeView(context: Context) : WebView(context) {

    /** Called with the title each time a new video starts playing. */
    var onPlaying: ((String) -> Unit)? = null

    /** Called with a short reason when the playlist can't be played at all. */
    var onFailed: ((String) -> Unit)? = null

    private val ui = Handler(Looper.getMainLooper())
    private val baseUrl = "https://" + context.packageName

    /** If nothing starts playing within this time, treat the playlist as failed. */
    private val watchdog = Runnable { onFailed?.invoke("TIMEOUT - NOTHING PLAYED") }

    inner class Bridge {
        @JavascriptInterface
        fun playing(title: String) {
            ui.post {
                ui.removeCallbacks(watchdog)
                onPlaying?.invoke(title)
            }
        }

        @JavascriptInterface
        fun failed(code: Int) {
            ui.post {
                ui.removeCallbacks(watchdog)
                onFailed?.invoke("YOUTUBE ERROR " + code)
            }
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = false
        isFocusableInTouchMode = false
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        addJavascriptInterface(Bridge(), "Android")
        webViewClient = object : WebViewClient() {
            // For the main page only (on Android 6+ this is not called for sub-resources).
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                if (failingUrl == null || failingUrl.startsWith(baseUrl)) {
                    ui.removeCallbacks(watchdog)
                    onFailed?.invoke("PAGE ERROR " + (description ?: errorCode.toString()))
                }
            }
        }
    }

    fun playPlaylist(playlistId: String) {
        ui.removeCallbacks(watchdog)
        ui.postDelayed(watchdog, 25000L)
        loadDataWithBaseURL(
            baseUrl,
            PAGE.replace("__LIST__", playlistId).replace("__ORIGIN__", baseUrl),
            "text/html",
            "utf-8",
            null
        )
    }

    fun stop() {
        ui.removeCallbacks(watchdog)
        loadUrl("about:blank")
    }

    fun togglePause() {
        evaluateJavascript(
            "if (window.player && player.getPlayerState) { if (player.getPlayerState() === 1) player.pauseVideo(); else player.playVideo(); }",
            null
        )
    }

    fun pauseVideo() {
        evaluateJavascript("if (window.player && player.pauseVideo) player.pauseVideo();", null)
    }

    fun playVideo() {
        evaluateJavascript("if (window.player && player.playVideo) player.playVideo();", null)
    }

    fun next() {
        evaluateJavascript("if (window.player && player.nextVideo) player.nextVideo();", null)
    }

    fun prev() {
        evaluateJavascript("if (window.player && player.previousVideo) player.previousVideo();", null)
    }

    companion object {
        private const val PAGE = """<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="referrer" content="strict-origin-when-cross-origin">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>
</head><body>
<div id="p"></div>
<script>
var player;
var errs = 0;
var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
document.head.appendChild(tag);
function onYouTubeIframeAPIReady() {
  player = new YT.Player('p', {
    width: '100%',
    height: '100%',
    host: 'https://www.youtube.com',
    playerVars: {
      listType: 'playlist', list: '__LIST__', autoplay: 1, controls: 0, rel: 0,
      playsinline: 1, modestbranding: 1, iv_load_policy: 3, fs: 0, disablekb: 1,
      loop: 1, origin: '__ORIGIN__'
    },
    events: {
      onReady: function (e) {
        try {
          e.target.setShuffle(true);
          var n = e.target.getPlaylist().length;
          e.target.playVideoAt(Math.floor(Math.random() * n));
        } catch (x) {}
        e.target.playVideo();
      },
      onStateChange: function (e) {
        if (e.data === 1) {
          errs = 0;
          var t = '';
          try { t = e.target.getVideoData().title; } catch (x) {}
          Android.playing(t || '');
        }
      },
      onError: function (e) {
        errs++;
        // 2 = bad request, 100 = playlist gone, 153 = player identity problem: no point skipping ahead
        if (errs > 4 || e.data === 2 || e.data === 100 || e.data === 153) {
          Android.failed(e.data);
        } else {
          // 101 / 150 / 152 = this one video can't be embedded: try the next one
          try { player.nextVideo(); } catch (x) { Android.failed(e.data); }
        }
      }
    }
  });
}
</script>
</body></html>"""
    }
}
