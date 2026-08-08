package com.github.diarmaidlindsay.sigenergybattery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.diarmaidlindsay.sigenergybattery.domain.model.ChartEvent
import com.github.diarmaidlindsay.sigenergybattery.domain.model.Direction
import com.github.diarmaidlindsay.sigenergybattery.domain.model.EventType
import com.github.diarmaidlindsay.sigenergybattery.domain.model.MinerPreset
import com.github.diarmaidlindsay.sigenergybattery.domain.model.TriggerAction
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.AlertRed
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.ChargeGreen
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.TextPrimary
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.TextSecondary
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.WarnYellow
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerVisibilityListener
import com.patrykandpatrick.vico.compose.cartesian.marker.Interaction
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.LineComponent
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 24-hour battery SOC line chart (0-100%, 5-minute samples).
 *
 * [socs] are samples oldest → newest where each index is [intervalSeconds] apart,
 * starting at [startEpochSeconds]. Null samples are carried forward from the last
 * known value so the line renders continuously.
 *
 * [events] are plotted as colored dots on the line at the time they fired (strategy
 * transitions in [WarnYellow], one-shot trigger fires in [AlertRed]). Pressing and
 * holding a dot shows an overlay card with the event's details.
 */
@Composable
fun SocHistoryChart(
    startEpochSeconds: Long,
    intervalSeconds: Long,
    socs: List<Double?>,
    events: List<ChartEvent> = emptyList(),
    modifier: Modifier = Modifier,
    height: Int = 180,
) {
    val cleaned = carryForward(socs)
    if (cleaned.isEmpty()) return
    // carryForward drops leading nulls; the chart's first sample no longer sits at
    // startEpochSeconds. Offset both the time labels and the event x positions.
    val leadingNulls = socs.indexOfFirst { it != null }.takeIf { it >= 0 } ?: 0
    val chartStartEpoch = startEpochSeconds + leadingNulls * intervalSeconds

    val plottedEvents = remember(events, chartStartEpoch, intervalSeconds, cleaned.size) {
        computePlottedEvents(events, chartStartEpoch, intervalSeconds, cleaned.size)
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(cleaned, plottedEvents) {
        val x = cleaned.indices.map { it.toDouble() }
        modelProducer.runTransaction {
            lineModel { series(x, cleaned) }
            if (plottedEvents.isNotEmpty()) {
                lineModel {
                    series(plottedEvents.map { it.x }, plottedEvents.map { it.soc }, key = EVENT_SERIES_KEY)
                }
            }
        }
    }

    val bottomFormatter = remember(chartStartEpoch, intervalSeconds) {
        CartesianValueFormatter { _, value, _ ->
            val epoch = chartStartEpoch + value.toLong() * intervalSeconds
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epoch * 1000))
        }
    }

    val zoomState = rememberVicoZoomState(
        initialZoom = Zoom.Content,
        minZoom = Zoom.Content,
    )

    val socLayer = rememberSocLineCartesianLayer()
    val eventsLayer = if (plottedEvents.isNotEmpty()) {
        val pointProvider = remember(plottedEvents) {
            EventPointProvider(
                strategyPoint = LineCartesianLayer.Point(ShapeComponent(Fill(WarnYellow), CircleShape), size = 12.dp),
                triggerPoint = LineCartesianLayer.Point(ShapeComponent(Fill(AlertRed), CircleShape), size = 12.dp),
                typeByX = plottedEvents.associate { it.x to it.event.type },
            )
        }
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill.Transparent),
                    stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 0.dp),
                    pointProvider = pointProvider,
                )
            ),
            rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0),
        )
    } else {
        null
    }

    val eventXSet = remember(plottedEvents) { plottedEvents.map { it.x }.toSet() }
    val marker = remember(eventXSet) {
        EventSelectionMarker(
            eventXSet = eventXSet,
            guideline = LineComponent(fill = Fill(TextSecondary), thickness = 1.dp),
            indicator = ShapeComponent(
                fill = Fill(ChargeGreen.copy(alpha = 0.25f)),
                shape = CircleShape,
                strokeFill = Fill(ChargeGreen),
                strokeThickness = 2.dp,
            ),
            indicatorSize = 16.dp,
        )
    }

    var selectedEvent by remember { mutableStateOf<ChartEvent?>(null) }
    var selectedOffset by remember { mutableStateOf<Offset?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    // Clear a stale selection when the events reload and the chosen one disappears.
    LaunchedEffect(plottedEvents) {
        if (selectedEvent != null && plottedEvents.none { it.event == selectedEvent }) {
            selectedEvent = null
            selectedOffset = null
        }
    }

    fun updateSelection(targets: List<CartesianMarker.Target>, eventByX: Map<Double, ChartEvent>) {
        val lineTarget = targets.firstOrNull {
            it is LineCartesianLayerMarkerTarget && eventByX.containsKey(it.x)
        } as? LineCartesianLayerMarkerTarget
        val point = lineTarget?.points?.firstOrNull()
        val event = lineTarget?.let { eventByX[it.x] }
        if (event != null && point != null) {
            val newOffset = Offset(lineTarget.canvasX, point.canvasY)
            if (selectedEvent != event || selectedOffset != newOffset) {
                selectedEvent = event
                selectedOffset = newOffset
            }
        } else if (selectedEvent != null || selectedOffset != null) {
            selectedEvent = null
            selectedOffset = null
        }
    }

    val selectionListener = remember(plottedEvents) {
        val eventByX = plottedEvents.associate { it.x to it.event }
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                updateSelection(targets, eventByX)
            }

            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                updateSelection(targets, eventByX)
            }

            override fun onHidden(marker: CartesianMarker) {
                if (selectedEvent != null || selectedOffset != null) {
                    selectedEvent = null
                    selectedOffset = null
                }
            }
        }
    }

    // A long-press only selects an event when the finger is actually on/near a dot
    // (within LONG_PRESS_TOLERANCE). Because the SOC layer publishes no marker
    // targets, Vico snaps the marker only to the sparse event dots.
    val density = LocalDensity.current
    val hitTolerancePx = with(density) { LONG_PRESS_TOLERANCE.toPx() }
    val markerController = remember(density) {
        object : CartesianMarkerController {
            private var held = false

            override fun shouldShowMarker(
                interaction: Interaction,
                targets: List<CartesianMarker.Target>,
            ): Boolean {
                when (interaction) {
                    is Interaction.LongPress -> held = true
                    is Interaction.Release, is Interaction.Exit -> held = false
                    else -> {}
                }
                if (!held || targets.isEmpty()) return false
                return abs(interaction.point.x - targets.first().canvasX) <= hitTolerancePx
            }
        }
    }

    val chart = rememberCartesianChart(
        *eventChartLayers(socLayer, eventsLayer),
        startAxis = VerticalAxis.rememberStart(
            valueFormatter = remember { CartesianValueFormatter.decimal(0, suffix = "%") }
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = bottomFormatter,
        ),
        marker = marker,
        markerVisibilityListener = selectionListener,
        markerController = markerController,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clipToBounds()
            .onSizeChanged { boxSize = it }
    ) {
        CartesianChartHost(
            chart,
            modelProducer,
            zoomState = zoomState,
            modifier = Modifier.fillMaxSize(),
        )

        selectedEvent?.let { event ->
            EventOverlayCard(
                event = event,
                modifier = Modifier
                    .onSizeChanged { cardSize = it }
                    .offset { overlayOffset(selectedOffset, boxSize, cardSize) },
            )
        }
    }
}

