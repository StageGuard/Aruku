package me.stageguard.aruku.ui.page.login

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import me.stageguard.aruku.R
import me.stageguard.aruku.preference.proto.AccountsOuterClass
import me.stageguard.aruku.ui.common.CompoundDropdownMenu
import me.stageguard.aruku.ui.common.ExpandedAnimatedVisibility
import me.stageguard.aruku.ui.common.NumberOutlinedTextField
import me.stageguard.aruku.util.stringResC

/**
 * Created by LoliBall on 2022/9/6 18:35.
 * https://github.com/WhichWho
 */

@Composable
fun AdvancedOptions(
    state: State<LoginState>,
    protocol: MutableState<AccountsOuterClass.Accounts.login_protocol>,
    heartbeatStrategy: MutableState<AccountsOuterClass.Accounts.heartbeat_strategy>,
    heartbeatPeriodMillis: MutableState<Long>,
    statHeartbeatPeriodMillis: MutableState<Long>,
    heartbeatTimeoutMillis: MutableState<Long>,
    autoReconnect: MutableState<Boolean>,
    reconnectionRetryTimes: MutableState<Int>
) {
    val showAdvanced = rememberSaveable { mutableStateOf(false) }
    val showAdvancedIconRotation by animateFloatAsState(if (!showAdvanced.value) 0f else 180f)
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
            modifier = Modifier.padding(start = 5.dp)
        )
    }
    ExpandedAnimatedVisibility(showAdvanced.value) {
        Column {
            CompoundDropdownMenu(
                list = AccountsOuterClass.Accounts.login_protocol.values()
                    .filter { it != AccountsOuterClass.Accounts.login_protocol.UNRECOGNIZED },
                current = protocol.value,
                label = R.string.protocol.stringResC,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .fillMaxWidth(),
                onClickItem = { protocol.value = it },
                enabled = state.value is LoginState.Default
            )
            CompoundDropdownMenu(
                list = AccountsOuterClass.Accounts.heartbeat_strategy.values()
                    .filter { it != AccountsOuterClass.Accounts.heartbeat_strategy.UNRECOGNIZED },
                current = heartbeatStrategy.value,
                label = R.string.heartbeat_strategy.stringResC,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .fillMaxWidth(),
                onClickItem = { heartbeatStrategy.value = it },
                enabled = state.value is LoginState.Default
            )
            ExpandedAnimatedVisibility(heartbeatStrategy.value != AccountsOuterClass.Accounts.heartbeat_strategy.NONE) {
                Column {
                    ExpandedAnimatedVisibility(heartbeatStrategy.value == AccountsOuterClass.Accounts.heartbeat_strategy.REGISTER) {
                        NumberOutlinedTextField(
                            heartbeatPeriodMillis.value,
                            label = R.string.heartbeat_period_millis.stringResC,
                            enabled = state.value is LoginState.Default
                        ) {
                            heartbeatPeriodMillis.value = it.toLong()
                        }
                    }
                    ExpandedAnimatedVisibility(heartbeatStrategy.value == AccountsOuterClass.Accounts.heartbeat_strategy.STAT_HB) {
                        NumberOutlinedTextField(
                            statHeartbeatPeriodMillis.value,
                            label = R.string.stat_heartbeat_timeout_millis.stringResC,
                            enabled = state.value is LoginState.Default
                        ) {
                            statHeartbeatPeriodMillis.value = it.toLong()
                        }
                    }
                    NumberOutlinedTextField(
                        heartbeatTimeoutMillis.value,
                        label = R.string.heartbeat_timeout_millis.stringResC,
                        enabled = state.value is LoginState.Default
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
                }, enabled = state.value is LoginState.Default)
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
                    enabled = state.value is LoginState.Default
                ) {
                    reconnectionRetryTimes.value = it.toInt()
                }
            }
        }
    }
}
