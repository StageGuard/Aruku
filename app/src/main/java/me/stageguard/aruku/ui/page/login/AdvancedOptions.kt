package me.stageguard.aruku.ui.page.login

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.stageguard.aruku.R
import me.stageguard.aruku.ui.common.CompoundDropdownMenu
import me.stageguard.aruku.ui.common.ExpandedAnimatedVisibility
import me.stageguard.aruku.ui.common.NumberOutlinedTextField
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC
import net.mamoe.mirai.utils.BotConfiguration.HeartbeatStrategy
import net.mamoe.mirai.utils.BotConfiguration.MiraiProtocol

/**
 * Created by LoliBall on 2022/9/6 18:35.
 * https://github.com/WhichWho
 */

@Composable
fun AdvancedOptions(
    enabled: Boolean,
    protocol: MutableState<String>,
    heartbeatStrategy: MutableState<String>,
    heartbeatPeriodMillis: MutableState<Long>,
    statHeartbeatPeriodMillis: MutableState<Long>,
    heartbeatTimeoutMillis: MutableState<Long>,
    autoReconnect: MutableState<Boolean>,
    reconnectionRetryTimes: MutableState<Int>
) {
    val showAdvanced = rememberSaveable { mutableStateOf(false) }
    val showAdvancedIconRotation by animateFloatAsState(if (!showAdvanced.value) 0f else 180f)
    Column {
        Row(modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(top = 25.dp, bottom = 20.dp)
            .clickable { showAdvanced.value = !showAdvanced.value }
        ) {
            Image(
                Icons.Outlined.ExpandMore,
                "Show advanced options.",
                modifier = Modifier.rotate(showAdvancedIconRotation)
            )
            Text(
                R.string.advanced_options.stringResC,
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
        ExpandedAnimatedVisibility(showAdvanced.value) {
            Column {
                CompoundDropdownMenu(
                    list = MiraiProtocol.values().toList(),
                    current = MiraiProtocol.valueOf(protocol.value),
                    label = R.string.protocol.stringResC,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .fillMaxWidth(),
                    onClickItem = { protocol.value = it.toString() },
                    enabled = enabled
                )
                CompoundDropdownMenu(
                    list = HeartbeatStrategy.values().toList(),
                    current = HeartbeatStrategy.valueOf(heartbeatStrategy.value),
                    label = R.string.heartbeat_strategy.stringResC,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth(),
                    onClickItem = { heartbeatStrategy.value = it.toString() },
                    enabled = enabled
                )
                ExpandedAnimatedVisibility(heartbeatStrategy.value != HeartbeatStrategy.NONE.toString()) {
                    Column {
                        ExpandedAnimatedVisibility(heartbeatStrategy.value == HeartbeatStrategy.REGISTER.toString()) {
                            NumberOutlinedTextField(
                                heartbeatPeriodMillis.value,
                                label = R.string.heartbeat_period_millis.stringResC,
                                enabled = enabled
                            ) {
                                heartbeatPeriodMillis.value = it.toLong()
                            }
                        }
                        ExpandedAnimatedVisibility(heartbeatStrategy.value == HeartbeatStrategy.STAT_HB.toString()) {
                            NumberOutlinedTextField(
                                statHeartbeatPeriodMillis.value,
                                label = R.string.stat_heartbeat_timeout_millis.stringResC,
                                enabled = enabled
                            ) {
                                statHeartbeatPeriodMillis.value = it.toLong()
                            }
                        }
                        NumberOutlinedTextField(
                            heartbeatTimeoutMillis.value,
                            label = R.string.heartbeat_timeout_millis.stringResC,
                            enabled = enabled
                        ) {
                            heartbeatTimeoutMillis.value = it.toLong()
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth()
                ) {
                    Checkbox(autoReconnect.value, onCheckedChange = {
                        autoReconnect.value = !autoReconnect.value
                    }, enabled = enabled)
                    Text(
                        text = R.string.auto_reconnect.stringResC,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
                ExpandedAnimatedVisibility(autoReconnect.value) {
                    NumberOutlinedTextField(
                        value = reconnectionRetryTimes.value,
                        label = R.string.reconnection_retry_times.stringResC,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                        enabled = enabled
                    ) {
                        reconnectionRetryTimes.value = it.toInt()
                    }
                }
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, backgroundColor = 0xffffffff)
@Composable
fun AdvancedOptionsPreview() {
    ArukuTheme(dynamicColor = false, darkTheme = true) {
        AdvancedOptions(
            enabled = true,
            protocol = mutableStateOf(MiraiProtocol.ANDROID_PHONE.toString()),
            heartbeatStrategy = mutableStateOf(HeartbeatStrategy.STAT_HB.toString()),
            heartbeatPeriodMillis = mutableStateOf(123),
            statHeartbeatPeriodMillis = mutableStateOf(123),
            heartbeatTimeoutMillis = mutableStateOf(123),
            autoReconnect = mutableStateOf(true),
            reconnectionRetryTimes = mutableStateOf(123),
        )
    }
}