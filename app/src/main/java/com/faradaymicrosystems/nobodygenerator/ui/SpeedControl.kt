package com.faradaymicrosystems.nobodygenerator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt

/** Speed is always adjusted in 0.05× steps. */
const val SPEED_STEP = 0.05f

/** Rounds to the nearest 0.05× so values never drift (1.0500001) and always land on a step. */
fun snapSpeed(v: Float, range: ClosedFloatingPointRange<Float>): Float =
    ((v / SPEED_STEP).roundToInt() * SPEED_STEP).coerceIn(range.start, range.endInclusive)

/** Number of in-between slider stops so every 0.05× value is a stop. */
private fun stepsFor(range: ClosedFloatingPointRange<Float>): Int =
    ((range.endInclusive - range.start) / SPEED_STEP).roundToInt() - 1

/** "Speed 1.25×" label, then  −  [slider]  +  — the buttons nudge by exactly 0.05×. */
@Composable
fun SpeedControl(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val v = snapSpeed(value, range)
    Column(modifier) {
        Text("Speed  ${"%.2f".format(v)}×", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onChange(snapSpeed(v - SPEED_STEP, range)) },
                enabled = enabled && v > range.start,
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Slider(
                value = v,
                onValueChange = { onChange(snapSpeed(it, range)) },
                valueRange = range,
                steps = stepsFor(range),
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = { onChange(snapSpeed(v + SPEED_STEP, range)) },
                enabled = enabled && v < range.endInclusive,
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
