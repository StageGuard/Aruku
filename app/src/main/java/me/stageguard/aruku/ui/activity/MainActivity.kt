package me.stageguard.aruku.ui.activity

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.heyanle.okkv2.core.Okkv
import com.heyanle.okkv2.core.okkv
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.stageguard.aruku.R
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.service.bridge.DisposableBridge
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.ArukuServiceConnection
import me.stageguard.aruku.service.bridge.BackendStateListener
import me.stageguard.aruku.service.bridge.DelegateBackendBridge
import me.stageguard.aruku.service.parcel.BackendState
import me.stageguard.aruku.ui.LocalStringLocale
import me.stageguard.aruku.ui.LocalSystemUiController
import me.stageguard.aruku.ui.common.MoeSnackBar
import me.stageguard.aruku.ui.page.MainPage
import me.stageguard.aruku.ui.page.SplashPage
import me.stageguard.aruku.ui.theme.ArukuTheme
import me.stageguard.aruku.util.StringLocale
import me.stageguard.aruku.util.stringRes
import me.stageguard.aruku.util.toastShort
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity(), BackendStateListener {

    private val logger = createAndroidLogger()
    private val okkv by inject<Okkv>()

    private val serviceConnection: ArukuServiceConnection = ArukuServiceConnection(this)
    private var backendStateListenerDisposable: DisposableBridge? = null
    private var activeBackend by okkv.okkv<String>("active_backend")

    private val backendEntryActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val component = result.data?.getParcelableExtra<ComponentName>("component")

            logger.i("backend entry start result: $component")

            if (component != null) lifecycleScope.launch {
                val binder = serviceConnection.awaitBinder()
                binder.bindBackendService(component.packageName)
            }
        }

    private var backendBridge: MutableStateFlow<DelegateBackendBridge?> = MutableStateFlow(null)

    private val repo by inject<MainRepository>(mode = LazyThreadSafetyMode.SYNCHRONIZED)
    private val stateFlow by lazy { repo.stateFlow.cancellable().flowWithLifecycle(lifecycle) }

    init {
        lifecycle.addObserver(serviceConnection)
    }

    override fun onState(state: BackendState) {
        when(state) {
            is BackendState.ConnectFailed -> {
                backendBridge.update { null }
                logger.i("backend ${state.id} connect failed.")
                toastShort(R.string.backend_connect_failed.stringRes(state.id, state.reason))
            }
            is BackendState.Disconnected -> {
                backendBridge.update { null }
                logger.i("backend ${state.id} is disconnected.")
            }
            is BackendState.Connected -> {
                val delegateBackendBridge = serviceConnection.binder?.getBackendBridge(state.id)
                backendBridge.update { delegateBackendBridge }
                if(delegateBackendBridge != null) repo.referBackendBridge(delegateBackendBridge)
                logger.i("backend ${state.id} is connected.")
            }
        }



        /**
         * account state flow produced by this backend service.
         */
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val focusManager = LocalFocusManager.current
            val systemUiController = rememberSystemUiController(window)
            val serviceConnected = serviceConnection.connected.observeAsState(false)
            val bridge by backendBridge.collectAsState()

            ArukuTheme(dynamicColor = false) {
                Box(
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = { focusManager.clearFocus() }
                    )
                ) {
                    CompositionLocalProvider(
                        LocalStringLocale provides StringLocale(this@MainActivity),
                        LocalSystemUiController provides systemUiController
                    ) {
                        if (serviceConnected.value && bridge != null) {
                            MainPage { stateFlow }
                        } else {
                            SplashPage()
                        }
                    }
                    MoeSnackBar(Modifier.statusBarsPadding())
                }
            }
        }

        lifecycleScope.launch {
            // ensure aruku service is connected.
            serviceConnection.awaitBinder().apply {
                backendStateListenerDisposable = registerBackendStateListener(this@MainActivity)
            }
            val entries = getExternalBackendEntry()
            if (entries.size == 1) {
                val entry = entries.single()

                val packageName = entry.activityInfo.packageName
                if (activeBackend == null) {
                    activeBackend = packageName
                } else if (activeBackend != packageName) {
                    activeBackend = packageName
                    toastShort(R.string.single_backend_changed.stringRes(packageName))
                }

                backendEntryActivityLauncher.launch(Intent().apply {
                    action = ACTIVITY_ENTRY
                    component = entry.activityInfo.run { ComponentName(packageName, name) }
                })
            }
        }
    }

    override fun onDestroy() {
        backendStateListenerDisposable?.dispose()
        lifecycle.removeObserver(serviceConnection)
        super.onDestroy()
    }

    private fun getExternalBackendEntry(): List<ResolveInfo> {
        return packageManager.queryIntentActivities(
            Intent().apply { action = ACTIVITY_ENTRY },
            PackageManager.GET_META_DATA
        )
    }

    companion object {
        private const val ACTIVITY_ENTRY = "me.stageguard.aruku.BACKEND_SERVICE_ENTRY"
    }
}
