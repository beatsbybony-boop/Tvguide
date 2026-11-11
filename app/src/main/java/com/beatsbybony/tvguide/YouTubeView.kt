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
 * Plays YouTube playlists through YouTube's own embedded player (the official
 * IFrame API), shuffled and starting at a random video. Given one playlist ID it
 * shuffles that playlist; given several IDs separated by commas it reads every video
 * from all of them, mixes them into one shuffled list and plays that. Videos that
 * can't be embedded are skipped; if nothing plays at all, [onFailed] is called with
 * a short reason so the app can fall back to another source.
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

    /** [playlistIds] is one playlist ID, or several separated by commas to combine them. */
    fun playPlaylist(playlistIds: String) {
        ui.removeCallbacks(watchdog)
        ui.postDelayed(watchdog, 40000L)
        loadDataWithBaseURL(
            baseUrl,
            PAGE.replace("__LIST__", playlistIds).replace("__ORIGIN__", baseUrl),
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
var LISTS = '__LIST__'.split(',');
var combine = LISTS.length > 1;
var collecting = false;
var collected = [];
var collectIdx = 0;
var collectToken = 0;

var tag = document.createElement('script');
tag.src = 'https://www.youtube.com/iframe_api';
document.head.appendChild(tag);

// An entry written v:VIDEOID is a single video; anything else is a playlist ID.
function isVid(s) { return s.indexOf('v:') === 0; }

function shuffle(a) {
  for (var i = a.length - 1; i > 0; i--) {
    var j = Math.floor(Math.random() * (i + 1));
    var t = a[i]; a[i] = a[j]; a[j] = t;
  }
  return a;
}

// Combined mode: load each playlist in turn just to read its video IDs.
function collectNext() {
  collectToken++;
  // single videos are added directly; playlists are read below
  while (collectIdx < LISTS.length && isVid(LISTS[collectIdx])) {
    collected.push(LISTS[collectIdx].substring(2));
    collectIdx++;
  }
  if (collectIdx >= LISTS.length) { finishCollect(); return; }
  var token = collectToken;
  try { player.cuePlaylist({ listType: 'playlist', list: LISTS[collectIdx] }); } catch (x) {}
  collectIdx++;
  setTimeout(function () {
    if (collecting && token === collectToken) collectNext();
  }, 9000);
}

function finishCollect() {
  collecting = false;
  var seen = {};
  var ids = [];
  for (var i = 0; i < collected.length; i++) {
    var v = collected[i];
    if (v && !seen[v]) { seen[v] = 1; ids.push(v); }
  }
  if (ids.length === 0) { Android.failed(100); return; }
  shuffle(ids);
  ids = ids.slice(0, 200);
  player.loadPlaylist(ids, 0, 0);
  try { player.setLoop(true); } catch (x) {}
}

function onYouTubeIframeAPIReady() {
  var vars = {
    autoplay: 1, controls: 0, rel: 0, playsinline: 1, modestbranding: 1,
    iv_load_policy: 3, fs: 0, disablekb: 1, origin: '__ORIGIN__'
  };
  if (!combine) {
    if (isVid(LISTS[0])) {
      vars.videoId = LISTS[0].substring(2);
    } else {
      vars.listType = 'playlist';
      vars.list = LISTS[0];
      vars.loop = 1;
    }
  }
  player = new YT.Player('p', {
    width: '100%',
    height: '100%',
    host: 'https://www.youtube.com',
    playerVars: vars,
    events: {
      onReady: function (e) {
        if (combine) {
          collecting = true;
          collectNext();
          return;
        }
        try {
          e.target.setShuffle(true);
          var n = e.target.getPlaylist().length;
          e.target.playVideoAt(Math.floor(Math.random() * n));
        } catch (x) {}
        e.target.playVideo();
      },
      onStateChange: function (e) {
        if (collecting && e.data === 5) {
          try {
            var got = e.target.getPlaylist();
            if (got) collected = collected.concat(got);
          } catch (x) {}
          collectNext();
          return;
        }
        if (e.data === 1) {
          errs = 0;
          var t = '';
          try { t = e.target.getVideoData().title; } catch (x) {}
          Android.playing(t || '');
        }
      },
      onError: function (e) {
        if (collecting) { collectNext(); return; }
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
