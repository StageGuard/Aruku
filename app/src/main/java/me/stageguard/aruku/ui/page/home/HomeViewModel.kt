package me.stageguard.aruku.ui.page.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Message
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.stageguard.aruku.R
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.ui.LocalNavController
import me.stageguard.aruku.ui.page.NAV_CHAT
import me.stageguard.aruku.ui.page.home.contact.HomeContactPage
import me.stageguard.aruku.ui.page.home.message.HomeMessagePage
import me.stageguard.aruku.ui.page.home.profile.HomeProfilePage
import me.stageguard.aruku.ui.page.login.LoginState

class HomeViewModel(
    private val repository: MainRepository,
    @Suppress("LocalVariableName") _accountList: LiveData<List<Long>>,
    composableLifecycleOwner: LifecycleOwner,
) : ViewModel() {
    val currentNavSelection = mutableStateOf(homeNaves[HomeNavSelection.MESSAGE]!!)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val accountObserver = viewModelScope.produce {
        send(_accountList.value ?: listOf())
        _accountList.observe(composableLifecycleOwner) {
            launch { send(it) }
        }
    }
    private val accountListUpdateFlow = MutableStateFlow(0L)
    val accounts: StateFlow<List<BasicAccountInfo>> = accountObserver
        .receiveAsFlow()
        .combine(accountListUpdateFlow) { info, _ -> info }
        .map { list ->
            list.mapNotNull { repository.queryAccountInfo(it) }
                .map { BasicAccountInfo(it.accountNo, it.nickname, it.avatarUrl) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, listOf())

}

enum class HomeNavSelection(val id: Int) {
    MESSAGE(0),
    CONTACT(1),
    PROFILE(2)
}

val homeNaves = mapOf(
    HomeNavSelection.MESSAGE to HomeNav(
        selection = HomeNavSelection.MESSAGE,
        icon = Icons.Default.Message,
        label = R.string.home_nav_message,
        content = {
            val navController = LocalNavController.current
            HomeMessagePage(it) { contact, messageId ->
                navController.navigate("$NAV_CHAT/${contact.toNavArg(messageId)}")
            }
        }
    ),
    HomeNavSelection.CONTACT to HomeNav(
        selection = HomeNavSelection.CONTACT,
        icon = Icons.Default.Contacts,
        label = R.string.home_nav_contact,
        content = { HomeContactPage(it) }
    ),
    HomeNavSelection.PROFILE to HomeNav(
        selection = HomeNavSelection.PROFILE,
        icon = Icons.Default.AccountCircle,
        label = R.string.home_nav_profile,
        content = { HomeProfilePage(it) }
    )
)

data class HomeNav(
    val selection: HomeNavSelection,
    val icon: ImageVector,
    @StringRes val label: Int,
    val content: @Composable (PaddingValues) -> Unit,
)

sealed class AccountState(val bot: Long) {
    object Default : AccountState(-1)
    class Login(bot: Long, val state: LoginState) : AccountState(bot)
    class Online(bot: Long) : AccountState(bot)
    class Offline(bot: Long, val cause: String?) : AccountState(bot)
}

data class BasicAccountInfo(
    val id: Long,
    val nick: String,
    val avatarUrl: String?
)
