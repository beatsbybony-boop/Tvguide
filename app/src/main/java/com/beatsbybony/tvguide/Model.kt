package com.beatsbybony.tvguide

/** One playable video file. */
data class Track(
    val title: String,
    val url: String,
    val durationMs: Long
)

/**
 * One slot in the guide. Usually a single video, but music channels group
 * several short videos into a half-hour "mix" block.
 */
class Program(
    val id: String,
    val title: String,
    val tracks: List<Track>,
    /** Set for programs that play a YouTube playlist instead of Archive files. */
    val youtubeId: String? = null,
    fixedDurationMs: Long = 0L
) {
    val durationMs: Long

    init {
        if (youtubeId != null) {
            durationMs = fixedDurationMs
        } else {
            var total = 0L
            for (t in tracks) total += t.durationMs
            durationMs = total
        }
    }
}

data class YtList(
    val id: String,
    val title: String
)

data class Airing(
    val program: Program,
    val startMs: Long,
    val endMs: Long
)

class Channel(
    val number: Int,
    val name: String,
    val queries: List<String>,
    val mix: Boolean,
    /** YouTube playlists for this channel (tried first); queries are the Archive fallback. */
    val youtube: List<YtList> = emptyList(),
    /** Merge all of the YouTube playlists into one shuffled stream instead of alternating blocks. */
    val combine: Boolean = false
) {
    /** True once YouTube could not be played and the channel switched to the Archive. */
    @Volatile
    var youtubeFailed: Boolean = false

    @Volatile
    var archivePrograms: List<Program> = emptyList()

    /** Replaced in one go once the channel has finished loading. */
    @Volatile
    var programs: List<Program> = emptyList()

    @Volatile
    var loaded: Boolean = false

    /**
     * Channels "air" a fixed loop of programs, anchored to the Unix epoch, so
     * whatever is on at a given moment is the same every time you look.
     */
    fun airingAt(timeMs: Long): Airing? {
        val list = programs
        if (list.isEmpty()) return null
        var total = 0L
        for (p in list) total += p.durationMs
        if (total <= 0L) return null
        val pos = ((timeMs % total) + total) % total
        var acc = 0L
        for (p in list) {
            if (pos < acc + p.durationMs) {
                val start = timeMs - (pos - acc)
                return Airing(p, start, start + p.durationMs)
            }
            acc += p.durationMs
        }
        return null
    }
}
