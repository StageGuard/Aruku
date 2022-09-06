package me.stageguard.aruku.ui.page

import android.annotation.SuppressLint
import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts
import me.stageguard.aruku.preference.proto.AccountsOuterClass.Accounts.AccountInfo
import me.stageguard.aruku.ui.SingleItemLazyColumn
import me.stageguard.aruku.ui.LocalArukuMiraiInterface
import me.stageguard.aruku.ui.theme.ArukuTheme

private const val TAG = "LoginView"

@Composable
fun LoginPage(
    onLoginSuccess: () -> Unit
) {
    val context = LocalArukuMiraiInterface.current
    val viewModel: LoginViewModel = viewModel { LoginViewModel(context) }

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

    val showAdvanced = rememberSaveable { mutableStateOf(false) }
    val showAdvancedIconRotation by animateFloatAsState(if (!showAdvanced.value) 0f else 180f)

    val protocol = rememberSaveable { mutableStateOf(defaultAccountInfo.protocol) }
    val heartbeatStrategy = rememberSaveable { mutableStateOf(defaultAccountInfo.heartbeatStrategy) }
    val heartbeatPeriodMillis = rememberSaveable { mutableStateOf(defaultAccountInfo.heartbeatPeriodMillis) }
    val heartbeatTimeoutMillis = rememberSaveable { mutableStateOf(defaultAccountInfo.heartbeatTimeoutMillis) }
    val statHeartbeatPeriodMillis = rememberSaveable { mutableStateOf(defaultAccountInfo.statHeartbeatPeriodMillis) }
    val autoReconnect = rememberSaveable { mutableStateOf(defaultAccountInfo.autoReconnect) }
    val reconnectionRetryTimes = rememberSaveable { mutableStateOf(defaultAccountInfo.reconnectionRetryTimes) }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = remember { TopAppBarDefaults.pinnedScrollBehavior(topBarState) }

    val internetPermission = rememberPermissionState(android.Manifest.permission.INTERNET)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 25.dp).align(Alignment.TopCenter)) {
            Spacer(Modifier.fillMaxWidth().height(50.dp))
            Text("Let's login",
                style = TextStyle.Default.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text("Login to ArukuMirai to access more function.",
                style = TextStyle.Default.copy(fontSize = 24.sp),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Column(
                modifier = Modifier.padding(10.dp).nestedScroll(scrollBehavior.nestedScrollConnection)
            ) { SingleItemLazyColumn {
                OutlinedTextField(account.value,
                    label = { Text("QQ account", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.padding(top = 30.dp, bottom = 4.dp).fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    isError = if (account.value.isEmpty()) false else !isAccountValid,
                    onValueChange = { account.value = it },
                    enabled = state.value is LoginState.Default
                )
                OutlinedTextField(password.value,
                    label = { Text("Password", style = MaterialTheme.typography.bodyMedium) },
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible.value = !passwordVisible.value }) {
                            Icon(imageVector = ImageVector.vectorResource(
                                if (passwordVisible.value) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                            ), contentDescription = "${if (passwordVisible.value) "Hide" else "Show"} password.")
                        }
                    },
                    visualTransformation = if (passwordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
                    onValueChange = { password.value = it },
                    enabled = state.value is LoginState.Default
                )
                Row(modifier = Modifier
                    .fillMaxWidth().wrapContentHeight().padding(top = 25.dp, bottom = 20.dp)
                    .clickable { showAdvanced.value = !showAdvanced.value }
                ) {
                    Image(
                        ImageVector.vectorResource(R.drawable.ic_expand_more),
                        "Show advanced options.",
                        modifier = Modifier.rotate(showAdvancedIconRotation)
                    )
                    Text("Advanced options", modifier = Modifier.padding(start = 5.dp))
                }
                ExpandedAnimatedVisibility(showAdvanced.value) {
                    Column {
                        CompoundDropdownMenu(
                            list = Accounts.login_protocol.values()
                                .filter { it != Accounts.login_protocol.UNRECOGNIZED },
                            current = protocol.value,
                            description = "Protocol",
                            modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth(),
                            onClickItem = { protocol.value = it },
                            enabled = state.value is LoginState.Default
                        )
                        CompoundDropdownMenu(
                            list = Accounts.heartbeat_strategy.values()
                                .filter { it != Accounts.heartbeat_strategy.UNRECOGNIZED },
                            current = heartbeatStrategy.value,
                            description = "Heartbeat strategy",
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                            onClickItem = { heartbeatStrategy.value = it },
                            enabled = state.value is LoginState.Default
                        )
                        ExpandedAnimatedVisibility(heartbeatStrategy.value != Accounts.heartbeat_strategy.NONE) {
                            Column {
                                ExpandedAnimatedVisibility(heartbeatStrategy.value == Accounts.heartbeat_strategy.REGISTER) {
                                    NumberOutlinedTextField(heartbeatPeriodMillis.value,
                                        description = "Heartbeat period millis",
                                        enabled = state.value is LoginState.Default
                                    ){
                                        heartbeatPeriodMillis.value = it.toLong()
                                    }
                                }
                                ExpandedAnimatedVisibility(heartbeatStrategy.value == Accounts.heartbeat_strategy.STAT_HB) {
                                    NumberOutlinedTextField(statHeartbeatPeriodMillis.value,
                                        description = "Stat heartbeat timeout millis",
                                        enabled = state.value is LoginState.Default
                                    ){
                                        statHeartbeatPeriodMillis.value = it.toLong()
                                    }
                                }
                                NumberOutlinedTextField(heartbeatTimeoutMillis.value,
                                    description = "Heartbeat timeout millis",
                                    enabled = state.value is LoginState.Default
                                ){
                                    heartbeatTimeoutMillis.value = it.toLong()
                                }
                            }
                        }
                        Row(modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                            Checkbox(autoReconnect.value, onCheckedChange = {
                                autoReconnect.value = !autoReconnect.value
                            }, enabled = state.value is LoginState.Default)
                            Text("Auto reconnect", modifier = Modifier.align(Alignment.CenterVertically))
                        }
                        ExpandedAnimatedVisibility(autoReconnect.value) {
                            NumberOutlinedTextField(reconnectionRetryTimes.value, "Reconnection retry times",
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                                enabled = state.value is LoginState.Default
                            ) {
                                reconnectionRetryTimes.value = it.toInt()
                            }
                        }
                    }
                }
                Button(onClick = { onLoginClick(AccountInfo.newBuilder().apply {
                    this.accountNo = account.value.toLong()
                    this.passwordMd5 = password.value
                    this.protocol = protocol.value
                    this.heartbeatStrategy = heartbeatStrategy.value
                    this.heartbeatPeriodMillis = heartbeatPeriodMillis.value
                    this.heartbeatTimeoutMillis = heartbeatTimeoutMillis.value
                    this.statHeartbeatPeriodMillis = statHeartbeatPeriodMillis.value
                    this.autoReconnect = autoReconnect.value
                    this.reconnectionRetryTimes = reconnectionRetryTimes.value
                }.build()) },
                    modifier = Modifier.padding(vertical = 30.dp).fillMaxWidth(),
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
                    AnimatedVisibility(state.value !is LoginState.Default,
                        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 3.dp).size(14.dp),
                            strokeWidth = 3.dp
                        )
                    }
                    Text(if (internetPermission.status.isGranted) "LOGIN" else "Please allow INTERNET permission",
                        style = TextStyle.Default.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                if (state.value is LoginState.LoginFailed) {
                    val failedState = state.value as LoginState.LoginFailed
                    AlertDialog(
                        onDismissRequest = { onLoginFailedClick(failedState.bot) },
                        title = {
                            Text("Login failed", style = MaterialTheme.typography.titleLarge)
                        },
                        text = {
                            Text("""
                                Failed to login account ${failedState.bot}: 
                                ${failedState.cause}
                            """.trimIndent(), style = MaterialTheme.typography.bodyMedium)
                        },
                        confirmButton = {
                            Button(onClick = { onLoginFailedClick(failedState.bot) }) {
                                Text("Confirm")
                            }
                        }
                    )
                } else if (state.value is LoginState.CaptchaRequired) {
                    val captchaState = state.value as LoginState.CaptchaRequired
                    val captchaType = captchaState.type
                    val captchaResult = remember { mutableStateOf("") }
                    AlertDialog(
                        onDismissRequest = { onRetryCaptchaClick(captchaState.bot) },
                        title = {
                            Text("Captcha required", style = MaterialTheme.typography.titleLarge)
                        },
                        text = {
                            Column {
                                Text(
                                    text = buildString {
                                        appendLine("We need to verify captcha while logging account ${captchaState.bot}.")
                                        when (captchaType) {
                                            is CaptchaType.Picture -> {
                                                appendLine("Please enter the verification code.")
                                            }
                                            is CaptchaType.Slider -> {
                                                appendLine("Please open the link to solve the slider and enter slider result.")
                                            }
                                            is CaptchaType.UnsafeDevice -> {
                                                appendLine("Please open the link via Android QQ client and click Confirm after finishing verification.")
                                            }
                                        }
                                        append("Click outside the dialog to regenerate captcha or cancel login.")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 5.dp).align(Alignment.CenterHorizontally)
                                )
                                if (captchaType is CaptchaType.Picture) {
                                    Image(
                                        bitmap = BitmapFactory
                                            .decodeByteArray(captchaType.data, 0, captchaType.data.size)
                                            .asImageBitmap(),
                                        contentDescription = "Picture captcha",
                                        modifier = Modifier.padding(horizontal = 5.dp).align(Alignment.CenterHorizontally)
                                    )
                                } else {
                                    Text(
                                        text = if (captchaType is CaptchaType.Slider) captchaType.url
                                            else if (captchaType is CaptchaType.UnsafeDevice) captchaType.url
                                            else "",
                                        style = TextStyle.Default.copy(
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(5.dp)
                                    )
                                }

                                if (captchaType !is CaptchaType.UnsafeDevice) {
                                    OutlinedTextField(captchaResult.value,
                                        label = {
                                            Text(
                                                text = if (captchaType is CaptchaType.Picture) "Verification code"
                                                    else if (captchaType is CaptchaType.Slider) "Slider result"
                                                    else "",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        modifier = Modifier.padding(5.dp).fillMaxWidth(),
                                        singleLine = true,
                                        shape = RoundedCornerShape(15.dp),
                                        onValueChange = { captchaResult.value = it }
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = { onSubmitCaptchaClick(captchaState.bot, captchaResult.value) }) {
                                Text("Confirm")
                            }
                        },
                        dismissButton = {
                            Button(onClick = { onLoginFailedClick(captchaState.bot) }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> CompoundDropdownMenu(
    list: List<T>, current: T,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean,
    onClickItem: (T) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(current.toString(), onValueChange = {}, readOnly = true,
            label = { Text(description ?: "", style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth().clickable { expanded.value = !expanded.value },
            shape = RoundedCornerShape(15.dp),
            trailingIcon = {
                IconButton(onClick = { expanded.value = !expanded.value }) {
                    Image(
                        ImageVector.vectorResource(R.drawable.ic_expand_more),
                        contentDescription = "Expand ${description ?: ""} dropdown menu."
                    )
                }
            },
            enabled = enabled
        )
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false }
        ) {
            list.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.toString()) },
                    onClick = { onClickItem(p); expanded.value = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("ModifierParameter")
fun NumberOutlinedTextField(
    value: Number,
    description: String? = null,
    modifier: Modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(value.toString(),
        label = { Text(description ?: "", style = MaterialTheme.typography.bodyMedium) },
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(15.dp),
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        onValueChange = onValueChange,
        enabled = enabled
    )
}

@Composable
fun ExpandedAnimatedVisibility(expanded: Boolean, content: @Composable (AnimatedVisibilityScope.() -> Unit)) {
    AnimatedVisibility(expanded,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) { content() }
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