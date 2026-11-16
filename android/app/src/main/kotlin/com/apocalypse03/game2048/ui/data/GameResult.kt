package com.apocalypse03.game2048.ui.data

/**
 * One finished game (DESIGN_SPEC section 16): its game [id], final [score], highest [tile] on the
 * board, hidden move count [moves] and game time [timeMs]. The mode is the list it is kept in.
 */
data class GameResult(val id: Long, val score: Int, val tile: Int, val moves: Int, val timeMs: Long)

/** Stored format and ordering of a mode's result list (DESIGN_SPEC section 16). */
object GameResults {
    /** Entries kept per mode. */
    const val MAX = 20

    /** Score desc, then tile desc, then moves asc, then the older entry (lower id) first. */
    val ORDER: Comparator<GameResult> = compareByDescending<GameResult> { it.score }
        .thenByDescending { it.tile }
        .thenBy { it.moves }
        .thenBy { it.id }

    /** Entries `id,score,tile,moves,timeMs` joined by `;`. */
    fun encode(results: List<GameResult>): String =
        results.joinToString(";") { "${it.id},${it.score},${it.tile},${it.moves},${it.timeMs}" }

    /**
     * Parses [text] (null or blank = no results). An entry is skipped if it does not parse, has a
     * negative value, or a tile that is not a power of two >= 2. The rest is ordered and truncated.
     */
    fun decode(text: String?): List<GameResult> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(";").mapNotNull(::parse).sortedWith(ORDER).take(MAX)
    }

    /** [results] with [result] inserted, or replacing the entry with its id; ordered, top [MAX] kept. */
    fun upsert(results: List<GameResult>, result: GameResult): List<GameResult> =
        (results.filter { it.id != result.id } + result).sortedWith(ORDER).take(MAX)

    private fun parse(entry: String): GameResult? {
        val p = entry.split(",")
        if (p.size != 5) return null
        val id = p[0].toLongOrNull() ?: return null
        val score = p[1].toIntOrNull() ?: return null
        val tile = p[2].toIntOrNull() ?: return null
        val moves = p[3].toIntOrNull() ?: return null
        val timeMs = p[4].toLongOrNull() ?: return null
        if (id < 0 || score < 0 || moves < 0 || timeMs < 0) return null
        if (tile < 2 || tile and (tile - 1) != 0) return null
        return GameResult(id, score, tile, moves, timeMs)
    }
}
