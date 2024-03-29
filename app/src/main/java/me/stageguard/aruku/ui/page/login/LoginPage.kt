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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.*
import me.stageguard.aruku.R
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.service.parcel.AccountLoginData
import me.stageguard.aruku.ui.LocalAccountsState
import me.stageguard.aruku.ui.common.SingleItemLazyColumn
import me.stageguard.aruku.ui.page.UIAccountState
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.stringResC
import org.koin.androidx.compose.koinViewModel

private val logger = createAndroidLogger("LoginPage")

@Composable
fun LoginPage(
    doLogin: (me.stageguard.aruku.common.service.parcel.AccountLoginData) -> Unit,
    submitCaptcha: (String?) -> Unit,

    onLoginFailed: (Long) -> Unit,
    onLoginSuccess: (Long) -> Unit
) {
    val viewModel: LoginViewModel = koinViewModel()

    val accountsState = LocalAccountsState.current
    var loginAccount: Long? by remember { mutableStateOf(null) }
    var lastLoginState: LoginState by remember { mutableStateOf(LoginState.Default) }

    val currentOnLoginSuccess by rememberUpdatedState(onLoginSuccess)
    val currentOnLoginFailed by rememberUpdatedState(onLoginFailed)

    LaunchedEffect(accountsState) {
        when (val state = accountsState[loginAccount]) {
            is UIAccountState.Login -> lastLoginState = state.state
            is UIAccountState.Offline -> if(state.cause != "INIT") {
                lastLoginState = LoginState.Failed(
                    viewModel.accountInfo.value.accountNo,
                    "${state.cause}: ${state.message ?: "unknown cause"}"
                )
            }
            is UIAccountState.Online -> currentOnLoginSuccess(viewModel.accountInfo.value.accountNo)
            else -> {}
        }
    }

    LoginView(
        accountInfo = viewModel.accountInfo,
        protocolList = viewModel.protocols,
        hbList = viewModel.heartbeatStrategies,
        state = lastLoginState,
        onLoginClick = {
            doLogin(viewModel.accountInfo.value)
            loginAccount = it
        },
        onLoginFailedClick = {
            lastLoginState = LoginState.Default
            currentOnLoginFailed(it)
        },
        onSubmitCaptchaClick = { _, result ->
            lastLoginState = LoginState.Logging
            submitCaptcha(result)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun LoginView(
    accountInfo: MutableState<AccountLoginData>,
    protocolList: List<String>,
    hbList: List<String>,
    state: LoginState,
    onLoginClick: (Long) -> Unit,
    onLoginFailedClick: (Long) -> Unit,
    onSubmitCaptchaClick: (Long, String?) -> Unit
) {
    val account = rememberSaveable { mutableStateOf("") }
    val isAccountValid = account.value.toLongOrNull().run { if (this == null) false else (this >= 10000) }
    val password = remember { mutableStateOf("") }
    val isPasswordValid = password.value.length in 6..16
    val passwordVisible = rememberSaveable { mutableStateOf(false) }

    val protocol = rememberSaveable { mutableStateOf(accountInfo.value.protocol) }
    val heartbeatStrategy = rememberSaveable { mutableStateOf(accountInfo.value.heartbeatStrategy) }
    val heartbeatPeriodMillis =
        rememberSaveable { mutableStateOf(accountInfo.value.heartbeatPeriodMillis) }
    val heartbeatTimeoutMillis =
        rememberSaveable { mutableStateOf(accountInfo.value.heartbeatTimeoutMillis) }
    val statHeartbeatPeriodMillis =
        rememberSaveable { mutableStateOf(accountInfo.value.statHeartbeatPeriodMillis) }
    val autoReconnect = rememberSaveable { mutableStateOf(accountInfo.value.autoReconnect) }
    val reconnectionRetryTimes =
        rememberSaveable { mutableStateOf(accountInfo.value.reconnectionRetryTimes) }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 25.dp)
                .align(Alignment.TopCenter)
        ) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
            Text(
                text = R.string.login_message.stringResC,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                text = R.string.login_desc.stringResC,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp),
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
                                R.string.qq_account.stringResC,
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
                        enabled = state is LoginState.Default
                    )
                    OutlinedTextField(
                        value = password.value,
                        label = {
                            Text(
                                R.string.qq_password.stringResC,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .fillMaxWidth(),
                        singleLine = true,
                        isError = if (password.value.isEmpty()) false else !isPasswordValid,
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
                        enabled = state is LoginState.Default
                    )
                    AdvancedOptions(
                        state is LoginState.Default,
                        protocolList,
                        hbList,
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
                        modifier = Modifier
                            .padding(vertical = 30.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        enabled = state is LoginState.Default && isAccountValid && isPasswordValid && internetPermission.status.isGranted,
                        colors = if (internetPermission.status.isGranted) ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer
                        ) else ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        AnimatedVisibility(
                            state !is LoginState.Default,
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
                            if (internetPermission.status.isGranted) R.string.login.stringResC
                            else R.string.internet_permission.stringResC,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    if (state is LoginState.Failed) {
                        AlertDialog(onDismissRequest = { onLoginFailedClick(state.bot) }, title = {
                            Text(
                                text = R.string.login_failed.stringResC,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }, text = {
                            Text(
                                text = R.string.login_failed_message.stringResC(
                                    state.bot.toString(), state.cause
                                ), style = MaterialTheme.typography.bodyMedium
                            )
                        }, confirmButton = {
                            Button(onClick = { onLoginFailedClick(state.bot) }) {
                                Text(R.string.confirm.stringResC)
                            }
                        })
                    } else if (state is LoginState.CaptchaRequired) {
                        CaptchaRequired(
                            state,
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
        val loginState by remember { mutableStateOf(LoginState.Default) }
        LoginView(mutableStateOf(
            AccountLoginData(
                accountNo = 0L,
                passwordMd5 = "",
                protocol = "ANDROID_PHONE",
                heartbeatStrategy = "STAT_HB",
                heartbeatPeriodMillis = 60 * 1000,
                heartbeatTimeoutMillis = 5 * 1000,
                statHeartbeatPeriodMillis = 300 * 1000,
                autoReconnect = true,
                reconnectionRetryTimes = 5
            )
        ),
            listOf(), listOf(),
            loginState,
            onLoginClick = {},
            onLoginFailedClick = {},
            onSubmitCaptchaClick = { _, _ -> })
    }
}