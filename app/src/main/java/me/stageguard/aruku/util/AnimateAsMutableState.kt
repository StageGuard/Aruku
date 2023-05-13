package me.stageguard.aruku.util

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * mostly copy from [animateValueAsState], make it mutable
 */
@SuppressLint("ComposableNaming")
@Composable
fun <T, V : AnimationVector> animateValueAsMutableState(
    initialValue: T,
    typeConverter: TwoWayConverter<T, V>,
    animationSpec: AnimationSpec<T> = remember { spring() },
    visibilityThreshold: T? = null,
    label: String = "ValueAnimation",
    finishedListener: ((T) -> Unit)? = null
): MutableState<T> {
    val animatable = remember { Animatable(initialValue, typeConverter, visibilityThreshold, label) }
    val listener by rememberUpdatedState(finishedListener)
    val animSpec: AnimationSpec<T> by rememberUpdatedState(animationSpec.run {
        if (visibilityThreshold != null && this is SpringSpec &&
            this.visibilityThreshold != visibilityThreshold
        ) spring(dampingRatio, stiffness, visibilityThreshold)else this
    })

    val channel = remember { Channel<T>(Channel.CONFLATED) }
    LaunchedEffect(channel) {
        for (target in channel) {
            val newTarget = channel.tryReceive().getOrNull() ?: target
            launch {
                if (newTarget != animatable.targetValue) {
                    animatable.animateTo(newTarget, animSpec)
                    listener?.invoke(animatable.value)
                }
            }
        }
    }

    return object : MutableState<T> {
        override var value: T
            get() = animatable.value
            set(value) { channel.trySend(value) }

        override fun component1(): T {
            return animatable.value
        }

        override fun component2(): (T) -> Unit {
            return { channel.trySend(value) }
        }
    }
}

private val defaultFloatSpring = spring<Float>()

@Composable
fun animateFloatAsMutableState(
    initialValue: Float,
    animationSpec: AnimationSpec<Float> = defaultFloatSpring,
    visibilityThreshold: Float = 0.01f,
    label: String = "FloatAnimation",
    finishedListener: ((Float) -> Unit)? = null
): MutableState<Float> {
    val resolvedAnimSpec =
        if (animationSpec === defaultFloatSpring) {
            remember(visibilityThreshold) { spring(visibilityThreshold = visibilityThreshold) }
        } else {
            animationSpec
        }
    return animateValueAsMutableState(
        initialValue,
        Float.VectorConverter,
        resolvedAnimSpec,
        visibilityThreshold,
        label,
        finishedListener
    )
}

private val defaultDpSpring = spring<Dp>(visibilityThreshold = Dp.VisibilityThreshold)

@Composable
fun animateDpAsMutableState(
    initialValue: Dp,
    animationSpec: AnimationSpec<Dp> = defaultDpSpring,
    label: String = "DpAnimation",
    finishedListener: ((Dp) -> Unit)? = null
): State<Dp> {
    return animateValueAsMutableState(
        initialValue,
        Dp.VectorConverter,
        animationSpec,
        label = label,
        finishedListener = finishedListener
    )
}