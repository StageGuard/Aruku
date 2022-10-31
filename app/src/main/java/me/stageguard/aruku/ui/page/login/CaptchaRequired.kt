package me.stageguard.aruku.ui.page.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC

/**
 * Created by LoliBall on 2022/9/6 18:34.
 * https://github.com/WhichWho
 */

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CaptchaRequired(
    state: LoginState.CaptchaRequired,
    onRetryCaptchaClick: (Long) -> Unit,
    onSubmitCaptchaClick: (Long, String) -> Unit,
    onLoginFailedClick: (Long) -> Unit
) {
    val captchaType = state.type
    val captchaResult = remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onRetryCaptchaClick(state.bot) },
        title = {
            Text(
                text = R.string.captcha_required.stringResC,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = if (captchaType !is CaptchaType.SMSRequest) {
                        R.string.verify_captcha_message.stringResC(
                            state.bot.toString(),
                            when (captchaType) {
                                is CaptchaType.Picture -> R.string.verify_captcha_message_code.stringResC
                                is CaptchaType.Slider -> R.string.verify_captcha_message_slider.stringResC
                                is CaptchaType.UnsafeDevice -> R.string.verify_captcha_message_device.stringResC
                                else -> ""
                            }
                        )
                    } else R.string.verify_captcha_sms.stringResC(captchaType.phone ?: "-"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(bottom = 5.dp)
                        .align(Alignment.CenterHorizontally)
                )
                when (captchaType) {
                    is CaptchaType.Picture -> {
                        Image(
                            bitmap = captchaType.imageBitmap,
                            contentDescription = "Picture captcha",
                            modifier = Modifier
                                .padding(horizontal = 5.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }

                    is CaptchaType.Slider, is CaptchaType.UnsafeDevice -> {
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

                    is CaptchaType.SMSRequest -> {}
                }

                if (captchaType !is CaptchaType.UnsafeDevice) {
                    OutlinedTextField(captchaResult.value,
                        label = {
                            Text(
                                text = when (captchaType) {
                                    is CaptchaType.Picture -> R.string.verification_code.stringResC
                                    is CaptchaType.Slider -> R.string.slider_result.stringResC
                                    is CaptchaType.SMSRequest -> R.string.sms_code.stringResC
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
            Button(onClick = { onSubmitCaptchaClick(state.bot, captchaResult.value) }) {
                Text(R.string.confirm.stringResC)
            }
        },
        dismissButton = {
            Button(onClick = { onLoginFailedClick(state.bot) }) {
                Text(R.string.cancel.stringResC)
            }
        }
    )
}

@Preview
@Composable
fun CaptchaRequiredSlider() {
    ArukuTheme {
        CaptchaRequired(
            LoginState.CaptchaRequired(
                123, CaptchaType.Slider(123, "https://example.com/captcha_url")
            ), { }, { _, _ -> }, { }
        )
    }
}

@Preview
@Composable
fun CaptchaRequiredUDL() {
    ArukuTheme {
        CaptchaRequired(
            LoginState.CaptchaRequired(
                123, CaptchaType.UnsafeDevice(123, "https://example.com/udl_url")
            ), { }, { _, _ -> }, { }
        )
    }
}

@Preview
@Composable
fun CaptchaRequiredPicture() {
    ArukuTheme {
        CaptchaRequired(
            LoginState.CaptchaRequired(
                123,
                CaptchaType.Picture(123, ImageBitmap(200, 100))
            ), { }, { _, _ -> }, { }
        )
    }
}

@Preview
@Composable
fun SMSRequest() {
    ArukuTheme {
        CaptchaRequired(
            LoginState.CaptchaRequired(
                123,
                CaptchaType.SMSRequest(123, "+8611451419198")
            ), { }, { _, _ -> }, { }
        )
    }
}