package me.stageguard.aruku.ui.page.login

import android.annotation.SuppressLint
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.*
import me.stageguard.aruku.R
import me.stageguard.aruku.service.parcel.AccountInfo
import me.stageguard.aruku.ui.common.SingleItemLazyColumn
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.secondsToMillis
import org.koin.androidx.compose.koinViewModel

private const val TAG = "LoginView"

@Composable
fun LoginPage(
    onLoginSuccess: (AccountInfo) -> Unit
) {
    val viewModel: LoginViewModel by koinViewModel()
    SideEffect {
        if (viewModel.state.value is LoginState.Success) onLoginSuccess(viewModel.accountInfo.value)
    }

    LoginView(accountInfo = viewModel.accountInfo,
        state = viewModel.state,
        onLoginClick = { viewModel.doLogin(it) },
        onLoginFailedClick = { viewModel.removeBotAndClearState(it) },
        onRetryCaptchaClick = { viewModel.retryCaptcha() },
        onSubmitCaptchaClick = { _, result -> viewModel.submitCaptcha(result) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LoginView(
    accountInfo: MutableState<AccountInfo>,
    state: State<LoginState>,
    onLoginClick: (Long) -> Unit,
    onLoginFailedClick: (Long) -> Unit,
    onRetryCaptchaClick: (Long) -> Unit,
    onSubmitCaptchaClick: (Long, String) -> Unit
) {
    val account = rememberSaveable { mutableStateOf("") }
    val isAccountValid = account.value.toLongOrNull()?.run { true } ?: true
    val password = remember { mutableStateOf("") }
    val passwordVisible = rememberSaveable { mutableStateOf(false) }

    val protocol = rememberSaveable { mutableStateOf(accountInfo.value.protocol) }
    val heartbeatStrategy = rememberSaveable { mutableStateOf(accountInfo.value.heartbeatStrategy) }
    val heartbeatPeriodMillis = rememberSaveable { mutableStateOf(accountInfo.value.heartbeatPeriodMillis) }
    val heartbeatTimeoutMillis = rememberSaveable { mutableStateOf(accountInfo.value.heartbeatTimeoutMillis) }
    val statHeartbeatPeriodMillis = rememberSaveable { mutableStateOf(accountInfo.value.statHeartbeatPeriodMillis) }
    val autoReconnect = rememberSaveable { mutableStateOf(accountInfo.value.autoReconnect) }
    val reconnectionRetryTimes = rememberSaveable { mutableStateOf(accountInfo.value.reconnectionRetryTimes) }

    val topBarState = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val scrollBehavior = remember { topBarState }

    val internetPermission = if (!LocalInspectionMode.current) {
        rememberPermissionState(android.Manifest.permission.INTERNET)
    } else {
        remember {
            object : PermissionState {
                override val permission: String = android.Manifest.permission.INTERNET
                override val status: PermissionStatus = PermissionStatus.Granted
                override fun launchPermissionRequest() {
                    error("not implemented in inspection mode")
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 25.dp).align(Alignment.TopCenter)
        ) {
            Spacer(
                Modifier.fillMaxWidth().height(50.dp)
            )
            Text(
                text = R.string.login_message.stringResC,
                style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = R.string.login_desc.stringResC,
                style = TextStyle(fontSize = 24.sp),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Column(
                modifier = Modifier.padding(10.dp).nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                SingleItemLazyColumn {
                    OutlinedTextField(
                        value = account.value,
                        label = {
                            Text(
                                R.string.qq_account.stringResC, style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier.padding(top = 30.dp, bottom = 4.dp).fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp),
                        isError = if (account.value.isEmpty()) false else !isAccountValid,
                        onValueChange = { account.value = it },
                        enabled = state.value is LoginState.Default
                    )
                    OutlinedTextField(
                        value = password.value,
                        label = {
                            Text(
                                R.string.qq_password.stringResC, style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(15.dp),
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = {
                                passwordVisible.value = !passwordVisible.value
                            }) {
                                Icon(
                                    imageVector = if (passwordVisible.value) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "${if (passwordVisible.value) "Hide" else "Show"} password."
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
                        onValueChange = { password.value = it },
                        enabled = state.value is LoginState.Default
                    )
                    AdvancedOptions(
                        state.value is LoginState.Default,
                        protocol,
                        heartbeatStrategy,
                        heartbeatPeriodMillis,
                        statHeartbeatPeriodMillis,
                        heartbeatTimeoutMillis,
                        autoReconnect,
                        reconnectionRetryTimes
                    )
                    Button(
                        onClick = {
                            accountInfo.value.apply a@{

                                this@a.accountNo = account.value.toLong()
                                this@a.passwordMd5 = password.value
                                this@a.protocol = protocol.value
                                this@a.heartbeatStrategy = heartbeatStrategy.value
                                this@a.heartbeatPeriodMillis = heartbeatPeriodMillis.value
                                this@a.heartbeatTimeoutMillis = heartbeatTimeoutMillis.value
                                this@a.statHeartbeatPeriodMillis = statHeartbeatPeriodMillis.value
                                this@a.autoReconnect = autoReconnect.value
                                this@a.reconnectionRetryTimes = reconnectionRetryTimes.value
                            }
                            onLoginClick(account.value.toLong())
                        },
                        modifier = Modifier.padding(vertical = 30.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        enabled = state.value is LoginState.Default && isAccountValid && account.value.isNotEmpty() && internetPermission.status.isGranted,
                        colors = if (internetPermission.status.isGranted) ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        AnimatedVisibility(
                            state.value !is LoginState.Default,
                            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 3.dp).size(14.dp), strokeWidth = 3.dp
                            )
                        }
                        Text(
                            if (internetPermission.status.isGranted) R.string.login.stringResC
                            else R.string.internet_permission.stringResC,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    if (state.value is LoginState.Failed) {
                        val failedState = state.value as LoginState.Failed
                        AlertDialog(onDismissRequest = { onLoginFailedClick(failedState.bot) }, title = {
                            Text(
                                text = R.string.login_failed.stringResC, style = MaterialTheme.typography.titleLarge
                            )
                        }, text = {
                            Text(
                                text = R.string.login_failed_message.stringResC(
                                    failedState.bot.toString(), failedState.cause
                                ), style = MaterialTheme.typography.bodyMedium
                            )
                        }, confirmButton = {
                            Button(onClick = { onLoginFailedClick(failedState.bot) }) {
                                Text(R.string.confirm.stringResC)
                            }
                        })
                    } else if (state.value is LoginState.CaptchaRequired) {
                        CaptchaRequired(
                            state.value as LoginState.CaptchaRequired,
                            onRetryCaptchaClick,
                            onSubmitCaptchaClick,
                            onLoginFailedClick
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview(uiMode = UI_MODE_NIGHT_NO, backgroundColor = 0xffffffff)
@Composable
fun LoginViewPreview() {
    ArukuTheme(dynamicColor = false, darkTheme = true) {
        LoginView(mutableStateOf(
            AccountInfo(
                accountNo = 0L,
                passwordMd5 = "",
                protocol = BotConfiguration.MiraiProtocol.ANDROID_PHONE.toString(),
                heartbeatStrategy = BotConfiguration.HeartbeatStrategy.STAT_HB.toString(),
                heartbeatPeriodMillis = 60.secondsToMillis,
                heartbeatTimeoutMillis = 5.secondsToMillis,
                statHeartbeatPeriodMillis = 300.secondsToMillis,
                autoReconnect = true,
                reconnectionRetryTimes = 5
            )
        ),
            remember { mutableStateOf(LoginState.Default) },
            onLoginClick = {},
            onLoginFailedClick = {},
            onRetryCaptchaClick = {},
            onSubmitCaptchaClick = { _, _ -> })
    }
}