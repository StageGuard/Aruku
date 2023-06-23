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
import me.stageguard.aruku.util.LoadState
import me.stageguard.aruku.util.mapOk
import me.stageguard.aruku.domain.MainRepository
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
                    me.stageguard.aruku.common.service.parcel.ContactId(
                        me.stageguard.aruku.common.service.parcel.ContactType.GROUP,
                        it.contact.subject
                    ),
                    it.name,
                    repository.getAvatarUrl(
                        bot,
                        me.stageguard.aruku.common.service.parcel.ContactId(
                            me.stageguard.aruku.common.service.parcel.ContactType.GROUP,
                            it.contact.subject
                        )
                    )
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, LoadState.Loading())

    @UiState
    val friends: StateFlow<LoadState<List<SimpleContactData>>> =
        repository.getFriends(bot).mapOk { data ->
            data.map {
                SimpleContactData(
                    me.stageguard.aruku.common.service.parcel.ContactId(
                        me.stageguard.aruku.common.service.parcel.ContactType.FRIEND,
                        it.contact.subject
                    ),
                    it.name,
                    repository.getAvatarUrl(
                        bot,
                        me.stageguard.aruku.common.service.parcel.ContactId(
                            me.stageguard.aruku.common.service.parcel.ContactType.FRIEND,
                            it.contact.subject
                        )
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
    val contact: me.stageguard.aruku.common.service.parcel.ContactId,
    val name: String,
    val avatarData: Any?,
)