private const val EVENT_SERIES_KEY = "events"
private val LONG_PRESS_TOLERANCE: Dp = 24.dp

/** An event positioned on the chart's sample-index x axis. */
internal data class PlottedEvent(
    val event: ChartEvent,
    val x: Double,
    val soc: Double,
)

/**
 * Maps events onto the chart's sample-index x axis. Events without a SOC reading
 * or outside the chart's window are dropped; events sharing the same x collapse
 * to the first one so Vico doesn't see duplicate points.
 */
internal fun computePlottedEvents(
    events: List<ChartEvent>,
    chartStartEpochSeconds: Long,
    intervalSeconds: Long,
    sampleCount: Int,
): List<PlottedEvent> =
    events.mapNotNull { event ->
        val soc = event.soc ?: return@mapNotNull null
        val x = (event.epochSeconds - chartStartEpochSeconds).toDouble() / intervalSeconds.toDouble()
        if (x < 0.0 || x > (sampleCount - 1).toDouble()) null else PlottedEvent(event, x, soc)
    }.distinctBy { it.x }

/** A [LineCartesianLayer] that never publishes marker targets. This keeps Vico's
 * marker snapping on the sparse event dots instead of every SOC sample. */
private class SocOnlyLineCartesianLayer(
    lineProvider: LineCartesianLayer.LineProvider,
    rangeProvider: CartesianLayerRangeProvider,
) : LineCartesianLayer(
    lineProvider = lineProvider,
    rangeProvider = rangeProvider,
) {
    override fun CartesianDrawingContext.updateMarkerTargets(
        entry: LineCartesianLayerModel.Entry,
        seriesKey: Any,
        canvasX: Float,
        canvasY: Float,
        lineFillBitmap: ImageBitmap,
    ) = Unit

    override fun CartesianDrawingContext.updateMarkerTargets(
        entry: LineCartesianLayerModel.Entry,
        seriesKey: Any,
        canvasX: Float,
        canvasY: Float,
        color: Color,
    ) = Unit
}

