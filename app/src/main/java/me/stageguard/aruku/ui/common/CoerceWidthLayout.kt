package me.stageguard.aruku.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

@Composable
fun CoerceWidthLayout(
    modifier: Modifier = Modifier,
    verticalAlign: CoerceWidthLayoutAlign = CoerceWidthLayoutAlign.LINEAR,
    content: @Composable (remeasuredWidth: Dp?) -> Unit
) {
    val density = LocalDensity.current

    SubcomposeLayout(modifier = modifier) { constraints ->
        val main = subcompose(SubcomposeSlot.Main) {
            content(null)
        }.map { it.measure(constraints) }

        val maxSize = main.foldRight(IntSize.Zero) { e, acc ->
            IntSize(
                maxOf(e.width, acc.width),
                if (verticalAlign == CoerceWidthLayoutAlign.LINEAR)
                    acc.height + e.height else maxOf(acc.height, e.height)
            )
        }

        val dependent = subcompose(SubcomposeSlot.Dependent) {
            content(with(density) { maxSize.width.toDp() })
        }.map { it.measure(constraints.copy(minWidth = maxSize.width)) }

        layout(maxSize.width, maxSize.height) {
            var currHeight = 0
            dependent.forEach {
                it.placeRelative(0, currHeight)
                if (verticalAlign == CoerceWidthLayoutAlign.LINEAR) currHeight += it.height
            }
        }
    }
}

private enum class SubcomposeSlot { Main, Dependent }
enum class CoerceWidthLayoutAlign { RELATIVE, LINEAR }
