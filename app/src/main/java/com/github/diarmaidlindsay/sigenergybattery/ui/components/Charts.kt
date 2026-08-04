package com.github.diarmaidlindsay.sigenergybattery.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.diarmaidlindsay.sigenergybattery.ui.theme.ChargeGreen
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.common.Fill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 24-hour battery SOC line chart (0–100%, 5-minute samples).
 *
 * [socs] are samples oldest → newest where each index is [intervalSeconds] apart,
 * starting at [startEpochSeconds]. Null samples are carried forward from the last
 * known value so the line renders continuously.
 */
@Composable
fun SocHistoryChart(
    startEpochSeconds: Long,
    intervalSeconds: Long,
    socs: List<Double?>,
    modifier: Modifier = Modifier,
    height: Int = 180,
) {
    val cleaned = carryForward(socs)
    if (cleaned.isEmpty()) return

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(cleaned) {
        val x = cleaned.indices.map { it.toDouble() }
        modelProducer.runTransaction {
            lineModel { series(x, cleaned) }
        }
    }

    val bottomFormatter = remember(startEpochSeconds, intervalSeconds) {
        CartesianValueFormatter { _, value, _ ->
            val epoch = startEpochSeconds + value.toLong() * intervalSeconds
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epoch * 1000))
        }
    }

    val zoomState = rememberVicoZoomState(
        initialZoom = Zoom.Content,
        minZoom = Zoom.Content,
    )

    CartesianChartHost(
        rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.Line(
                        fill = LineCartesianLayer.LineFill.single(Fill(ChargeGreen)),
                        areaFill = LineCartesianLayer.AreaFill.single(Fill(ChargeGreen.copy(alpha = 0.15f)))
                    )
                ),
                rangeProvider = CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = 100.0)
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = remember { CartesianValueFormatter.decimal(0, suffix = "%") }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = bottomFormatter,
            )
        ),
        modelProducer,
        zoomState = zoomState,
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
    )
}

private fun carryForward(socs: List<Double?>): List<Double> {
    val out = mutableListOf<Double>()
    var last: Double? = null
    for (s in socs) {
        if (s != null) last = s
        if (last != null) out.add(last)
    }
    return out
}
