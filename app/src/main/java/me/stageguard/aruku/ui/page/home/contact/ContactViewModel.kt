package me.stageguard.aruku.ui.page.home.contact

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.stageguard.aruku.domain.MainRepository
import me.stageguard.aruku.service.parcel.ArukuContact
import me.stageguard.aruku.service.parcel.ArukuContactType

/**
 * Created by LoliBall on 2022/12/31 17:13.
 * https://github.com/WhichWho
 */
class ContactViewModel(
    private val repository: MainRepository
) : ViewModel() {
    private val _groups: MutableState<Flow<PagingData<SimpleContactData>>?> = mutableStateOf(null)
    private val _friends: MutableState<Flow<PagingData<SimpleContactData>>?> = mutableStateOf(null)
    val groups: State<Flow<PagingData<SimpleContactData>>?> get() = _groups
    val friends: State<Flow<PagingData<SimpleContactData>>?> get() = _friends

    context(CoroutineScope) suspend fun initContacts(account: Long) {
        withContext(Dispatchers.IO) {
            _groups.value = Pager(
                config = PagingConfig(15),
                initialKey = 0,
                pagingSourceFactory = { repository.getGroups(account) }
            ).flow.map { data ->
                data.map {
                    SimpleContactData(
                        ArukuContact(ArukuContactType.GROUP, it.id),
                        it.name,
                        repository.getAvatarUrl(
                            account,
                            ArukuContact(ArukuContactType.GROUP, it.id)
                        )
                    )
                }
            }.cachedIn(viewModelScope)

            _friends.value = Pager(
                config = PagingConfig(15),
                initialKey = 0,
                pagingSourceFactory =  { repository.getFriends(account) }
            ).flow.map { data ->
                data.map {
                    SimpleContactData(
                        ArukuContact(ArukuContactType.FRIEND, it.id),
                        it.name,
                        repository.getAvatarUrl(
                            account,
                            ArukuContact(ArukuContactType.FRIEND, it.id)
                        )
                    )
                }
            }.cachedIn(viewModelScope)
        }
    }
}

data class ContactTab(@StringRes val title: Int, val content: @Composable () -> Unit)

data class SimpleContactData(
    val contact: ArukuContact,
    val name: String,
    val avatarData: Any?,
)