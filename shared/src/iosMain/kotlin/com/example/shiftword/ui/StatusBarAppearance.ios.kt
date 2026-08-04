package com.example.shiftword.ui

import androidx.compose.runtime.Composable

// iOS status bar style is view-controller-driven (preferredStatusBarStyle / Info.plist), not a
// per-frame runtime call the way Android's WindowInsetsController is -- no per-theme-change hook
// is needed here. If dark mode's iOS styling ever needs a runtime nudge, this is the seam to add
// it in, matching this project's existing expect/actual split (SoundPlayer, DatabaseDriverFactory).
@Composable
actual fun ApplyStatusBarAppearance(darkTheme: Boolean) {
}
