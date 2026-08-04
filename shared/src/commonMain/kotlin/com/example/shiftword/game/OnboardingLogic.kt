package com.example.shiftword.game

/**
 * GAME_DESIGN.md §9h: the single gate every onboarding surface (auto-played hint nudge, forced
 * win highlight, hint-button callout, first-win explanation) is derived from -- kept as a pure,
 * standalone function (same reasoning as [buildLevelSelectEntries]'s own doc comment: this is
 * exactly the kind of state-transition logic this project has gotten wrong before via untested
 * ad-hoc reasoning) rather than inlined separately at each of AppNavHost's onboarding call sites,
 * so all four surfaces are provably tied to the same condition instead of four independent copies
 * that could drift apart.
 *
 * True only for level 1, and only until [hasSeenOnboarding] flips true (on the player's first
 * ever level completion, see AppNavHost's onLevelCompleted wiring) -- never re-derivable to true
 * again afterward, matching the one-time, not-replayable-from-Settings design decision.
 */
fun isOnboardingLevel(hasSeenOnboarding: Boolean, levelNumber: Int): Boolean =
    !hasSeenOnboarding && levelNumber == 1
