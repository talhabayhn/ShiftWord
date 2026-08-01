package com.example.shiftword.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.shiftword.domain.apply
import com.example.shiftword.model.Axis
import com.example.shiftword.model.Grid
import com.example.shiftword.model.Move
import com.example.shiftword.ui.theme.DustyLavender
import com.example.shiftword.ui.theme.LavenderTileTint
import com.example.shiftword.ui.theme.SageGreen
import com.example.shiftword.ui.theme.SurfaceWhite
import com.example.shiftword.ui.theme.TextPrimary
import com.example.shiftword.ui.theme.TileCornerRadius
import com.example.shiftword.ui.theme.WarmSand
import com.example.shiftword.ui.theme.tileLetterStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Default/fallback only -- GameScreen now passes a larger, screen-width-responsive [GridBoard.cellSize]
// so the board reads as the gameplay screen's visual focal point (Shape.kt's own doc comment flags
// this original 56.dp as smaller than the mockup's tile proportions, a deliberate scope-limiting call
// at the time, not the intended final size). Kept as the default so other call sites (tests) are
// unaffected.
private val DEFAULT_CELL_SIZE = 56.dp
private val SNAP_SPRING = spring<Offset>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
private const val EXPLODE_DURATION_MS = 300
private const val HINT_NUDGE_DURATION_MS = 260

/**
 * Renders [grid] and turns row/column drags into committed [Move]s. Each cell is wrapped in
 * `key(cell.id)` (ARCHITECTURE.md §2): Compose matches composition groups by that key across
 * recompositions regardless of which (row, col) loop position it now occupies, so a cell that
 * moved keeps its own remembered `Animatable` and animates a slide — it is never torn down and
 * recreated the way it would be if this were keyed by (row, col) position instead.
 */
