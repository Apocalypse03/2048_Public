# Golden tests (2048 oracle)

**Purpose:** freeze the behaviour of the Python game `2048_vertical.py` (the oracle) as JSON vectors and spawn statistics for the Kotlin tests.

**Command:**
- `python tools/golden/generate.py` writes the four files below.
- `python tools/golden/generate.py --verify` regenerates in memory. It prints `DIFF <file>` for each mismatch and exits 1, or prints `OK`.

**Outputs** (in `android/game/src/test/resources/golden/`):
- `moves.json`: `{board, dir, expected, gain, moved}` for 7 hand-written edge boards plus 3500 random boards, each tried in all 4 directions (14028 cases).
- `legal.json`: `{board, hasLegalMoves}` for 3007 boards (about 50% full, 751 False).
- `spawn_stats.json`: statistics for distribution tests, not exact vectors. `newgame` = 20000 `generate_tiles()` calls. `spawn` = 100000 real `end_tiles` calls on a board with 6 empty cells.
- `meta.json`: oracle SHA-256, Python and pygame versions, seed, counts.

**How stubbing works:** the oracle is loaded by file path with SDL dummy drivers, and pygame's banner is suppressed. `g.draw` is replaced by a no-op and the clock by a no-op object. For `moves.json`, `g.end_tiles` is replaced with `lambda t: "continue"` so no random tile spawns. The original is restored afterwards. Spawn statistics use the real `end_tiles` and `generate_tiles`, with the global `random` seeded.

**Determinism:** all randomness comes from seeded `random.Random` instances (seed 20261022; legal uses seed+1). Output is written as bytes with sorted keys, compact separators and an LF newline.

**Regenerate rule:** regenerate only when the oracle changes on purpose. Run `--verify` first. If it fails, check the Kotlin port against the new oracle, then regenerate and commit the golden files together with the change. `meta.json` records the Python and pygame versions, so `--verify` also fails on a machine with different versions even when the vectors match.
