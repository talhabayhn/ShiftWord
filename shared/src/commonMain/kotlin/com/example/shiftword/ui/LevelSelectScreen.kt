package com.example.shiftword.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shiftword.game.LevelSelectEntry
import com.example.shiftword.ui.theme.PillShape
import com.example.shiftword.ui.theme.StarEmpty
import com.example.shiftword.ui.theme.SurfaceWhite
import com.example.shiftword.ui.theme.TextPrimary
import com.example.shiftword.ui.theme.TileCornerRadius
import com.example.shiftword.ui.theme.WarmSand

/**
 * Level Select (GAME_DESIGN.md): a scrollable grid of the current language's level pack (see
 * `LEVEL_PACK_SIZE`), replacing direct-to-gameplay navigation from Main Menu's "Oyna" button.
 * Unlocked levels (per [LevelSelectEntry.isUnlocked], from `buildLevelSelectEntries`'s pure
 * unlock-logic function) are tappable and show a star rating if already completed; locked levels
 * are dimmed and non-interactive. Tapping any unlocked level -- including an already-completed
 * one -- starts (or replays) gameplay on that exact level, reusing the same stable `Level` lookup
 * ("Tekrar Oyna"/GridBoard's sessionKey mechanics already handle a fresh `GameViewModel` per level
 * correctly, per the earlier replay/drag-staleness fix).
 */
@Composable
fun LevelSelectScreen(
    entries: List<LevelSelectEntry>,
    onLevelSelected: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    strings: UiStrings = TurkishStrings,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(strings.levelSelectTitle, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(entries, key = { it.levelNumber }) { entry ->
                LevelCard(entry = entry, onClick = { onLevelSelected(entry.levelNumber) }, strings = strings)
            }
        }

        Button(
            onClick = onBack,
            shape = PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite, contentColor = TextPrimary),
        ) { Text(strings.backToMenu) }
    }
}

@Composable
private fun LevelCard(entry: LevelSelectEntry, onClick: () -> Unit, strings: UiStrings) {
    val cardShape = RoundedCornerShape(TileCornerRadius)
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .background(if (entry.isUnlocked) SurfaceWhite else StarEmpty.copy(alpha = 0.25f), cardShape)
            .border(1.dp, if (entry.isUnlocked) WarmSand else StarEmpty, cardShape)
            .let { if (entry.isUnlocked) it.clickable(onClick = onClick) else it }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            entry.levelNumber.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (entry.isUnlocked) TextPrimary else StarEmpty,
        )
        when {
            entry.stars != null -> Text(
                buildString { repeat(3) { i -> append(if (i < entry.stars) "★" else "☆") } },
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
            )
            !entry.isUnlocked -> Text(strings.levelLocked, style = MaterialTheme.typography.labelSmall, color = StarEmpty)
            else -> Text("☆☆☆", style = MaterialTheme.typography.labelSmall, color = StarEmpty)
        }
    }
}
