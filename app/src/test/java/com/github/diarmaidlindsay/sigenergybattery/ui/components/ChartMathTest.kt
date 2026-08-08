package com.github.diarmaidlindsay.sigenergybattery.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.diarmaidlindsay.sigenergybattery.domain.model.ChartEvent
import com.github.diarmaidlindsay.sigenergybattery.domain.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {

    private fun event(epoch: Long, type: EventType = EventType.TRIGGER, soc: Double? = 50.0) =
        ChartEvent(
            type = type,
            epochSeconds = epoch,
            soc = soc,
            error = null,
            thresholdSoc = null,
            direction = null,
        )

    // ------------------------------------------------------------------
    // computePlottedEvents
    // ------------------------------------------------------------------

    @Test
    fun mapsEventToSampleIndexX() {
        // start=1000, interval=300s -> sample index = (epoch - 1000)/300
        val events = listOf(event(epoch = 1600))
        val plotted = computePlottedEvents(events, 1000L, 300L, sampleCount = 10)
        assertEquals(1, plotted.size)
        assertEquals(2.0, plotted[0].x, 0.001)
        assertEquals(50.0, plotted[0].soc, 0.001)
    }

    @Test
    fun handlesFractionalX() {
        val events = listOf(event(epoch = 1150)) // (1150-1000)/300 = 0.5
        val plotted = computePlottedEvents(events, 1000L, 300L, sampleCount = 10)
        assertEquals(0.5, plotted[0].x, 0.001)
    }

    @Test
    fun dropsEventsWithoutSoc() {
        val events = listOf(
            event(epoch = 1300, soc = null),
            event(epoch = 1600, soc = 60.0),
        )
        val plotted = computePlottedEvents(events, 1000L, 300L, sampleCount = 10)
        assertEquals(1, plotted.size)
        assertEquals(60.0, plotted[0].soc, 0.001)
    }

    @Test
    fun dropsEventsBeforeChartStart() {
        val events = listOf(event(epoch = 900))
        assertTrue(computePlottedEvents(events, 1000L, 300L, sampleCount = 10).isEmpty())
    }

    @Test
    fun dropsEventsAfterChartEnd() {
        // sampleCount=10 -> last valid x = 9 -> last valid epoch = 1000 + 9*300 = 3700
        val events = listOf(event(epoch = 3800))
        assertTrue(computePlottedEvents(events, 1000L, 300L, sampleCount = 10).isEmpty())
    }

    @Test
    fun dedupesEventsAtSameXKeepingFirst() {
        val first = event(epoch = 1600, soc = 50.0)
        val second = event(epoch = 1600, soc = 70.0, type = EventType.STRATEGY)
        val plotted = computePlottedEvents(listOf(first, second), 1000L, 300L, sampleCount = 10)
        assertEquals(1, plotted.size)
        assertEquals(50.0, plotted[0].soc, 0.001)
        assertEquals(EventType.TRIGGER, plotted[0].event.type)
    }

    @Test
    fun returnsEmptyForNoEvents() {
        assertTrue(computePlottedEvents(emptyList(), 1000L, 300L, sampleCount = 10).isEmpty())
    }

    // ------------------------------------------------------------------
    // carryForward
    // ------------------------------------------------------------------

    @Test
    fun carryForwardFillsInteriorNulls() {
        assertEquals(listOf(50.0, 50.0, 60.0), carryForward(listOf(50.0, null, 60.0)))
    }

    @Test
    fun carryForwardDropsLeadingNulls() {
        assertEquals(listOf(50.0, 60.0), carryForward(listOf(null, null, 50.0, 60.0)))
    }

    @Test
    fun carryForwardReturnsEmptyForAllNull() {
        assertTrue(carryForward(listOf(null, null)).isEmpty())
    }

    // ------------------------------------------------------------------
    // overlayOffset
    // ------------------------------------------------------------------

    @Test
    fun overlayOffsetPositionsAboveDotWhenRoom() {
        val offset = overlayOffset(
            selectedOffset = Offset(100f, 60f),
            boxSize = IntSize(300, 200),
            cardSize = IntSize(120, 50),
        )
        // x centered, clamped: 100 - 60 = 40
        // y prefers above: 60 - 50 - 8 = 2
        assertEquals(IntOffset(40, 2), offset)
    }

    @Test
    fun overlayOffsetFlipsBelowDotWhenNoRoomAbove() {
        val offset = overlayOffset(
            selectedOffset = Offset(100f, 10f),
            boxSize = IntSize(300, 200),
            cardSize = IntSize(120, 50),
        )
        // above would be 10 - 50 - 8 = -48 -> no room, so below: 10 + 8 = 18
        assertEquals(IntOffset(40, 18), offset)
    }

    @Test
    fun overlayOffsetClampsToBoxBounds() {
        val offset = overlayOffset(
            selectedOffset = Offset(290f, 190f),
            boxSize = IntSize(300, 200),
            cardSize = IntSize(120, 50),
        )
        // x clamped to 300-120=180; y prefers above: 190-50-8=132 (within bounds)
        assertEquals(IntOffset(180, 132), offset)
    }

    @Test
    fun overlayOffsetReturnsZeroWhenNoSelection() {
        assertEquals(IntOffset.Zero, overlayOffset(null, IntSize(300, 200), IntSize(120, 50)))
    }
}
