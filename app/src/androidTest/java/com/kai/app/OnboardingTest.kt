package com.kai.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a fresh installation (or pm clear) before the suite. No API key required. */
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun opensWithoutAKeyAndShowsOnboarding() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Mulai").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Mulai").assertExists()
        compose.onNodeWithText("Kamu mengetik. AI bisa bersuara.").assertExists()
    }
}
