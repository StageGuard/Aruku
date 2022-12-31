package me.stageguard.aruku.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import me.stageguard.aruku.R
import me.stageguard.aruku.util.stringRes

val moeSnackBarQueue = mutableStateListOf<MoeSnackBarData>()

object MoeSnackBar {
    const val LONG = 4000L
    const val SHORT = 2000L
    const val INFINITY = -1L
}

data class MoeSnackBarData @OptIn(ExperimentalMaterialApi::class) constructor(
    val message: MutableState<String>,
    val modifier: Modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 0.dp),
    val duration: Long = MoeSnackBar.SHORT,
    val onCancel: ((MoeSnackBarData) -> Unit)? = null,
    val onConfirm: ((MoeSnackBarData) -> Unit)? = null,
    val cancelLabel: String = R.string.btn_cancel.stringRes,
    val confirmLabel: String = R.string.btn_confirm.stringRes,
    val onDismiss: ((MoeSnackBarData) -> Unit)? = null,
    val animationDuration: Int = 250,
    val show: MutableState<Boolean> = mutableStateOf(false),
    val shouldClear: MutableState<Boolean> = mutableStateOf(false),
    var dismissState: DismissState = DismissState(DismissValue.Default),
    var isCancelPressed: Boolean = false,
    var isConfirmPressed: Boolean = false,
    val content: @Composable ((MoeSnackBarData) -> Unit)? = null,
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MoeSnackBar(modifier: Modifier = Modifier) {
    Column(
        Modifier
            .fillMaxSize()
            .then(modifier)
            .wrapContentHeight(Alignment.Top)
            .verticalScroll(rememberScrollState())
    ) {
        moeSnackBarQueue.forEach {
            LaunchedEffect(it) {
                it.show.value = true
                if (it.duration > -1L) {
                    delay(it.duration + it.animationDuration.toLong())
                    if (it.show.value && !it.shouldClear.value) it.dismiss()
                }
            }
            AnimatedVisibility(
                it.show.value,
                enter = expandVertically(tween(it.animationDuration)) + fadeIn(tween(it.animationDuration)),
                exit = shrinkVertically(tween(it.animationDuration)) + fadeOut(tween(it.animationDuration))
            ) {
                SwipeToDismiss(
                    state = it.dismissState.apply {
                        if (isDismissed(DismissDirection.StartToEnd)) it.dismiss()
                    },
                    background = {},
                    directions = setOf(DismissDirection.StartToEnd),
                ) {
                    if (it.content != null) it.content.run { this(it) } else Snackbar(
                        modifier = it.modifier,
                        backgroundColor = MaterialTheme.colors.background,
                        contentColor = MaterialTheme.colors.contentColorFor(MaterialTheme.colors.background),
                        action = {
                            Row {
                                it.onConfirm?.run {
                                    TextButton({
                                        it.isConfirmPressed = true
                                        this(it)
                                    }) { Text(it.confirmLabel) }
                                }
                                it.onCancel?.run {
                                    TextButton({
                                        it.isCancelPressed = true
                                        this(it)
                                    }) { Text(it.cancelLabel) }
                                }
                            }
                        },
                        actionOnNewLine = it.onConfirm != null
                    ) { Text(it.message.value) }
                }
            }
        }
        if (moeSnackBarQueue.size > 0) Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .padding(8.dp),
            shape = RoundedCornerShape(32.dp),
            elevation = 4.dp,
        ) {
            Text(
                R.string.info_moesnackbar_swipe.stringRes,
                Modifier.padding(8.dp, 0.dp),
                fontSize = 12.sp
            )
        }
    }
}

fun MoeSnackBarData.dismiss() = apply {
    MainScope().launch {
        onDismiss?.run { this(this@dismiss) }
        show.value = false
        delay(animationDuration.toLong())
        shouldClear.value = true
        clearIfAllClosed()
    }
}

private fun clearIfAllClosed() {
    val shouldClear1 = moeSnackBarQueue.all { it.shouldClear.value }
    if (shouldClear1) moeSnackBarQueue.clear()
}

fun MoeSnackBarData.show() = apply {
    MainScope().launch {
        moeSnackBarQueue += this@show
    }
}

fun Any.moeSnackBar(
    duration: Long = MoeSnackBar.SHORT,
    onCancel: ((MoeSnackBarData) -> Unit)? = null,
    onConfirm: ((MoeSnackBarData) -> Unit)? = null,
    cancelLabel: String = R.string.btn_cancel.stringRes,
    confirmLabel: String = R.string.btn_confirm.stringRes,
    onDismiss: ((MoeSnackBarData) -> Unit)? = null,
    animationDuration: Int = 250,
    modifier: Modifier = Modifier.padding(8.dp, 8.dp, 8.dp, 0.dp),
    content: @Composable ((MoeSnackBarData) -> Unit)? = null,
) = MoeSnackBarData(
    mutableStateOf(toString()),
    modifier,
    duration,
    onCancel,
    onConfirm,
    cancelLabel,
    confirmLabel,
    onDismiss,
    animationDuration,
    content = content
).apply { show() }