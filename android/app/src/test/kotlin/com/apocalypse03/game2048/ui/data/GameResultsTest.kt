package com.apocalypse03.game2048.ui.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Result list format, sanitising, ordering and truncation (DESIGN_SPEC section 16). */
class GameResultsTest {
    private fun r(id: Long, score: Int, tile: Int = 8, moves: Int = 10, timeMs: Long = 1_000L) =
        GameResult(id, score, tile, moves, timeMs)

    @Test
    fun encodeDecodeRoundTrips() {
        val list = listOf(r(3, 500, 64, 40, 61_000), r(1, 100, 16, 12, 9_000))
        val text = GameResults.encode(list)
        assertEquals("3,500,64,40,61000;1,100,16,12,9000", text)
        assertEquals(list, GameResults.decode(text))
    }

    @Test
    fun nullOrBlankIsEmpty() {
        assertTrue(GameResults.decode(null).isEmpty())
        assertTrue(GameResults.decode("").isEmpty())
    }

    @Test
    fun corruptEntriesAreSkipped() {
        val text = listOf(
            "1,10,8,3,100", // ok
            "garbage",
            "2,-1,8,3,5", // negative score
            "3,10,6,3,5", // tile not a power of two
            "4,10,1,3,5", // tile below 2
            "5,10,8,3", // too few fields
            "6,10,8,3,5,9", // too many fields
            "-7,10,8,3,5", // negative id
            "8,10,8,-3,5", // negative moves
            "9,10,8,3,-5", // negative time
            "10,x,8,3,5",
            "11,20,16,2,7", // ok
        ).joinToString(";")
        assertEquals(listOf(11L, 1L), GameResults.decode(text).map { it.id })
    }

    @Test
    fun orderIsScoreThenTileThenMovesThenOlder() {
        val list = listOf(
            r(1, 100, tile = 8, moves = 5),
            r(2, 200, tile = 8, moves = 5),
            r(3, 100, tile = 16, moves = 5),
            r(4, 100, tile = 8, moves = 3),
            r(5, 100, tile = 8, moves = 5),
        )
        val sorted = list.fold(emptyList<GameResult>(), GameResults::upsert)
        assertEquals(listOf(2L, 3L, 4L, 1L, 5L), sorted.map { it.id })
    }

    @Test
    fun keepsTop20AndUpsertsById() {
        var list = emptyList<GameResult>()
        for (i in 1..20) list = GameResults.upsert(list, r(i.toLong(), 1_000 + i))
        list = GameResults.upsert(list, r(21, 5))
        assertEquals(20, list.size)
        assertTrue(list.none { it.id == 21L })
        list = GameResults.upsert(list, r(22, 5_000))
        assertEquals(20, list.size)
        assertEquals(22L, list.first().id)
        assertTrue(list.none { it.id == 1L }) // lowest score dropped
        list = GameResults.upsert(list, r(22, 10))
        assertEquals(19, list.indexOfFirst { it.id == 22L })
        assertEquals(1, list.count { it.id == 22L })
    }

    @Test
    fun decodeOrdersAndTruncates() {
        val text = (1..25).joinToString(";") { "$it,$it,8,1,1" }
        val list = GameResults.decode(text)
        assertEquals(20, list.size)
        assertEquals(25L, list.first().id)
        assertEquals(6L, list.last().id)
    }
}
