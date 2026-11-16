#!/usr/bin/env python3
"""Golden-test generator. Uses 2048_vertical.py as the oracle.

  python tools/golden/generate.py           write golden files
  python tools/golden/generate.py --verify  regenerate in memory, diff against disk
"""
import os

os.environ["PYGAME_HIDE_SUPPORT_PROMPT"] = "1"
os.environ["SDL_VIDEODRIVER"] = "dummy"
os.environ["SDL_AUDIODRIVER"] = "dummy"

import argparse
import hashlib
import importlib.util
import json
import pathlib
import random
import sys
import time
from collections import Counter

import pygame

REPO = pathlib.Path(__file__).resolve().parents[2]
ORACLE = REPO / "2048_vertical.py"
OUT_DIR = REPO / "android" / "game" / "src" / "test" / "resources" / "golden"
GENERATOR_ID = "tools/golden/generate.py v1"
SEED = 20261022
RANDOM_BOARDS = 3500
LEGAL_BOARDS = 3000
NEWGAME_TRIALS = 20000
SPAWN_TRIALS = 100000
DIRECTIONS = ["left", "right", "up", "down"]
VALUES = [2 ** e for e in range(1, 14)]  # 2 .. 8192
VALUE_WEIGHTS = [40, 25, 15, 8, 5, 3, 2, 1, 0.5, 0.3, 0.2, 0.1, 0.1]
MERGE_ROWS = [[2, 2, 2, 2], [4, 4, 8, 8], [2, 2, 4, 4], [8, 0, 8, 8]]
CATEGORIES = ["sparse", "medium", "dense", "full_merge", "full_nomerge", "merge_heavy"]


class NoClock:
    def tick(self, *a):
        pass


def load_oracle():
    spec = importlib.util.spec_from_file_location("orig2048", ORACLE)
    g = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(g)
    g.draw = lambda *a, **k: None  # in-memory stub: skip rendering
    return g


def to_tiles(g, grid):
    return {f"{r}{c}": g.Tile(grid[r][c], r, c)
            for r in range(4) for c in range(4) if grid[r][c]}


def from_tiles(tiles):
    grid = [[0] * 4 for _ in range(4)]
    for key, t in tiles.items():
        if key != f"{t.row}{t.col}":
            raise SystemExit(f"oracle key/position mismatch: {key} vs {t.row}{t.col}")
        grid[t.row][t.col] = t.value
    return grid


def weighted_value(rng):
    return rng.choices(VALUES, weights=VALUE_WEIGHTS)[0]


def weighted_distinct(rng, k):
    chosen = []
    while len(chosen) < k:
        v = weighted_value(rng)
        if v not in chosen:
            chosen.append(v)
    return chosen


