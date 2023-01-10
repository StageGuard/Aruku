package me.stageguard.aruku.ui.page.chat

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.stageguard.aruku.database.ArukuDatabase
import me.stageguard.aruku.service.IArukuMiraiInterface
import me.stageguard.aruku.service.parcel.ArukuContact

class ChatViewModel(
    private val arukuServiceInterface: IArukuMiraiInterface,
    private val database: ArukuDatabase,
    private val contact: ArukuContact,
) : ViewModel() {
    private val _subjectName = mutableStateOf(contact.subject.toString())
    val subjectName: State<String> = _subjectName


    context(CoroutineScope) fun initChatInfoState(bot: Long) {
        this@CoroutineScope.launch {
            _subjectName.value = arukuServiceInterface.getNickname(bot, contact)
        }
    }
}