package com.example.shiftword.domain

import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move
import com.example.shiftword.model.allMoves

/**
 * Hard depth cap, chosen directly from the timing table in ALGORITHM_VALIDATION.md Risk R4:
 * depth 5 ~3.5s, depth 6 50+s then OOM-killed. Do NOT raise this without re-running that
 * timing validation first — solvability of a level never depends on BFS succeeding (see R4),
 * so there is no correctness reason to loosen it.
 */
const val BFS_HARD_DEPTH_CAP = 5

data class SolverResult(val minMoves: Int, val path: List<Move>)

// Just the letters (no per-cell Long ids) -- a much lighter identity for a visited/queued search
// state than a full Grid. See bfsMinMovesToAnyTarget's doc comment for why this matters.
private typealias GridKey = List<List<Char>>

/**
 * Real-device playtesting P0 finding: an OutOfMemoryError crashed mid-BFS-call on a ~192MB
 * Android heap, with the heap already almost entirely full (<1% free) at the moment of failure —
 * the crash site itself (a routine allocation) was a symptom, not the cause. Root cause: the
 * original implementation kept every visited state's FULL `Grid` object AND a growing copy of its
 * `List<Move>` path simultaneously live in the BFS queue. At [BFS_HARD_DEPTH_CAP]=5 with a
 * branching factor of 4*gridSize (16 for 4x4, 20 for 5x5), the worst case (a target the search
 * ultimately can't reach — see the R4 addendum's "point-in-time, not standing" note on how a
 * remaining target can legitimately drift outside the depth-5 window mid-play) explores up to
 * ~1M (4x4) to ~3.2M (5x5) states, each held as a live Grid (a nested `List<List<Cell>>`, each
 * `Cell` boxing a `Char` and a `Long` id) plus its own path copy. This was validated for TIME on
 * desktop JVM hardware (ALGORITHM_VALIDATION.md Risk R4's timing table, gigabytes of heap
 * available) but never for MEMORY on a real, resource-constrained mobile heap — see
 * TESTING_GAPS.md, which already flagged on-device BFS latency (but not memory) as unmeasured.
 *
 * Fixed by keeping only a compact `letterKey()` (`List<List<Char>>` — just the letters, no per-
 * cell `Long` ids) per queued/visited state instead of a full `Grid`, plus a parent-pointer map
 * (`key -> (parentKey, moveTaken)`) so a path is reconstructed ONCE, only for the winning state,
 * by replaying at most [BFS_HARD_DEPTH_CAP] moves from `start` -- instead of every queued node
 * separately carrying (and copying, via `path + m`, at every step) its own growing path. The
 * `Grid` needed to expand a dequeued state's neighbors is rebuilt on demand from its key via the
 * same cheap replay, trading a small, bounded amount of extra `Grid.apply()` calls (at most
 * [BFS_HARD_DEPTH_CAP] per dequeue) for no longer holding the search's entire explored frontier as
 * live, independently-referenced heap objects at once -- the dominant cost this crash traced back
 * to. Output (whether a result is found, and its `minMoves`/`path`) is unchanged; this only
 * changes how the search represents its own state internally.
 *
 * [isActive] lets a caller running this inside a coroutine (see `GameViewModel.requestHint`) make
 * an abandoned/cancelled search actually stop early instead of running its full (potentially
 * multi-million-state) worst case to completion regardless of anyone still needing the result --
 * confirmed as a real, compounding factor in the same crash: the stack trace's suppressed
 * exception showed the coroutine was already in a "Cancelling" state when the allocation that
 * tipped the heap over failed, meaning cancellation was requested but had no effect on the
 * already-running, non-cooperative search. Defaults to always-active so every other existing
 * caller (tests, `generateLevel`, `resolveCascade`'s reachability checks) is unaffected.
 */
fun bfsMinMovesToAnyTarget(
    start: Grid,
    targets: Set<String>,
    maxDepth: Int = BFS_HARD_DEPTH_CAP,
    isActive: () -> Boolean = { true },
): SolverResult? {
    val effectiveMaxDepth = minOf(maxDepth, BFS_HARD_DEPTH_CAP)
    val moves = allMoves(start.size)

    if (findMatchedWords(start, targets).isNotEmpty()) return SolverResult(0, emptyList())

    val startKey: GridKey = start.letterKey()
    val visited = mutableSetOf(startKey)
    val parent = HashMap<GridKey, Pair<GridKey, Move>>() // key -> (parentKey, moveTaken)
    val depthOf = HashMap<GridKey, Int>()
    depthOf[startKey] = 0
    val queue = ArrayDeque<GridKey>()
    queue.add(startKey)

    fun gridFor(key: GridKey): Grid {
        if (key == startKey) return start
        val pathToKey = ArrayDeque<Move>()
        var k = key
        while (k != startKey) {
            val (parentKey, move) = parent.getValue(k)
            pathToKey.addFirst(move)
            k = parentKey
        }
        var grid = start
        for (m in pathToKey) grid = grid.apply(m)
        return grid
    }

    fun pathTo(key: GridKey): List<Move> {
        val path = mutableListOf<Move>()
        var k = key
        while (k != startKey) {
            val (parentKey, move) = parent.getValue(k)
            path.add(move)
            k = parentKey
        }
        path.reverse()
        return path
    }

    while (queue.isNotEmpty()) {
        if (!isActive()) return null
        val currentKey = queue.removeFirst()
        val depth = depthOf.getValue(currentKey)
        if (depth >= effectiveMaxDepth) continue
        val current = gridFor(currentKey)
        for (m in moves) {
            val next = current.apply(m)
            val key = next.letterKey()
            if (key in visited) continue
            visited.add(key)
            parent[key] = currentKey to m
            depthOf[key] = depth + 1
            if (findMatchedWords(next, targets).isNotEmpty()) {
                val path = pathTo(key)
                return SolverResult(path.size, path)
            }
            queue.add(key)
        }
    }
    return null
}
