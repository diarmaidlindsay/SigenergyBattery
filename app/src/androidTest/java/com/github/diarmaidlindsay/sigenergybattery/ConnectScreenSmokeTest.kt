package com.github.diarmaidlindsay.sigenergybattery

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectScreenSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launchesAndRendersConnectOrMonitorScreen() {
        // The app auto-connects to the last URL when it has connected before, so
        // either the connect screen or the monitor screen may be shown on launch.
        val showsConnect = composeRule.onAllNodesWithText("Connect").fetchSemanticsNodes().isNotEmpty()
        val showsMonitor = composeRule.onAllNodesWithText("BATTERY SOC").fetchSemanticsNodes().isNotEmpty()
        assertTrue("App launched but showed neither connect nor monitor screen", showsConnect || showsMonitor)
    }
}
