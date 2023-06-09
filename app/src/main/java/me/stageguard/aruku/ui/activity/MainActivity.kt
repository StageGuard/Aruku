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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.heyanle.okkv2.core.Okkv
import com.heyanle.okkv2.core.okkv
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import me.stageguard.aruku.MainRepositoryImpl
import me.stageguard.aruku.R
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge
import me.stageguard.aruku.common.service.bridge.ArukuBackendBridge_Proxy
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.ServiceConnector
import me.stageguard.aruku.service.bridge.BackendStateListener
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

    private val serviceConnector: ServiceConnector = ServiceConnector(this)
    private var activeBackend by okkv.okkv<String>("active_backend")

    private val backendEntryActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val intent = result.data ?: return@registerForActivityResult
            val component = intent.getParcelableExtra<ComponentName>("component")

            logger.i("backend entry start result: $component")

            if (component != null) lifecycleScope.launch {
                val binder = serviceConnector.awaitBinder()
                backendLifecycle.addObserver(repo as MainRepositoryImpl)
                binder.bindBackendService(component.packageName)
            }
        }

    private var backendBridge: ArukuBackendBridge? = null
    private val backendLifecycle = LifecycleRegistry(this@MainActivity)

    private val repo by inject<MainRepository>(mode = LazyThreadSafetyMode.SYNCHRONIZED)
    private val stateFlow by lazy { repo.stateFlow.cancellable().flowWithLifecycle(lifecycle) }

    init {
        lifecycle.addObserver(serviceConnector)
    }

    override fun onState(state: BackendState) {
        when(state) {
            is BackendState.ConnectFailed -> {
                backendLifecycle.currentState = Lifecycle.State.DESTROYED
                logger.i("backend ${state.id} connect failed.")
                toastShort(R.string.backend_connect_failed.stringRes(state.id, state.reason))
            }
            is BackendState.Disconnected -> {
                backendBridge = null
                backendLifecycle.currentState = Lifecycle.State.DESTROYED
                logger.i("backend ${state.id} is disconnected.")
            }
            is BackendState.Connected -> {
                backendLifecycle.currentState = Lifecycle.State.STARTED
                val bridge = object : ArukuBackendBridge by ArukuBackendBridge_Proxy(state.bridge) { }
                backendBridge = bridge
                repo.referBackendBridge(bridge)
                logger.i("backend ${state.id} is connected.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val focusManager = LocalFocusManager.current
            val systemUiController = rememberSystemUiController(window)
            val serviceConnected = serviceConnector.connected.observeAsState(false)

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
                        if (serviceConnected.value && backendBridge != null) {
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
            serviceConnector.awaitBinder().apply {
                registerBackendStateListener(this@MainActivity)
            }
            backendLifecycle.currentState = Lifecycle.State.INITIALIZED

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
                    action = Intent.ACTION_VIEW
                    component = entry.activityInfo.run { ComponentName(packageName, name) }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        }
    }

    override fun onDestroy() {
        lifecycle.removeObserver(serviceConnector)
        backendLifecycle.currentState = Lifecycle.State.DESTROYED
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
