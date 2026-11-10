package com.beatsbybony.tvguide

data class Program(
    val id: String,
    val title: String,
    val url: String,
    val durationMs: Long
)

data class Airing(
    val program: Program,
    val startMs: Long,
    val endMs: Long
)

class Channel(
    val number: Int,
    val name: String,
    val query: String
) {
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
