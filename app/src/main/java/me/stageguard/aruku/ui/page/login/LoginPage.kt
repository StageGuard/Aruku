package me.stageguard.aruku.ui.page.login

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import me.stageguard.aruku.R
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts.AccountInfo
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.SingleItemLazyColumn
import me.stageguard.aruku.ui.page.LoginState
import me.stageguard.aruku.ui.page.LoginViewModel
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringRes

private const val TAG = "LoginView"

@Composable
fun LoginPage(
    onLoginSuccess: () -> Unit
) {
    val arukuInterface = LocalArukuMiraiInterface.current
    val viewModel: LoginViewModel = viewModel { LoginViewModel(arukuInterface) }

    if (viewModel.state.value is LoginState.LoginSuccess) {
        onLoginSuccess()
    } else {
        LoginView(
            defaultAccountInfo = LoginViewModel.defaultAccountInfoConfiguration,
            state = viewModel.state,
            onLoginClick = { viewModel.doLogin(it) },
            onLoginFailedClick = { viewModel.removeBotAndClearState(it) },
            onRetryCaptchaClick = { viewModel.retryCaptcha() },
            onSubmitCaptchaClick = { _, result -> viewModel.submitCaptcha(result) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LoginView(
    defaultAccountInfo: AccountInfo,
    state: State<LoginState>,
    onLoginClick: (AccountInfo) -> Unit,
    onLoginFailedClick: (Long) -> Unit,
    onRetryCaptchaClick: (Long) -> Unit,
    onSubmitCaptchaClick: (Long, String) -> Unit
) {
    val account = rememberSaveable { mutableStateOf("") }
    val isAccountValid = account.value.toLongOrNull()?.run { true } ?: true
    val password = remember { mutableStateOf("") }
    val passwordVisible = rememberSaveable { mutableStateOf(false) }

    val protocol = rememberSaveable { mutableStateOf(defaultAccountInfo.protocol) }
    val heartbeatStrategy =
        rememberSaveable { mutableStateOf(defaultAccountInfo.heartbeatStrategy) }
    val heartbeatPeriodMillis =
        rememberSaveable { mutableStateOf(defaultAccountInfo.heartbeatPeriodMillis) }
    val heartbeatTimeoutMillis =
        rememberSaveable { mutableStateOf(defaultAccountInfo.heartbeatTimeoutMillis) }
    val statHeartbeatPeriodMillis =
        rememberSaveable { mutableStateOf(defaultAccountInfo.statHeartbeatPeriodMillis) }
    val autoReconnect = rememberSaveable { mutableStateOf(defaultAccountInfo.autoReconnect) }
    val reconnectionRetryTimes =
        rememberSaveable { mutableStateOf(defaultAccountInfo.reconnectionRetryTimes) }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior(topBarState) }

    val internetPermission = rememberPermissionState(android.Manifest.permission.INTERNET)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 25.dp)
                .align(Alignment.TopCenter)
        ) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp))
            Text(
                text = R.string.login_message.stringRes,
                style = TextStyle.Default.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = R.string.login_desc.stringRes,
                style = TextStyle.Default.copy(fontSize = 24.sp),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                SingleItemLazyColumn {
                    OutlinedTextField(
                        value = account.value,
                        label = {
                            Text(
                                R.string.qq_account.stringRes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .padding(top = 30.dp, bottom = 4.dp)
                            .fillMaxWidth(),
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
                                R.string.qq_password.stringRes,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
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
                        protocol,
                        state,
                        heartbeatStrategy,
                        heartbeatPeriodMillis,
                        statHeartbeatPeriodMillis,
                        heartbeatTimeoutMillis,
                        autoReconnect,
                        reconnectionRetryTimes
                    )
                    Button(
                        onClick = {
                            onLoginClick(AccountInfo.newBuilder().apply {
                                this.accountNo = account.value.toLong()
                                this.passwordMd5 = password.value
                                this.protocol = protocol.value
                                this.heartbeatStrategy = heartbeatStrategy.value
                                this.heartbeatPeriodMillis = heartbeatPeriodMillis.value
                                this.heartbeatTimeoutMillis = heartbeatTimeoutMillis.value
                                this.statHeartbeatPeriodMillis = statHeartbeatPeriodMillis.value
                                this.autoReconnect = autoReconnect.value
                                this.reconnectionRetryTimes = reconnectionRetryTimes.value
                            }.build())
                        },
                        modifier = Modifier
                            .padding(vertical = 30.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        enabled = state.value is LoginState.Default
                                && isAccountValid
                                && account.value.isNotEmpty()
                                && internetPermission.status.isGranted,
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
                                modifier = Modifier
                                    .padding(end = 3.dp)
                                    .size(14.dp),
                                strokeWidth = 3.dp
                            )
                        }
                        Text(
                            if (internetPermission.status.isGranted) R.string.login.stringRes
                            else R.string.internet_permission.stringRes,
                            style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    if (state.value is LoginState.LoginFailed) {
                        val failedState = state.value as LoginState.LoginFailed
                        AlertDialog(
                            onDismissRequest = { onLoginFailedClick(failedState.bot) },
                            title = {
                                Text(
                                    text = R.string.login_failed.stringRes,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Text(
                                    text = R.string.login_failed_message.stringRes(
                                        failedState.bot.toString(),
                                        failedState.cause
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            confirmButton = {
                                Button(onClick = { onLoginFailedClick(failedState.bot) }) {
                                    Text(R.string.confirm.stringRes)
                                }
                            }
                        )
                    } else if (state.value is LoginState.CaptchaRequired) {
                        CaptchaRequired(
                            state,
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

@Preview(uiMode = UI_MODE_NIGHT_NO, backgroundColor = 0xffffffff)
@Composable
fun LoginViewPreview() {
    ArukuTheme(dynamicColor = false, darkTheme = true) {
        LoginView(
            AccountInfo.getDefaultInstance(),
            remember { mutableStateOf(LoginState.Default) },
            onLoginClick = {},
            onLoginFailedClick = {},
            onRetryCaptchaClick = {},
            onSubmitCaptchaClick = { _, _ -> }
        )
    }
}