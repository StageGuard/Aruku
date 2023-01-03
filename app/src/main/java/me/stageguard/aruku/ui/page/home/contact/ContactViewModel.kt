package me.stageguard.aruku.ui.page.home.contact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.parcel.ArukuMessageType

/**
 * Created by LoliBall on 2022/12/31 17:13.
 * https://github.com/WhichWho
 */
class ContactViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val database: ArukuDatabase,
) : ViewModel() {
    private val _groups: MutableState<Flow<PagingData<SimpleContactData>>?> = mutableStateOf(null)
    private val _friends: MutableState<Flow<PagingData<SimpleContactData>>?> = mutableStateOf(null)
    val groups: State<Flow<PagingData<SimpleContactData>>?> get() = _groups
    val friends: State<Flow<PagingData<SimpleContactData>>?> get() = _friends

    suspend fun initContacts(account: Long) {
        withContext(Dispatchers.IO) {
            _groups.value = Pager(config = PagingConfig(15), initialKey = 0) {
                database.groups().getGroupsPaging(account)
            }.flow.map { data ->
                data.map {
                    SimpleContactData(
                        it.id,
                        it.name,
                        arukuServiceInterface.getAvatar(account, ArukuMessageType.GROUP.ordinal, it.id)
                    )
                }
            }.cachedIn(viewModelScope)

            _friends.value = Pager(config = PagingConfig(15), initialKey = 0) {
                database.friends().getFriendsPaging(account)
            }.flow.map { data ->
                data.map {
                    SimpleContactData(
                        it.id,
                        it.name,
                        arukuServiceInterface.getAvatar(account, ArukuMessageType.FRIEND.ordinal, it.id)
                    )
                }
            }.cachedIn(viewModelScope)
        }
    }
}

data class ContactTab(val title: Int, val content: @Composable () -> Unit)

data class SimpleContactData(
    val id: Long,
    val name: String,
    val avatarData: Any?,
)