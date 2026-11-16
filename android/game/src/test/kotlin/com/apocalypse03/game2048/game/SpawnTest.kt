package com.apocalypse03.game2048.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SpawnTest {
    private val fixed = Board.of(
        listOf(listOf(2, 4, 8, 16), listOf(32, 64, 128, 256), listOf(4, 8, 0, 0), listOf(0, 0, 0, 0)),
    )

    @Test
    fun spawnDistribution() {
        val rng = Random(20261022)
        val trials = 100_000
        val counts = HashMap<Pair<Int, Int>, Int>()
        var twos = 0
        var fours = 0
        repeat(trials) {
            val after = Rules.spawn(fixed, rng)
            val diff = (0 until 4).flatMap { r -> (0 until 4).map { c -> r to c } }
                .filter { (r, c) -> after[r, c] != fixed[r, c] }
            assertEquals(1, diff.size)
            val (r, c) = diff.single()
            assertEquals(0, fixed[r, c])
            counts.merge(diff.single(), 1, Int::plus)
            when (after[r, c]) { 2 -> twos++; 4 -> fours++; else -> error("bad value") }
        }
        assertEquals(fixed.emptyCells().toSet(), counts.keys)
        val expected = trials / 6.0
        val chi = counts.values.sumOf { (it - expected) * (it - expected) / expected }
        println("CHI-SQUARE spawn (df=5): $chi counts=$counts twos=$twos fours=$fours")
        assertTrue("chi2=$chi", chi < 11.07)
        assertEquals(0.9, twos.toDouble() / trials, 0.005)
        assertEquals(trials, twos + fours)
    }

    @Test(expected = IllegalArgumentException::class)
    fun spawnOnFullBoardThrows() {
        Rules.spawn(Board.of(List(4) { List(4) { 2 } }), Random(1))
    }

    @Test
    fun newGameBoardsHaveTwoTwosUniformly() {
        val rng = Random(777)
        val n = 20_000
        val occ = IntArray(16)
        repeat(n) {
            val b = Rules.newGameBoard(rng)
            val flat = b.toIntArray()
            val tiles = flat.indices.filter { flat[it] != 0 }
            assertEquals(2, tiles.size)
            tiles.forEach { assertEquals(2, flat[it]); occ[it]++ }
        }
        val expected = n * 2 / 16.0
        val chi = occ.sumOf { (it - expected) * (it - expected) / expected }
        println("CHI-SQUARE newgame (df=15): $chi")
        assertTrue("chi2=$chi", chi < 25.0)
    }
}