@Composable
fun GridBoard(
    grid: Grid,
    explodingCellIds: Set<Long>,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    // Feature 1B (GAME_DESIGN.md): off by default -- a real difficulty lever, not a cosmetic
    // default. When true, and the axis currently being dragged would spell one of [targetWords]
    // if released right now, that row/column is highlighted.
    winHighlightEnabled: Boolean = false,
    targetWords: Set<String> = emptySet(),
    // Bug fix: identifies which GameViewModel instance [onMove] belongs to (AppNavHost passes the
    // viewModel itself). pointerInput below only restarts its gesture-detection coroutine when one
    // of its keys changes; keying on `size` alone was never enough, since every generated level is
    // the same size (generateRandomLevel hardcodes size=4). On level-advance/replay, AppNavHost
    // swaps in a brand-new GameViewModel and cancels the old one's viewModelScope (see its
    // DisposableEffect), but without this key the running pointerInput coroutine kept the ORIGINAL
    // onDragEnd closure forever, which still called the disposed viewModel's onMove -- a no-op on a
    // cancelled scope, so the grid looked unresponsive to drags after every level transition/replay.
    sessionKey: Any = Unit,
    // Feature: visualize the hint as a brief automatic shift-and-back animation on the suggested
    // row/column, reusing the same graphicsLayer-driven position Animatable real drags use, rather
    // than requiring the player to read/interpret text ("1. satır sola" etc.). Keyed on hintMove
    // transitioning from null to non-null (see the LaunchedEffect below); GameViewModel.requestHint
    // clears hintMove to null synchronously on every accepted request specifically so that
    // transition -- and therefore this replay -- still happens even when the new request resolves
    // to the exact same Move as the previous one, which plain value-equality would not retrigger.
    hintMove: Move? = null,
    // Task 1 (enlarge/center the board): screen-width-responsive by default at the call site
    // (GameScreen computes this via BoxWithConstraints), not a fixed constant -- see
    // DEFAULT_CELL_SIZE's doc comment for why 56.dp alone was never the intended final size.
    cellSize: Dp = DEFAULT_CELL_SIZE,
) {
    val size = grid.size
    val density = LocalDensity.current
    val cellSizePx = with(density) { cellSize.toPx() }

    var dragAxis by remember { mutableStateOf<Axis?>(null) }
    var dragIndex by remember { mutableStateOf(0) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var dragStartRow by remember { mutableStateOf(0) }
    var dragStartCol by remember { mutableStateOf(0) }

    var hintAnimAxis by remember { mutableStateOf<Axis?>(null) }
    var hintAnimIndex by remember { mutableStateOf(0) }
    val hintOffset = remember { Animatable(0f) }
    // Tracked explicitly (not just a plain LaunchedEffect) so a real drag starting mid-animation
    // can *cancel* it outright -- see onDragStart below -- rather than merely being out-rendered by
    // effectiveAxis's priority while the coroutine keeps running to completion in the background.
    val coroutineScope = rememberCoroutineScope()
    var hintJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(hintMove, sessionKey) {
        val move = hintMove ?: return@LaunchedEffect
        if (dragAxis != null) return@LaunchedEffect // a real finger drag already in progress always wins
        hintJob?.cancel()
        hintJob = coroutineScope.launch {
            hintAnimAxis = move.axis
            hintAnimIndex = move.index
            val nudgeDistancePx = cellSizePx * 0.55f * if (move.forward) 1f else -1f
            hintOffset.snapTo(0f)
            hintOffset.animateTo(nudgeDistancePx, tween(HINT_NUDGE_DURATION_MS))
            hintOffset.animateTo(0f, tween(HINT_NUDGE_DURATION_MS))
            hintAnimAxis = null
        }
    }

    // Real drag always takes priority over the hint animation on whatever row/column they'd both
    // touch -- see onDragStart below, which cancels any in-flight hint animation the moment a real
    // drag starts, and the LaunchedEffect above, which refuses to start one while a real drag is
    // already in progress (the reverse ordering).
    val effectiveAxis = dragAxis ?: hintAnimAxis
    val effectiveIndex = if (dragAxis != null) dragIndex else hintAnimIndex
    val effectiveOffsetPx = if (dragAxis != null) dragOffsetPx else hintOffset.value

    // Feature 1B: whether releasing the drag right now would complete a target word. Mirrors
    // GridBoard's own onDragEnd steps-rounding exactly, so the highlight never promises a win
    // that release wouldn't actually produce. Grid.apply is O(size^2) -- cheap enough to
    // recompute every recomposition at this grid's scale (4x4/5x5), same as the rest of this
    // composable's per-frame drag math. Also lights up during the hint animation (effectiveAxis),
    // which doubles as a subtle confirmation that the suggested move does complete a word.
    val wouldWin = run {
        val axis = effectiveAxis
        if (!winHighlightEnabled || axis == null) return@run false
        val steps = (effectiveOffsetPx / cellSizePx).roundToInt()
        if (steps == 0) return@run false
        val shifted = grid.apply(Move(axis, effectiveIndex, forward = steps > 0))
        val resultWord = when (axis) {
            Axis.Row -> shifted.cells[effectiveIndex].joinToString("") { it.letter.toString() }
            Axis.Col -> (0 until size).joinToString("") { r -> shifted.cells[r][effectiveIndex].letter.toString() }
        }
        resultWord in targetWords
    }

    Box(
        modifier = modifier
            .size(cellSize * size)
            // One soft shadow under the whole grid, not per-tile heavy shadows, per the
            // gameplay mockup — a low elevation keeps it subtle rather than a hard drop shadow.
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(TileCornerRadius), ambientColor = WarmSand, spotColor = WarmSand)
            .pointerInput(size, sessionKey) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Cancel any in-flight hint animation outright -- a real finger drag
                        // starting mid-animation must interrupt it cleanly, not just get rendered
                        // on top of it (see hintJob's doc comment above).
                        hintJob?.cancel()
                        hintJob = null
                        hintAnimAxis = null
                        dragStartCol = (offset.x / cellSizePx).toInt().coerceIn(0, size - 1)
                        dragStartRow = (offset.y / cellSizePx).toInt().coerceIn(0, size - 1)
                        dragAxis = null
                        dragOffsetPx = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val axis = dragAxis ?: run {
                            val chosen = if (abs(dragAmount.x) > abs(dragAmount.y)) Axis.Row else Axis.Col
                            dragAxis = chosen
                            dragIndex = if (chosen == Axis.Row) dragStartRow else dragStartCol
                            chosen
                        }
                        dragOffsetPx += if (axis == Axis.Row) dragAmount.x else dragAmount.y
                    },
                    onDragEnd = {
                        val axis = dragAxis
                        if (axis != null) {
                            val steps = (dragOffsetPx / cellSizePx).roundToInt()
                            if (steps != 0) onMove(Move(axis, dragIndex, forward = steps > 0))
                        }
                        dragAxis = null
                        dragOffsetPx = 0f
                    },
                    onDragCancel = {
                        dragAxis = null
                        dragOffsetPx = 0f
                    },
                )
            },
    ) {
        for (r in 0 until size) {
            for (c in 0 until size) {
                val cell = grid.cells[r][c]
                val isLiveDraggingThis = (effectiveAxis == Axis.Row && effectiveIndex == r) || (effectiveAxis == Axis.Col && effectiveIndex == c)
                val baseX = c * cellSizePx
                val baseY = r * cellSizePx
                val targetX = if (effectiveAxis == Axis.Row && effectiveIndex == r) baseX + effectiveOffsetPx else baseX
                val targetY = if (effectiveAxis == Axis.Col && effectiveIndex == c) baseY + effectiveOffsetPx else baseY

                key(cell.id) {
                    // A single Animatable kept alive for this cell's whole lifetime (as long as
                    // its id exists in the grid) so releasing a drag mid-flight eases smoothly
                    // from wherever the finger left it, rather than snapping instantly.
                    val position = remember { Animatable(Offset(baseX, baseY), Offset.VectorConverter) }
                    LaunchedEffect(targetX, targetY, isLiveDraggingThis) {
                        if (isLiveDraggingThis) {
                            position.snapTo(Offset(targetX, targetY))
                        } else {
                            position.animateTo(Offset(targetX, targetY), SNAP_SPRING)
                        }
                    }

                    val isExploding = cell.id in explodingCellIds
                    val explodeScale by animateFloatAsState(
                        targetValue = if (isExploding) 0f else 1f,
                        animationSpec = tween(durationMillis = EXPLODE_DURATION_MS),
                    )
                    val explodeAlpha by animateFloatAsState(
                        targetValue = if (isExploding) 0f else 1f,
                        animationSpec = tween(durationMillis = EXPLODE_DURATION_MS),
                    )

                    val tileShape = RoundedCornerShape(TileCornerRadius)
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .graphicsLayer {
                                translationX = position.value.x
                                translationY = position.value.y
                                scaleX = explodeScale
                                scaleY = explodeScale
                                alpha = explodeAlpha
                            }
                            .background(
                                if (isLiveDraggingThis) (if (wouldWin) SageGreen else LavenderTileTint) else SurfaceWhite,
                                tileShape,
                            )
                            .border(1.dp, if (isLiveDraggingThis) DustyLavender else WarmSand, tileShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = cell.letter.toString(), style = tileLetterStyle(), color = TextPrimary)
                    }
                }
            }
        }

        // Feature 1A (always on): the letter that will wrap in from the opposite edge once this
        // drag is released -- purely cosmetic early visibility, no new information vs. what the
        // wrap-around mechanic already makes computable, it just removes the "pop in only on
        // release" lag. Only one shift is ever committed per gesture regardless of how far the
        // drag travels (Move has no magnitude), so only the single upcoming wrap letter is shown,
        // fading in over the first cell-width of travel and staying fully visible beyond that.
        val ghostAxis = effectiveAxis
        if (ghostAxis != null && effectiveOffsetPx != 0f) {
            val forward = effectiveOffsetPx > 0f
            val ghostLetter = when (ghostAxis) {
                Axis.Row -> if (forward) grid.cells[effectiveIndex][size - 1].letter else grid.cells[effectiveIndex][0].letter
                Axis.Col -> if (forward) grid.cells[size - 1][effectiveIndex].letter else grid.cells[0][effectiveIndex].letter
            }
            val ghostBaseX = when (ghostAxis) {
                Axis.Row -> if (forward) -cellSizePx else size * cellSizePx
                Axis.Col -> effectiveIndex * cellSizePx
            }
            val ghostBaseY = when (ghostAxis) {
                Axis.Row -> effectiveIndex * cellSizePx
                Axis.Col -> if (forward) -cellSizePx else size * cellSizePx
            }
            val ghostX = ghostBaseX + if (ghostAxis == Axis.Row) effectiveOffsetPx else 0f
            val ghostY = ghostBaseY + if (ghostAxis == Axis.Col) effectiveOffsetPx else 0f
            val ghostAlpha = (abs(effectiveOffsetPx) / cellSizePx).coerceIn(0f, 1f)
            val ghostTileShape = RoundedCornerShape(TileCornerRadius)

            Box(
                modifier = Modifier
                    .size(cellSize)
                    .graphicsLayer {
                        translationX = ghostX
                        translationY = ghostY
                        alpha = ghostAlpha
                    }
                    .background(if (wouldWin) SageGreen else LavenderTileTint, ghostTileShape)
                    .border(1.dp, DustyLavender, ghostTileShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = ghostLetter.toString(), style = tileLetterStyle(), color = TextPrimary)
            }
        }
    }
}