@Composable
private fun rememberSocLineCartesianLayer(): LineCartesianLayer =
    remember {
        SocOnlyLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.Line(
                    fill = LineCartesianLayer.LineFill.single(Fill(ChargeGreen)),
                    areaFill = LineCartesianLayer.AreaFill.single(Fill(ChargeGreen.copy(alpha = 0.15f))),
                )
            ),
            rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0),
        )
    }

private fun eventChartLayers(
    socLayer: LineCartesianLayer,
    eventsLayer: LineCartesianLayer?,
): Array<CartesianLayer<*>> =
    if (eventsLayer != null) arrayOf(socLayer, eventsLayer) else arrayOf(socLayer)

/** Draws dots colored by event type (strategy vs trigger). */
private class EventPointProvider(
    private val strategyPoint: LineCartesianLayer.Point,
    private val triggerPoint: LineCartesianLayer.Point,
    private val typeByX: Map<Double, EventType>,
) : LineCartesianLayer.PointProvider {
    override fun getPoint(
        entry: LineCartesianLayerModel.Entry,
        extraStore: ExtraStore,
    ): LineCartesianLayer.Point? =
        when (typeByX[entry.x]) {
            EventType.STRATEGY -> strategyPoint
            EventType.TRIGGER -> triggerPoint
            null -> null
        }

    override fun getLargestPoint(extraStore: ExtraStore): LineCartesianLayer.Point? = null
}

/** Draws a guideline and a highlight ring only when the marked point is an event. */
private class EventSelectionMarker(
    private val eventXSet: Set<Double>,
    private val guideline: LineComponent,
    private val indicator: ShapeComponent,
    private val indicatorSize: Dp,
) : CartesianMarker {
    override fun drawOverLayers(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>,
    ) {
        val eventTargets = targets.filter { it.x in eventXSet }
        if (eventTargets.isEmpty()) return
        with(context) {
            eventTargets.forEach { target ->
                guideline.drawVertical(this, target.canvasX, layerBounds.top, layerBounds.bottom)
                (target as? LineCartesianLayerMarkerTarget)?.points?.forEach { point ->
                    val half = indicatorSize.pixels / 2
                    indicator.draw(
                        this,
                        target.canvasX - half,
                        point.canvasY - half,
                        target.canvasX + half,
                        point.canvasY + half,
                    )
                }
            }
        }
    }
}

