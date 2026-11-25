package com.forge.imperiale

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.forge.imperiale.ui.components.LinktreeColorBlockHero
import com.forge.imperiale.ui.theme.ForgeTheme
import org.junit.Rule
import org.junit.Test

class WorkoutSmokeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun linktreeHeroRendersCleanly() {
        composeRule.setContent {
            ForgeTheme {
                LinktreeColorBlockHero(
                    eyebrow = "WEEK 1 OF 8",
                    headline = "STRICT TRAINING FLOOR",
                    badgeText = "READY"
                )
            }
        }
        composeRule.onNodeWithText("STRICT TRAINING FLOOR").assertIsDisplayed()
        composeRule.onNodeWithText("READY").assertIsDisplayed()
    }
}
