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
 * embedded are skipped; if the whole playlist fails, [onFailed] is called so the
 * app can fall back to another source.
 */
@SuppressLint("SetJavaScriptEnabled")
class YouTubeView(context: Context) : WebView(context) {

    /** Called with the title each time a new video starts playing. */
    var onPlaying: ((String) -> Unit)? = null

    /** Called when the playlist can't be played at all. */
    var onFailed: (() -> Unit)? = null

    private val ui = Handler(Looper.getMainLooper())

    inner class Bridge {
        @JavascriptInterface
        fun playing(title: String) {
            ui.post { onPlaying?.invoke(title) }
        }

        @JavascriptInterface
        fun failed(code: Int) {
            ui.post { onFailed?.invoke() }
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
        webViewClient = WebViewClient()
    }

    fun playPlaylist(playlistId: String) {
        loadDataWithBaseURL(
            "https://www.youtube.com",
            PAGE.replace("__LIST__", playlistId),
            "text/html",
            "utf-8",
            null
        )
    }

    fun stop() {
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
    playerVars: {
      listType: 'playlist', list: '__LIST__', autoplay: 1, controls: 0, rel: 0,
      playsinline: 1, modestbranding: 1, iv_load_policy: 3, fs: 0, disablekb: 1,
      loop: 1, origin: 'https://www.youtube.com'
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
        if (errs > 4 || e.data === 2 || e.data === 100) {
          Android.failed(e.data);
        } else {
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
