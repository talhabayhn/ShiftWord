package com.example.shiftword.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shiftword.ui.theme.DustyLavender
import com.example.shiftword.ui.theme.OnAccent
import com.example.shiftword.ui.theme.TextPrimary

@Composable
fun SettingsScreen(
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // "tr" / "en" — matches LanguageProfile.code / SettingsRepository's persisted value.
    languageCode: String = "tr",
    onLanguageCodeChange: (String) -> Unit = {},
    strings: UiStrings = TurkishStrings,
    // Feature 1B: opt-in, off-by-default drag-time win highlight.
    winHighlightEnabled: Boolean = false,
    onWinHighlightEnabledChange: (Boolean) -> Unit = {},
    // Accessibility/appearance settings (GAME_DESIGN.md, ARCHITECTURE.md §7a): both off by
    // default, matching SettingsRepository's column defaults.
    reducedMotionEnabled: Boolean = false,
    onReducedMotionEnabledChange: (Boolean) -> Unit = {},
    darkModeEnabled: Boolean = false,
    onDarkModeEnabledChange: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(strings.backToMenu, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }

        Text(strings.settingsTitle, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.soundLabel, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Switch(
                checked = soundEnabled,
                onCheckedChange = onSoundEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = DustyLavender),
            )
        }

        Text(
            strings.languageLabel,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.padding(top = 24.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LanguageOption(
                label = strings.languageTurkish,
                selected = languageCode == "tr",
                onClick = { onLanguageCodeChange("tr") },
            )
            LanguageOption(
                label = strings.languageEnglish,
                selected = languageCode == "en",
                onClick = { onLanguageCodeChange("en") },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.winHighlightLabel, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Switch(
                checked = winHighlightEnabled,
                onCheckedChange = onWinHighlightEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = DustyLavender),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.reducedMotionLabel, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Switch(
                checked = reducedMotionEnabled,
                onCheckedChange = onReducedMotionEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = DustyLavender),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(strings.darkModeLabel, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Switch(
                checked = darkModeEnabled,
                onCheckedChange = onDarkModeEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = OnAccent, checkedTrackColor = DustyLavender),
            )
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = DustyLavender, contentColor = OnAccent),
        ) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label, color = TextPrimary) }
    }
}
