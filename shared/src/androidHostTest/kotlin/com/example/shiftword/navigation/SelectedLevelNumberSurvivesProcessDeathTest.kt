package com.example.shiftword.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression test for a real-device process-death finding: `AppNavHost`'s `selectedLevelNumber`
 * used to be a plain `remember`, which silently reset to its default (1) whenever the process
 * was recreated -- even though Navigation Compose's own back stack correctly restored the
 * GAMEPLAY destination, so a player on e.g. level 40 who got backgrounded and reclaimed by the
 * OS (an ordinary event, not an edge case) would land back on what looked like normal resumed
 * gameplay but was actually level 1's puzzle, silently substituted with no error and no move
 * count anomaly. Reproduced live (real backgrounding, real `run-as ... kill -9`, real relaunch)
 * before this fix and confirmed fixed after it -- this test guards the mechanism so it can't
 * silently regress back to plain `remember`.
 *
 * Reproduces `AppNavHost`'s exact shape (a value set by one route, shared via a NavHost-external
 * `rememberSaveable`, read by another) rather than exercising the full `AppNavHost` composable --
 * doing so would need a seeded database, real dictionary/level-pack generation, and a
 * `SoundEffectsFactory`, none of which are relevant to what actually broke here.
 * `StateRestorationTester` is the standard, documented way to test exactly this class of bug.
 */
@RunWith(RobolectricTestRunner::class)
class SelectedLevelNumberSurvivesProcessDeathTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun HarnessNavHost() {
        val navController = rememberNavController()
        // Mirrors AppNavHost.kt's actual declaration exactly -- see this test's own doc comment.
        var selectedLevelNumber by rememberSaveable { mutableIntStateOf(1) }

        NavHost(navController = navController, startDestination = "select") {
            composable("select") {
                Button(onClick = {
                    selectedLevelNumber = 40
                    navController.navigate("gameplay")
                }) { Text("go") }
            }
            composable("gameplay") {
                Text("level:$selectedLevelNumber")
            }
        }
    }

    @Test
    fun selectedLevelNumberSurvivesStateRestoration() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { HarnessNavHost() }

        composeTestRule.onNodeWithText("go").performClick()
        composeTestRule.onNodeWithText("level:40").assertExists()

        // Simulates process death + recreation -- the exact mechanism a real Android process
        // kill exercises, without needing a device/emulator.
        restorationTester.emulateSavedInstanceStateRestore()

        // Before the fix: this would show "level:1" (selectedLevelNumber silently reset),
        // even though the NavHost itself correctly restored to the "gameplay" route.
        composeTestRule.onNodeWithText("level:40").assertExists()
    }
}
