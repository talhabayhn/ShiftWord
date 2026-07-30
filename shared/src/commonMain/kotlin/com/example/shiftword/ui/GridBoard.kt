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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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

private val CELL_SIZE = 56.dp
private val SNAP_SPRING = spring<Offset>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
private const val EXPLODE_DURATION_MS = 300

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
) {
    val size = grid.size
    val density = LocalDensity.current
    val cellSizePx = with(density) { CELL_SIZE.toPx() }

    var dragAxis by remember { mutableStateOf<Axis?>(null) }
    var dragIndex by remember { mutableStateOf(0) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var dragStartRow by remember { mutableStateOf(0) }
    var dragStartCol by remember { mutableStateOf(0) }

    // Feature 1B: whether releasing the drag right now would complete a target word. Mirrors
    // GridBoard's own onDragEnd steps-rounding exactly, so the highlight never promises a win
    // that release wouldn't actually produce. Grid.apply is O(size^2) -- cheap enough to
    // recompute every recomposition at this grid's scale (4x4/5x5), same as the rest of this
    // composable's per-frame drag math.
    val wouldWin = run {
        val axis = dragAxis
        if (!winHighlightEnabled || axis == null) return@run false
        val steps = (dragOffsetPx / cellSizePx).roundToInt()
        if (steps == 0) return@run false
        val shifted = grid.apply(Move(axis, dragIndex, forward = steps > 0))
        val resultWord = when (axis) {
            Axis.Row -> shifted.cells[dragIndex].joinToString("") { it.letter.toString() }
            Axis.Col -> (0 until size).joinToString("") { r -> shifted.cells[r][dragIndex].letter.toString() }
        }
        resultWord in targetWords
    }

    Box(
        modifier = modifier
            .size(CELL_SIZE * size)
            // One soft shadow under the whole grid, not per-tile heavy shadows, per the
            // gameplay mockup — a low elevation keeps it subtle rather than a hard drop shadow.
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(TileCornerRadius), ambientColor = WarmSand, spotColor = WarmSand)
            .pointerInput(size) {
                detectDragGestures(
                    onDragStart = { offset ->
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
                val isLiveDraggingThis = (dragAxis == Axis.Row && dragIndex == r) || (dragAxis == Axis.Col && dragIndex == c)
                val baseX = c * cellSizePx
                val baseY = r * cellSizePx
                val targetX = if (dragAxis == Axis.Row && dragIndex == r) baseX + dragOffsetPx else baseX
                val targetY = if (dragAxis == Axis.Col && dragIndex == c) baseY + dragOffsetPx else baseY

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
                            .size(CELL_SIZE)
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
        val ghostAxis = dragAxis
        if (ghostAxis != null && dragOffsetPx != 0f) {
            val forward = dragOffsetPx > 0f
            val ghostLetter = when (ghostAxis) {
                Axis.Row -> if (forward) grid.cells[dragIndex][size - 1].letter else grid.cells[dragIndex][0].letter
                Axis.Col -> if (forward) grid.cells[size - 1][dragIndex].letter else grid.cells[0][dragIndex].letter
            }
            val ghostBaseX = when (ghostAxis) {
                Axis.Row -> if (forward) -cellSizePx else size * cellSizePx
                Axis.Col -> dragIndex * cellSizePx
            }
            val ghostBaseY = when (ghostAxis) {
                Axis.Row -> dragIndex * cellSizePx
                Axis.Col -> if (forward) -cellSizePx else size * cellSizePx
            }
            val ghostX = ghostBaseX + if (ghostAxis == Axis.Row) dragOffsetPx else 0f
            val ghostY = ghostBaseY + if (ghostAxis == Axis.Col) dragOffsetPx else 0f
            val ghostAlpha = (abs(dragOffsetPx) / cellSizePx).coerceIn(0f, 1f)
            val ghostTileShape = RoundedCornerShape(TileCornerRadius)

            Box(
                modifier = Modifier
                    .size(CELL_SIZE)
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
