package me.stageguard.aruku.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

@Composable
fun CoerceWidthLayout(
    modifier: Modifier = Modifier,
    verticalAlign: CoerceWidthLayoutAlign = CoerceWidthLayoutAlign.LINEAR,
    content: @Composable (remeasuredWidth: Dp?) -> Unit
) {
    val density = LocalDensity.current

    var width: Int? by remember { mutableStateOf(null) }
    var height by remember { mutableStateOf(0) }

    SubcomposeLayout(modifier = modifier) { constraints ->
        val children = subcompose(CHILDREN) {
            content(with(density) { width?.toDp() })
        }
        val placeable = children.map { it.measure(constraints) }

        width = placeable.maxOf { it.width }
        height = if (verticalAlign == CoerceWidthLayoutAlign.LINEAR) {
            placeable.sumOf { it.height }
        } else {
            placeable.maxOf { it.height }
        }

        layout(width ?: 0, height) {
            var currHeight = 0
            placeable.forEach {
                it.placeRelative(0, currHeight)
                if (verticalAlign == CoerceWidthLayoutAlign.LINEAR) currHeight += it.height
            }
        }
    }
}

private val CHILDREN = "Children"
enum class CoerceWidthLayoutAlign { RELATIVE, LINEAR }
