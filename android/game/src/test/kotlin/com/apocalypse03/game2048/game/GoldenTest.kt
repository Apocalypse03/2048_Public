package com.apocalypse03.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays oracle-generated fixtures (from 2048_vertical.py) against [Rules]. */
class GoldenTest {

    private fun resource(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/golden/$name")) { "missing $name" }
            .bufferedReader().use { it.readText() }

    /** Minimal tokenizer for the fixed fixture format: objects, arrays, ints, strings, bools. */
    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Any? {
            skipWs()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                else -> if (c == '-' || c.isDigit()) num() else error("bad char '$c' at $i")
            }
        }

        private fun skipWs() { while (s[i].isWhitespace()) i++ }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (s[i] == '}') { i++; return m }
            while (true) {
                skipWs()
                val k = str()
                skipWs()
                check(s[i++] == ':')
                m[k] = parse()
                skipWs()
                if (s[i++] == '}') return m
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            skipWs()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(parse())
                skipWs()
                if (s[i++] == ']') return l
            }
        }

        private fun str(): String {
            val start = ++i
            while (s[i] != '"') i++
            return s.substring(start, i++)
        }

        private fun num(): Int {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            return s.substring(start, i).toInt()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun cases(file: String): List<Map<String, Any?>> =
        ((Parser(resource(file)).parse() as Map<String, Any?>)["cases"] as List<Map<String, Any?>>)

    @Suppress("UNCHECKED_CAST")
    private fun boardOf(v: Any?): Board = Board.of(v as List<List<Int>>)

    @Test
    fun movesMatchOracle() {
        val cases = cases("moves.json")
        var mismatches = 0
        cases.forEachIndexed { idx, c ->
            val board = boardOf(c["board"])
            val dir = Direction.valueOf((c["dir"] as String).uppercase())
            val r = Rules.move(board, dir)
            val expected = boardOf(c["expected"])
            val ok = r.board == expected && r.gain == c["gain"] && r.moved == c["moved"]
            if (!ok) {
                if (mismatches == 0) {
                    println("FIRST MISMATCH idx=$idx board=${board.toRows()} dir=$dir " +
                        "expected=${expected.toRows()} gain=${c["gain"]} moved=${c["moved"]} " +
                        "actual=${r.board.toRows()} gain=${r.gain} moved=${r.moved}")
                }
                mismatches++
            }
        }
        println("GOLDEN moves cases asserted: ${cases.size}, mismatches: $mismatches")
        assertEquals(14028, cases.size)
        assertEquals("mismatching move cases", 0, mismatches)
    }

    @Test
    fun legalMovesMatchOracle() {
        val cases = cases("legal.json")
        var mismatches = 0
        cases.forEachIndexed { idx, c ->
            val board = boardOf(c["board"])
            if (Rules.hasLegalMoves(board) != c["hasLegalMoves"]) {
                if (mismatches == 0) {
                    println("FIRST MISMATCH idx=$idx board=${board.toRows()} expected=${c["hasLegalMoves"]}")
                }
                mismatches++
            }
        }
        println("GOLDEN legal cases asserted: ${cases.size}, mismatches: $mismatches")
        assertEquals(3007, cases.size)
        assertTrue("mismatching legal cases: $mismatches", mismatches == 0)
    }
}
