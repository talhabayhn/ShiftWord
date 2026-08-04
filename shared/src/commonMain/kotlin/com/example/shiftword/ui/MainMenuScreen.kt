package com.example.shiftword.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.shiftword.ui.theme.DustyLavender
import com.example.shiftword.ui.theme.OnAccent
import com.example.shiftword.ui.theme.PillShape
import com.example.shiftword.ui.theme.SageGreen
import com.example.shiftword.ui.theme.TextPrimary

data class MainMenuStats(val wordsFound: Long, val dayStreak: Int)

@Composable
fun MainMenuScreen(
    stats: MainMenuStats,
    onPlay: () -> Unit,
    onDailyPuzzle: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    strings: UiStrings = TurkishStrings,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        LogoMark(squareSize = 36.dp)
        Text(strings.appTitle, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
        Text(
            strings.appTagline,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )

        Spacer(Modifier.weight(1f))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = DustyLavender, contentColor = OnAccent),
            ) { Text(strings.play, style = MaterialTheme.typography.titleLarge) }

            Button(
                onClick = onDailyPuzzle,
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = SageGreen, contentColor = OnAccent),
            ) { Text(strings.dailyPuzzle, style = MaterialTheme.typography.titleMedium) }

            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape,
            ) { Text(strings.settings, style = MaterialTheme.typography.titleMedium, color = TextPrimary) }
        }

        Spacer(Modifier.weight(2f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatColumn(value = stats.wordsFound.toString(), label = strings.wordsFoundLabel)
            StatColumn(value = stats.dayStreak.toString(), label = strings.dayStreakLabel)
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = TextPrimary, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextPrimary, textAlign = TextAlign.Center)
    }
}