internal fun overlayOffset(
    selectedOffset: Offset?,
    boxSize: IntSize,
    cardSize: IntSize,
): IntOffset {
    val pos = selectedOffset ?: return IntOffset.Zero
    val gap = 8
    val maxX = (boxSize.width - cardSize.width).coerceAtLeast(0)
    val maxY = (boxSize.height - cardSize.height).coerceAtLeast(0)
    val x = (pos.x - cardSize.width / 2f).toInt().coerceIn(0, maxX)
    val preferAbove = pos.y - cardSize.height - gap >= 0
    val y = if (preferAbove) {
        (pos.y - cardSize.height - gap).toInt().coerceIn(0, maxY)
    } else {
        (pos.y + gap).toInt().coerceIn(0, maxY)
    }
    return IntOffset(x, y)
}

@Composable
private fun EventOverlayCard(event: ChartEvent, modifier: Modifier = Modifier) {
    val typeColor = if (event.type == EventType.STRATEGY) WarnYellow else AlertRed
    val typeLabel = if (event.type == EventType.STRATEGY) "Strategy" else "Monitoring"
    val time = remember(event) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.epochSeconds * 1000))
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(typeColor, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "$typeLabel · $time",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = typeColor,
                )
            }
            event.soc?.let { soc ->
                OverlayRow("SOC", "%.1f%%".format(soc))
            }
            when (event.type) {
                EventType.TRIGGER -> {
                    event.direction?.let { dir ->
                        val threshold = event.thresholdSoc?.let { "%.0f%%".format(it) } ?: "?"
                        OverlayRow("Condition", "${directionLabel(dir)} $threshold")
                    }
                    event.actions.takeIf { it.isNotEmpty() }?.let { actions ->
                        OverlayRow("Actions", actions.joinToString(", ") { actionLabel(it) })
                    }
                    event.minerPreset?.let { preset ->
                        OverlayRow("Preset", presetLabel(preset))
                    }
                }

                EventType.STRATEGY -> {
                    event.strategyName?.let { name ->
                        OverlayRow("Strategy", name)
                    }
                    event.stepName?.let { step ->
                        val from = event.fromStep
                        val to = event.toStep
                        val transition =
                            if (from != null && to != null) "step $from → $to" else null
                        OverlayRow("Step", transition?.let { "$step ($it)" } ?: step)
                    }
                    event.reason?.let { reason ->
                        OverlayRow("Reason", reasonLabel(reason))
                    }
                    event.actions.takeIf { it.isNotEmpty() }?.let { actions ->
                        OverlayRow("Actions", actions.joinToString(", ") { actionLabel(it) })
                    }
                    event.minerPreset?.let { preset ->
                        OverlayRow("Preset", presetLabel(preset))
                    }
                }
            }
            event.error?.let { error ->
                Text(
                    "Action failed: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed,
                )
            }
        }
    }
}

@Composable
private fun OverlayRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(72.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
        )
    }
}

private fun directionLabel(direction: Direction): String = when (direction) {
    Direction.AT_OR_BELOW -> "at or below"
    Direction.AT_OR_ABOVE -> "at or above"
}

private fun actionLabel(action: TriggerAction): String = when (action) {
    TriggerAction.NOTIFY -> "Notify"
    TriggerAction.MINER_ON -> "Miners on"
    TriggerAction.MINER_OFF -> "Miners off"
    TriggerAction.SET_POWER_PRESET -> "Set power preset"
}

private fun presetLabel(preset: MinerPreset): String = when (preset) {
    MinerPreset.LOW -> "Low · 1 kW"
    MinerPreset.EFFICIENT -> "Efficient · 2 kW"
    MinerPreset.MAX -> "Max · 2.76 kW"
}

private fun reasonLabel(reason: String): String = when (reason) {
    "active_hours_end" -> "Active hours ended"
    "condition" -> "SOC condition"
    else -> reason
}

internal fun carryForward(socs: List<Double?>): List<Double> {
    val out = mutableListOf<Double>()
    var last: Double? = null
    for (s in socs) {
        if (s != null) last = s
        if (last != null) out.add(last)
    }
    return out
}
