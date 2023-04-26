package me.stageguard.aruku.ui.page.home.contact

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.stageguard.aruku.database.LoadState
import me.stageguard.aruku.database.mapOk
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType
import me.stageguard.aruku.ui.UiState

/**
 * Created by LoliBall on 2022/12/31 17:13.
 * https://github.com/WhichWho
 */
class ContactViewModel(
    private val repository: MainRepository,
    private val bot: Long,
) : ViewModel() {
    private val contactUpdateFlow = MutableStateFlow(0L)

    @UiState
    val groups: StateFlow<LoadState<List<SimpleContactData>>> =
        repository.getGroups(bot).mapOk { data ->
            data.map {
                SimpleContactData(
                    ArukuContact(ArukuContactType.GROUP, it.id),
                    it.name,
                    repository.getAvatarUrl(
                        bot,
                        ArukuContact(ArukuContactType.GROUP, it.id)
                    )
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, LoadState.Loading())

    @UiState
    val friends: StateFlow<LoadState<List<SimpleContactData>>> =
        repository.getFriends(bot).mapOk { data ->
            data.map {
                SimpleContactData(
                    ArukuContact(ArukuContactType.FRIEND, it.id),
                    it.name,
                    repository.getAvatarUrl(
                        bot,
                        ArukuContact(ArukuContactType.FRIEND, it.id)
                    )
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, LoadState.Loading())

    fun updateContacts() {
        viewModelScope.launch {
            contactUpdateFlow.emit(System.currentTimeMillis())
        }
    }
}

data class ContactTab(@StringRes val title: Int, val content: @Composable () -> Unit)

data class SimpleContactData(
    val contact: ArukuContact,
    val name: String,
    val avatarData: Any?,
)