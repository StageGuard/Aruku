package me.stageguard.aruku.ui.page.login

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.page.CaptchaType
import me.stageguard.aruku.ui.page.LoginState
import me.stageguard.aruku.util.stringRes

/**
 * Created by LoliBall on 2022/9/6 18:34.
 * https://github.com/WhichWho
 */

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CaptchaRequired(
    state: State<LoginState>,
    onRetryCaptchaClick: (Long) -> Unit,
    onSubmitCaptchaClick: (Long, String) -> Unit,
    onLoginFailedClick: (Long) -> Unit
) {
    val captchaState = state.value as LoginState.CaptchaRequired
    val captchaType = captchaState.type
    val captchaResult = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onRetryCaptchaClick(captchaState.bot) },
        title = {
            Text(
                text = R.string.captcha_required.stringRes,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = R.string.verify_captcha_message.stringRes(
                        captchaState.bot.toString(),
                        when (captchaType) {
                            is CaptchaType.Picture -> R.string.verify_captcha_message_code
                            is CaptchaType.Slider -> R.string.verify_captcha_message_slider
                            is CaptchaType.UnsafeDevice -> R.string.verify_captcha_message_device
                            else -> error("never occur")
                        }.stringRes
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(bottom = 5.dp)
                        .align(Alignment.CenterHorizontally)
                )
                if (captchaType is CaptchaType.Picture) {
                    Image(
                        bitmap = BitmapFactory
                            .decodeByteArray(captchaType.data, 0, captchaType.data.size)
                            .asImageBitmap(),
                        contentDescription = "Picture captcha",
                        modifier = Modifier
                            .padding(horizontal = 5.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = if (captchaType is CaptchaType.Slider) captchaType.url
                            else if (captchaType is CaptchaType.UnsafeDevice) captchaType.url
                            else "",
                            style = TextStyle.Default.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(5.dp)
                        )
                    }
                }

                if (captchaType !is CaptchaType.UnsafeDevice) {
                    OutlinedTextField(captchaResult.value,
                        label = {
                            Text(
                                text = when (captchaType) {
                                    is CaptchaType.Picture -> R.string.verification_code.stringRes
                                    is CaptchaType.Slider -> R.string.slider_result.stringRes
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .padding(5.dp)
                            .fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp),
                        onValueChange = { captchaResult.value = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSubmitCaptchaClick(captchaState.bot, captchaResult.value) }) {
                Text(R.string.confirm.stringRes)
            }
        },
        dismissButton = {
            Button(onClick = { onLoginFailedClick(captchaState.bot) }) {
                Text(R.string.cancel.stringRes)
            }
        }
    )
}