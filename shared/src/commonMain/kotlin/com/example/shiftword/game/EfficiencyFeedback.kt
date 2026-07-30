package com.example.shiftword.game

/**
 * "Optimal: N moves" is only a true claim when the level's minMovesToSolve came from an
 * exact BFS result. When BFS hit its hard depth cap during generation (Risk R4),
 * minMovesToSolve is just the structural scramble-length upper bound — a real but non-optimal
 * number — so this must not be labelled "optimal", per the R4 fallback semantics.
 */
// Message templates are parameters (not a language import) to keep this function's dependency
// direction pointing away from ui/ — defaults preserve the original English-only behavior.
fun efficiencyMessage(
    movesUsed: Int,
    minMovesToSolve: Int,
    minMovesIsExact: Boolean,
    optimalMessage: (minMoves: Int, used: Int) -> String = { min, used -> "Optimal: $min moves — you used $used" },
    usedMessage: (used: Int) -> String = { used -> "You used $used moves" },
): String = if (minMovesIsExact) optimalMessage(minMovesToSolve, movesUsed) else usedMessage(movesUsed)