def random_cells(rng, k):
    grid = [[0] * 4 for _ in range(4)]
    for idx in rng.sample(range(16), k):
        grid[idx // 4][idx % 4] = weighted_value(rng)
    return grid


def has_equal_pair(grid):
    for r in range(4):
        for c in range(4):
            if c < 3 and grid[r][c] == grid[r][c + 1]:
                return True
            if r < 3 and grid[r][c] == grid[r + 1][c]:
                return True
    return False


def gen_full_merge(rng):
    while True:
        grid = [[weighted_value(rng) for _ in range(4)] for _ in range(4)]
        if has_equal_pair(grid):
            return grid


def gen_full_nomerge(rng):
    # Greedy fill in row-major order; each cell differs from its up and left
    # neighbours, which covers every adjacent pair. Palette >= 5 > 2 constraints.
    palette = weighted_distinct(rng, rng.randint(5, 8))
    grid = [[0] * 4 for _ in range(4)]
    for r in range(4):
        for c in range(4):
            forbidden = set()
            if r > 0:
                forbidden.add(grid[r - 1][c])
            if c > 0:
                forbidden.add(grid[r][c - 1])
            allowed = [v for v in palette if v not in forbidden]
            grid[r][c] = rng.choice(allowed)
    return grid


def gen_merge_heavy(rng):
    grid = []
    for _ in range(4):
        if rng.random() < 0.2:
            row = [weighted_value(rng) if rng.random() < 0.5 else 0 for _ in range(4)]
        else:
            scale = 2 ** rng.randint(0, 10)  # max 8 * 1024 = 8192
            row = [v * scale if v else 0 for v in rng.choice(MERGE_ROWS)]
            if rng.random() < 0.5:
                row.reverse()
        grid.append(row)
    return grid


def gen_board(rng, cat):
    if cat == "sparse":
        return random_cells(rng, rng.randint(1, 4))
    if cat == "medium":
        return random_cells(rng, rng.randint(5, 11))
    if cat == "dense":
        return random_cells(rng, rng.randint(12, 15))
    if cat == "full_merge":
        return gen_full_merge(rng)
    if cat == "full_nomerge":
        return gen_full_nomerge(rng)
    return gen_merge_heavy(rng)


def edge_boards():
    empty = [[0] * 4 for _ in range(4)]
    out = [("edge_empty", empty)]
    for name, (r, c) in [("corner_tl", (0, 0)), ("corner_tr", (0, 3)),
                         ("corner_bl", (3, 0)), ("corner_br", (3, 3))]:
        grid = [[0] * 4 for _ in range(4)]
        grid[r][c] = 2
        out.append((f"edge_single_{name}", grid))
    out.append(("edge_all_two", [[2] * 4 for _ in range(4)]))
    out.append(("edge_checker_2_4",
                [[2 if (r + c) % 2 == 0 else 4 for c in range(4)] for r in range(4)]))
    return out


def dump(obj):
    return (json.dumps(obj, separators=(",", ":"), sort_keys=True) + "\n").encode("utf-8")


def check_moves(cases):
    for c in cases:
        if not c["moved"]:
            if c["expected"] != c["board"] or c["gain"] != 0:
                raise SystemExit(f"SANITY FAILED (moved=False but board changed or gain!=0): {c}")
        elif c["expected"] == c["board"]:
            raise SystemExit(f"SANITY FAILED (moved=True but board unchanged): {c}")


def build_moves(g, real_end):
    rng = random.Random(SEED)
    counts = Counter()
    edge = edge_boards()
    boards = [grid for _, grid in edge]
    counts["moves_boards_edge"] = len(edge)
    for i in range(RANDOM_BOARDS):
        cat = CATEGORIES[i % len(CATEGORIES)]
        boards.append(gen_board(rng, cat))
        counts[f"moves_boards_{cat}"] += 1
    for cat in CATEGORIES:
        if counts[f"moves_boards_{cat}"] < 0.10 * RANDOM_BOARDS:
            raise SystemExit(f"category {cat} below 10%")

    cases = []
    g.end_tiles = lambda tiles: "continue"  # no random spawn: pure oracle move
    try:
        for grid in boards:
            for d in DIRECTIONS:
                tiles = to_tiles(g, grid)
                status, gain, moved = g.move_tiles(None, tiles, NoClock(), d, 0, 0, None)
                if status != "continue":
                    raise SystemExit(f"unexpected oracle status {status!r}")
                cases.append({"board": grid, "dir": d, "expected": from_tiles(tiles),
                              "gain": int(gain), "moved": bool(moved)})
    finally:
        g.end_tiles = real_end

    check_moves(cases)
    for c in cases:
        counts["moves_moved_true" if c["moved"] else "moves_moved_false"] += 1
        before = sum(map(sum, c["board"]))
        after = sum(map(sum, c["expected"]))
        if before != after:
            counts["moves_sum_mismatch"] += 1
    counts["moves_cases"] = len(cases)
    return cases, counts


def build_legal(g):
    rng = random.Random(SEED + 1)
    boards = [grid for _, grid in edge_boards()]
    for i in range(LEGAL_BOARDS):
        if i % 2 == 0:  # 50% full boards, half without merges
            cat = "full_nomerge" if (i // 2) % 2 == 0 else "full_merge"
            boards.append(gen_board(rng, cat))
        else:
            boards.append(random_cells(rng, rng.randint(1, 15)))
    cases = []
    counts = Counter()
    for grid in boards:
        has = bool(g.has_legal_moves(to_tiles(g, grid)))
        cases.append({"board": grid, "hasLegalMoves": has})
        full = all(v for row in grid for v in row)
        counts["legal_full" if full else "legal_partial"] += 1
        counts["legal_true" if has else "legal_false"] += 1
    counts["legal_cases"] = len(cases)
    if counts["legal_false"] < 300:
        raise SystemExit("legal.json has fewer than 300 False cases")
    if counts["legal_full"] < 0.40 * len(cases):
        raise SystemExit("legal.json has fewer than 40% full boards")
    return cases, counts


def build_spawn(g, real_end):
    random.seed(SEED)
    counts = Counter()
    occ = [[0] * 4 for _ in range(4)]
    values = Counter()
    tile_counts = Counter()
    for _ in range(NEWGAME_TRIALS):
        tiles = g.generate_tiles()
        tile_counts[len(tiles)] += 1
        for t in tiles.values():
            values[t.value] += 1
            occ[t.row][t.col] += 1
    newgame = {
        "boards": NEWGAME_TRIALS,
        "value_hist": {str(k): values[k] for k in sorted(values)},
        "tile_count_hist": {str(k): tile_counts[k] for k in sorted(tile_counts)},
        "occupancy": occ,
    }
    counts["newgame_boards"] = NEWGAME_TRIALS

    random.seed(SEED)
    base = [[2, 4, 8, 16], [32, 64, 128, 256], [4, 8, 0, 0], [0, 0, 0, 0]]
    base_keys = set(to_tiles(g, base))
    empties = [[r, c] for r in range(4) for c in range(4) if base[r][c] == 0]
    if len(empties) != 6:
        raise SystemExit("spawn board must have exactly 6 empty cells")
    spawn_values = Counter()
    cell_hist = [[0] * 4 for _ in range(4)]
    for _ in range(SPAWN_TRIALS):
        tiles = to_tiles(g, base)
        status = real_end(tiles)
        new_keys = [k for k in tiles if k not in base_keys]
        if status != "continue" or len(new_keys) != 1 or len(tiles) != len(base_keys) + 1:
            raise SystemExit("end_tiles did not add exactly one tile")
        t = tiles[new_keys[0]]
        spawn_values[t.value] += 1
        cell_hist[t.row][t.col] += 1
    if set(spawn_values) - {2, 4}:
        raise SystemExit(f"unexpected spawn values {sorted(spawn_values)}")
    spawn = {
        "board": base,
        "empty_cells": empties,
        "trials": SPAWN_TRIALS,
        "value_hist": {"2": spawn_values[2], "4": spawn_values[4]},
        "cell_hist": cell_hist,
    }
    counts["spawn_trials"] = SPAWN_TRIALS
    return {"newgame": newgame, "spawn": spawn}, counts


def build_outputs():
    g = load_oracle()
    real_end = g.end_tiles
    moves, c1 = build_moves(g, real_end)
    legal, c2 = build_legal(g)
    spawn, c3 = build_spawn(g, real_end)
    counts = Counter()
    counts.update(c1)
    counts.update(c2)
    counts.update(c3)
    if counts["moves_sum_mismatch"]:
        print(f"WARNING: oracle tile-sum changed in {counts['moves_sum_mismatch']} move cases")
    meta = {
        "oracle": "2048_vertical.py",
        "oracle_sha256": hashlib.sha256(ORACLE.read_bytes()).hexdigest(),
        "python": sys.version,
        "pygame": pygame.version.ver,
        "seed": SEED,
        "counts": dict(sorted(counts.items())),
        "generator": GENERATOR_ID,
    }
    return {
        "moves.json": dump({"cases": moves}),
        "legal.json": dump({"cases": legal}),
        "spawn_stats.json": dump(spawn),
        "meta.json": dump(meta),
    }


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--verify", action="store_true",
                    help="regenerate in memory and compare with files on disk")
    args = ap.parse_args(argv)
    t0 = time.time()
    outputs = build_outputs()
    if args.verify:
        bad = [name for name, data in outputs.items()
               if not (OUT_DIR / name).exists() or (OUT_DIR / name).read_bytes() != data]
        for name in bad:
            print(f"DIFF {name}")
        if bad:
            return 1
        print("OK")
        return 0
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, data in outputs.items():
        (OUT_DIR / name).write_bytes(data)
        print(f"wrote {OUT_DIR / name} ({len(data)} bytes)")
    print(f"done in {time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
