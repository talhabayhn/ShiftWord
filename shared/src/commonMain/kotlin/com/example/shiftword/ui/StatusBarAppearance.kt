package com.example.shiftword.ui

import androidx.compose.runtime.Composable

/**
 * Dark mode addendum (ARCHITECTURE.md §7a): this app's dark mode is an app-level Settings toggle,
 * independent of the OS's own light/dark setting -- Android's `enableEdgeToEdge()` (called once,
 * argument-less, in `MainActivity.onCreate`) only auto-derives system-bar icon color/contrast
 * from the OS's day/night configuration, which is NOT the same signal. Without this, toggling
 * dark mode ON while the device itself is in light mode would leave the status/navigation bar
 * icons using their light-mode (dark-on-light) styling drawn over this app's now-dark background
 * -- invisible, not just mismatched. [darkTheme] should be the same value passed to
 * `WordShiftTheme`, called once near the composition root (App.kt) so it re-applies whenever the
 * setting changes, not just at first launch.
 */
@Composable
expect fun ApplyStatusBarAppearance(darkTheme: Boolean)
