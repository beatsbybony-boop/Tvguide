package com.beatsbybony.tvguide

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Random
import java.util.concurrent.Executors

object Http {
    fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        conn.setRequestProperty("User-Agent", "TVGuideApp/1.0")
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP " + conn.responseCode)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

object ChannelConfig {
    /**
     * channels.json entries look like:
     *   { "number": 2, "name": "TOONS", "query": "collection:classic_cartoons" }
     *   { "number": 20, "name": "80S HIP HOP", "mix": true, "queries": ["...", "..."] }
     * "queries" are tried in order, so put the strictest search first.
     */
    fun load(ctx: Context): List<Channel> {
        val text = ctx.assets.open("channels.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val out = ArrayList<Channel>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val queries = ArrayList<String>()
            val qa = o.optJSONArray("queries")
            if (qa != null) {
                for (k in 0 until qa.length()) queries.add(qa.getString(k))
            } else if (o.has("query")) {
                queries.add(o.getString("query"))
            }
            out.add(Channel(o.getInt("number"), o.getString("name"), queries, o.optBoolean("mix", false)))
        }
        return out
    }
}

object Weather {
    @Volatile
    var text: String = ""

    fun startPolling(onUpdate: () -> Unit) {
        val t = Thread {
            while (true) {
                try {
                    val unit = if (Config.FAHRENHEIT) "fahrenheit" else "celsius"
                    val body = Http.get(
                        "https://api.open-meteo.com/v1/forecast?latitude=" + Config.LATITUDE +
                            "&longitude=" + Config.LONGITUDE +
                            "&current=temperature_2m&temperature_unit=" + unit
                    )
                    val temp = JSONObject(body).getJSONObject("current").getDouble("temperature_2m")
                    text = Math.round(temp).toString() + "°" + (if (Config.FAHRENHEIT) "F" else "C")
                    onUpdate()
                } catch (e: Exception) {
                    // keep the last known value
                }
                try {
                    Thread.sleep(15 * 60 * 1000L)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
        t.isDaemon = true
        t.start()
    }
}

/**
 * Fills each channel with programs from the Internet Archive.
 * A channel's programs are published all at once when it finishes loading,
 * so the schedule never shifts underneath the viewer.
 */
class ChannelLoader(private val onChanged: () -> Unit) {

    private val pool = Executors.newFixedThreadPool(6) { r ->
        val t = Thread(r)
        t.isDaemon = true
        t
    }

    fun load(channels: List<Channel>) {
        for (ch in channels) {
            pool.execute { loadChannel(ch) }
        }
    }

    private fun loadChannel(ch: Channel) {
        var result: List<Program> = emptyList()
        var attempt = 0
        while (attempt < 3 && result.isEmpty()) {
            if (attempt > 0) {
                try {
                    Thread.sleep(10000)
                } catch (e: InterruptedException) {
                    return
                }
            }
            result = tryLoad(ch)
            attempt++
        }
        ch.programs = result
        ch.loaded = true
        onChanged()
    }

    private fun tryLoad(ch: Channel): List<Program> {
        val found = ArrayList<Pair<String, Track>>()
        val want = if (ch.mix) Config.MIX_TRACKS else Config.ITEMS_PER_CHANNEL
        try {
            for (doc in search(ch)) {
                if (found.size >= want) break
                try {
                    val t = resolveTrack(doc.first, doc.second, ch.mix)
                    if (t != null) found.add(Pair(doc.first, t))
                } catch (e: Exception) {
                    // skip items that fail
                }
            }
        } catch (e: Exception) {
            // network problem: caller retries
        }
        if (ch.mix) return group(ch, found)
        val out = ArrayList<Program>()
        for (f in found) out.add(Program(f.first, f.second.title, listOf(f.second)))
        return out
    }

    /** Short music videos are bundled into ~30 minute blocks so they fit the guide grid. */
    private fun group(ch: Channel, items: List<Pair<String, Track>>): List<Program> {
        val out = ArrayList<Program>()
        var cur = ArrayList<Track>()
        var curMs = 0L
        var n = 1
        for (item in items) {
            val t = item.second
            if (t.durationMs >= 20 * 60_000L) {
                // a long compilation stands on its own
                if (cur.isNotEmpty()) {
                    out.add(Program(ch.number.toString() + "-mix-" + n, ch.name + " MIX " + n, cur))
                    n++
                    cur = ArrayList<Track>()
                    curMs = 0L
                }
                out.add(Program(item.first, t.title, listOf(t)))
                continue
            }
            cur.add(t)
            curMs += t.durationMs
            if (curMs >= 25 * 60_000L || cur.size >= 8) {
                out.add(Program(ch.number.toString() + "-mix-" + n, ch.name + " MIX " + n, cur))
                n++
                cur = ArrayList<Track>()
                curMs = 0L
            }
        }
        if (cur.isNotEmpty()) {
            out.add(Program(ch.number.toString() + "-mix-" + n, ch.name + " MIX " + n, cur))
        }
        return out
    }

    /**
     * Runs each of the channel's queries in order and returns the candidates,
     * strictest query first. Within a query the order is shuffled (stable for a
     * whole day) so the lineup is different from day to day.
     */
    private fun search(ch: Channel): List<Pair<String, String>> {
        val seed = (Calendar.getInstance().get(Calendar.DAY_OF_YEAR) * 100 + ch.number).toLong()
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for (query in ch.queries) {
            try {
                val docs = searchOne(query)
                for (d in docs.shuffled(Random(seed))) {
                    if (seen.add(d.first)) out.add(d)
                }
            } catch (e: Exception) {
                // try the next query
            }
            if (out.size >= 80) break
        }
        return out
    }

    private fun searchOne(query: String): List<Pair<String, String>> {
        val q = "(" + query + ") AND mediatype:movies AND NOT access-restricted-item:true"
        val url = "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(q, "UTF-8") +
            "&fl%5B%5D=identifier&fl%5B%5D=title" +
            "&sort%5B%5D=" + URLEncoder.encode("downloads desc", "UTF-8") +
            "&rows=60&page=1&output=json"
        val docs = JSONObject(Http.get(url)).getJSONObject("response").getJSONArray("docs")
        val out = ArrayList<Pair<String, String>>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val id = d.optString("identifier")
            if (id.isEmpty()) continue
            val t = d.opt("title")
            val title = when (t) {
                is JSONArray -> t.optString(0)
                null -> id
                else -> t.toString()
            }
            out.add(Pair(id, if (title.isBlank()) id else title))
        }
        return out
    }

    private fun resolveTrack(id: String, title: String, mix: Boolean): Track? {
        val json = JSONObject(Http.get("https://archive.org/metadata/" + id))
        val files = json.optJSONArray("files") ?: return null
        var best: JSONObject? = null
        var bestRank = Int.MAX_VALUE
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val name = f.optString("name")
            if (!name.endsWith(".mp4", ignoreCase = true)) continue
            if (f.optLong("size", 0L) > 1_500_000_000L) continue
            val rank = when (f.optString("format")) {
                "h.264" -> 0
                "h.264 HD" -> 1
                "MPEG4" -> 2
                "512Kb MPEG4" -> 3
                else -> 5
            }
            if (rank < bestRank) {
                best = f
                bestRank = rank
            }
        }
        val file = best ?: return null
        val seconds = parseLength(file.optString("length"))
        val durationMs = (if (seconds > 0.0) seconds else 1800.0) * 1000.0
        val maxMs = if (mix) 90 * 60 * 1000.0 else 4 * 3600 * 1000.0
        if (durationMs < 30_000.0 || durationMs > maxMs) return null
        val url = "https://archive.org/download/" + id + "/" + android.net.Uri.encode(file.getString("name"), "/")
        return Track(title, url, durationMs.toLong())
    }

    private fun parseLength(s: String): Double {
        if (s.isBlank()) return 0.0
        return try {
            if (s.contains(":")) {
                var total = 0.0
                for (part in s.split(":")) total = total * 60.0 + part.toDouble()
                total
            } else {
                s.toDouble()
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
