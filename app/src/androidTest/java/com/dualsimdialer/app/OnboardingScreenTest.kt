package com.dualsimdialer.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardingExplainsDefaultPhoneRole() {
        // A fresh install is intentionally not made default by an automated test.
        rule.onNodeWithText("DualSimDialer").assertIsDisplayed()
        val setup = rule.onAllNodesWithText("Make default Phone app").fetchSemanticsNodes()
        val main = rule.onAllNodesWithText("Dialer").fetchSemanticsNodes()
        assertTrue("Expected either onboarding or the already-configured dialer", setup.isNotEmpty() || main.isNotEmpty())
    }
}
