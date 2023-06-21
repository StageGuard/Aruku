package me.stageguard.aruku.mah

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.serializer
import me.stageguard.aruku.common.createAndroidLogger
import me.stageguard.aruku.common.service.BaseArukuBackend
import me.stageguard.aruku.common.service.bridge.BotStateObserver
import me.stageguard.aruku.common.service.bridge.ContactSyncBridge
import me.stageguard.aruku.common.service.bridge.DisposableBridge
import me.stageguard.aruku.common.service.bridge.LoginSolverBridge
import me.stageguard.aruku.common.service.bridge.MessageSubscriber
import me.stageguard.aruku.common.service.bridge.RoamingQueryBridge
import me.stageguard.aruku.common.service.parcel.AccountInfo
import me.stageguard.aruku.common.service.parcel.AccountInfoImpl
import me.stageguard.aruku.common.service.parcel.AccountLoginData
import me.stageguard.aruku.common.service.parcel.AccountProfile
import me.stageguard.aruku.common.service.parcel.AccountState
import me.stageguard.aruku.common.service.parcel.AccountState.OfflineCause
import me.stageguard.aruku.common.service.parcel.ContactId
import me.stageguard.aruku.common.service.parcel.ContactInfo
import me.stageguard.aruku.common.service.parcel.ContactSyncOp
import me.stageguard.aruku.common.service.parcel.ContactType
import me.stageguard.aruku.common.service.parcel.GroupMemberInfo
import me.stageguard.aruku.common.service.parcel.Message
import me.stageguard.aruku.common.service.parcel.MessageImpl
import me.stageguard.aruku.common.service.parcel.RemoteFile
import me.stageguard.aruku.mah.dto.calculateMessageId
import me.stageguard.aruku.mah.dto.json
import me.stageguard.aruku.mah.dto.toMessageElements
import net.mamoe.mirai.api.http.adapter.internal.dto.BotJoinGroupEventDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.BotLeaveEventActiveDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.BotLeaveEventDisbandDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.BotLeaveEventKickDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.BotOfflineEventActiveDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.BotOfflineEventDroppedDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.BotOfflineEventForceDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.BotOnlineEventDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.DTO
import net.mamoe.mirai.api.http.adapter.internal.dto.ElementResult
import net.mamoe.mirai.api.http.adapter.internal.dto.EventDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.FileInfoDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.FriendList
import net.mamoe.mirai.api.http.adapter.internal.dto.FriendMessagePacketDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.FriendSyncMessagePacketDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.GroupDetailDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.GroupList
import net.mamoe.mirai.api.http.adapter.internal.dto.GroupMessagePacketDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.GroupSyncMessagePacketDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.LongTargetDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.MemberDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.MemberTargetDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.MessagePacketDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.MessageSourceDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.ProfileDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.RemoteFileDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.TempMessagePacketDTO
import net.mamoe.mirai.api.http.adapter.internal.dto.TempSyncMessagePacketDTO
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

class ArukuMiraiApiHttpService: BaseArukuBackend() {
    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 74
        private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ArukuMiraiApiHttpService"
    }

    private val logger = createAndroidLogger()
    private val client = HttpClient(OkHttp) {
        install(WebSockets)
    }

    private val session: ConcurrentHashMap<Long, BotSession> = ConcurrentHashMap()
    private val eventJob: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    private var stateObserver: BotStateObserver? = null
    private val stateChannel = Channel<AccountState>()
    private val stateCacheQueue: ConcurrentLinkedQueue<AccountState> = ConcurrentLinkedQueue()
    private val lastState: ConcurrentHashMap<Long, AccountState> = ConcurrentHashMap()

    private var contactSyncer: ContactSyncBridge? = null

    private var messageSubscriber: MessageSubscriber? = null
    private val messageCacheQueue: ConcurrentLinkedQueue<Message> = ConcurrentLinkedQueue()

    private val accountInfoCache: ConcurrentHashMap<Long, AccountInfoImpl> = ConcurrentHashMap()

    override val serviceName: String = "ArukuMiraiApiHttp"
    override val notificationId: Int = FOREGROUND_NOTIFICATION_ID
    override val notificationChannelId: String = FOREGROUND_NOTIFICATION_CHANNEL_ID
    override val notificationIcon: Int = R.drawable.ic_launcher_foreground

    override fun onCreate0() {
        launch {
            val channelFlow = stateChannel.consumeAsFlow()

            channelFlow.flowOn(Dispatchers.Main).collect { state ->
                if (stateObserver == null) {
                    // cache state
                    // this often happens that login process has already produced states
                    // but no state bridge to consume.
                    // so we cache state and dispatch all when state bridge is set.
                    stateCacheQueue.offer(state)
                    return@collect
                }
                // dispatch state to bridge directly
                tryOrMarkAsDead(
                    block = { stateObserver?.dispatchState(state) },
                    onDied = { stateCacheQueue.offer(state) }
                )
            }
        }
        logger.i("service is created.")
    }

    override fun onDestroy0() {

    }

    override fun addBot(info: AccountLoginData?, alsoLogin: Boolean): Boolean {
        logger.i("add bot: $info")

        //client.webSocket("ws")

        if (info == null) return false
        addBot(info)
        if (alsoLogin) login(info.accountNo)
        return true
    }

    override fun getBots(): List<Long> {
        return session.keys().toList()
    }

    override fun attachBotStateObserver(observer: BotStateObserver): DisposableBridge {
        if (stateObserver != null) logger.w("attaching multiple BotStateObserver.")
        stateObserver = observer

        // dispatch latest cached state of each bot.
        launch {
            val dispatched = mutableListOf<Long>()
            var state = stateCacheQueue.poll()
            while (state != null) {
                if (state.account !in dispatched) {
                    observer.dispatchState(state)
                    dispatched.add(state.account)
                }
                state = stateCacheQueue.poll()
            }
        }
        return DisposableBridge {
            stateObserver = null
        }
    }

    override fun getLastBotState(): Map<Long, AccountState> {
        return lastState
    }

    override fun attachLoginSolver(solver: LoginSolverBridge): DisposableBridge {
        return DisposableBridge {  }
    }

    override fun openRoamingQuery(account: Long, contact: ContactId): RoamingQueryBridge? {
        return createRoamingQuerySession(account, contact)
    }

    override fun getAccountOnlineState(account: Long): Boolean {
        return lastState[account] is AccountState.Online
    }

    override fun getAvatarUrl(account: Long, contact: ContactId): String {
        return when (contact.type) {
            ContactType.GROUP -> "http://p.qlogo.cn/gh/${contact.subject}/${contact.subject}/640"
            ContactType.FRIEND,
            ContactType.TEMP -> "http://q.qlogo.cn/g?b=qq&nk=${contact.subject}&s=640"
        }
    }

    override fun getNickname(account: Long, contact: ContactId): String? {
        val session = session[account] ?: return null
        if (!session.connected) return null

        return runBlocking(Dispatchers.IO) {
            when (contact.type) {
                ContactType.GROUP -> session.sendAndExpect<GroupDetailDTO>(
                    "groupConfig",
                    LongTargetDTO(contact.subject),
                    subCommand = "get"
                ).name

                ContactType.FRIEND -> session.sendAndExpect<ProfileDTO>(
                    "friendProfile",
                    LongTargetDTO(contact.subject)
                ).nickname

                ContactType.TEMP -> contact.subject.toString() // TODO
            }
        }
    }

    override fun getGroupMemberInfo(
        account: Long,
        groupId: Long,
        memberId: Long
    ): GroupMemberInfo? {
        val session = session[account] ?: return null
        if (!session.connected) return null

        return runBlocking(Dispatchers.IO) {
            val memberInfo = session.sendAndExpect<MemberDTO>(
                "memberInfo",
                MemberTargetDTO(groupId, memberId),
                subCommand = "get"
            )
            GroupMemberInfo(
                memberId,
                memberInfo.memberName,
                getAvatarUrl(account, ContactId(ContactType.FRIEND, memberId))
            )
        }
    }

    override fun queryAccountInfo(account: Long): AccountInfo? {
        val existing = accountInfoCache[account]
        if (existing != null) return existing

        val session = session[account] ?: return null
        if (!session.connected) return null

        return runBlocking(Dispatchers.IO) {
            val profile = session.sendAndExpect<ProfileDTO>("botProfile")
            AccountInfoImpl(
                account,
                profile.nickname,
                getAvatarUrl(account, ContactId(ContactType.FRIEND, account))
            ).also { accountInfoCache[account] = it }
        }
    }

    override fun queryAccountProfile(account: Long): AccountProfile? {
        val session = session[account] ?: return null
        if (!session.connected) return null

        return runBlocking(Dispatchers.IO) {
            val profile = session.sendAndExpect<ProfileDTO>("botProfile")
            AccountProfile(
                accountNo = account,
                nickname = profile.nickname,
                avatarUrl = getAvatarUrl(account, ContactId(ContactType.FRIEND, account)),
                age = profile.age,
                email = profile.email,
                qLevel = profile.level,
                sign = profile.sign,
                sex = if (profile.sex == "FEMALE") 1 else 0 // TODO
            )
        }
    }

    override fun attachContactSyncer(bridge: ContactSyncBridge): DisposableBridge {
        contactSyncer = bridge
        return DisposableBridge {
            contactSyncer = null
        }
    }

    override fun subscribeMessages(bridge: MessageSubscriber): DisposableBridge {
        messageSubscriber = bridge

        launch {
            var message = messageCacheQueue.poll()
            while (message != null) {
                bridge.onMessage(message)
                message = messageCacheQueue.poll()
            }
        }

        return DisposableBridge {
            contactSyncer = null
        }
    }

    private fun addBot(account: AccountLoginData): Boolean {
        if (session[account.accountNo] != null) {
            removeBot(account.accountNo)
        }
        session[account.accountNo] = BotSession(this, client, account.accountNo)

        if (!stateChannel.trySend(
                AccountState.Offline(account.accountNo, OfflineCause.INIT, null)
            ).isSuccess) {
            logger.w("Failed to send add bot state.")
        }

        return true
    }

    override fun login(accountNo: Long): Boolean {
        val currSession = session[accountNo] ?: return false
        if (lastState[accountNo] is AccountState.Online) return true

        if (eventJob[accountNo]?.isCancelled == true) {
            logger.w("session $accountNo is cancelled, renewing session instance.")
            session[accountNo] = BotSession(this, client, accountNo)
        }

        val job = session[accountNo]
        if (job != null && job.active) {
            logger.e("session $accountNo is active.")
            return true
        }

        val syncContactLock = Mutex(true)

        eventJob[accountNo] = launch(context = CoroutineExceptionHandler { _, th ->
            eventJob.remove(accountNo)

            logger.e("uncaught exception while logging $accountNo.", th)
            stateChannel.trySend(AccountState.Offline(accountNo, OfflineCause.LOGIN_FAILED, th.unwrapMessage()))
        }) {
            currSession.start()

            when(val verifyResult = currSession.awaitConnection()) { // throws exception if timeout
                0 -> {
                    logger.i("bot $accountNo is connected.")
                    stateChannel.send(AccountState.Online(accountNo))
                }
                1 -> throw Exception("wrong verify key")
                2 -> throw NullPointerException("bot $accountNo is not exist")
                3, 4 -> throw IllegalStateException("session is expired or invalid.")
                else -> error("unknown verify result code: $verifyResult")
            }

            launch sync@ {
                logger.i("syncing contacts of bot $accountNo to database.")

                val syncer = contactSyncer
                if (syncer == null) {
                    logger.w("contact syncer is null")
                    return@sync
                }

                val friendList = currSession.sendAndExpect<FriendList>("friendList").data.map {
                    val contact = ContactId(ContactType.FRIEND, it.id)
                    ContactInfo(contact, it.nickname, getAvatarUrl(accountNo, contact))
                }
                val groupList = currSession.sendAndExpect<GroupList>("groupList").data.map {
                    val contact = ContactId(ContactType.GROUP, it.id)
                    ContactInfo(contact, it.name, getAvatarUrl(accountNo, contact))
                }
                val accountInfo = queryAccountInfo(accountNo)

                tryOrMarkAsDead(
                    block = {
                        syncer.onSyncContact(ContactSyncOp.REFRESH, accountNo, buildList {
                            addAll(friendList)
                            addAll(groupList)
                        })
                        if (accountInfo != null) syncer.onUpdateAccountInfo(accountInfo)
                    },
                    onDied = { logger.w("contact syncer binder has died.") }
                )


                logger.i("sync contact of bot $accountNo complete.")
                syncContactLock.unlock()
            }

            currSession.eventFlow().cancellable().catch { t ->
                if (t is IllegalStateException &&
                    t.toString().contains(Regex("UNKNOWN_MESSAGE_EVENT_TYPE"))
                ) {
                    val type = t.toString().substringAfter(':').trim()
                    logger.w("Unknown message type while listening $accountNo: $type")
                    return@catch
                }
                logger.e("Error while subscribing message of $accountNo", t)
            }.subscribeEvents(currSession, syncContactLock)
        }
        return true
    }

    private suspend fun Flow<EventDTO>.subscribeEvents(session: BotSession, syncContactLock: Mutex) = collect { event ->
        val accountNo = session.accountNo
        when(event) {
            // state event
            is BotOnlineEventDTO -> {
                logger.i("bot ${event.qq} login success.")
                stateChannel.send(AccountState.Online(event.qq))
            }
            is BotOfflineEventActiveDTO ->
                stateChannel.send(AccountState.Offline(event.qq, OfflineCause.SUBJECTIVE, "offline manually"))
            is BotOfflineEventDroppedDTO ->
                stateChannel.send(AccountState.Offline(event.qq, OfflineCause.DISCONNECTED, "dropped"))
            is BotOfflineEventForceDTO ->
                stateChannel.send(AccountState.Offline(event.qq, OfflineCause.FORCE, event.message))

            // message event
            is FriendMessagePacketDTO, is FriendSyncMessagePacketDTO,
            is GroupMessagePacketDTO, is GroupSyncMessagePacketDTO,
            is TempMessagePacketDTO, is TempSyncMessagePacketDTO -> {
                val contact = ContactId(
                    when(event) {
                        is FriendMessagePacketDTO, is FriendSyncMessagePacketDTO -> ContactType.FRIEND
                        is GroupMessagePacketDTO, is GroupSyncMessagePacketDTO -> ContactType.GROUP
                        else -> ContactType.TEMP
                    },
                    when(event) {
                        is FriendMessagePacketDTO -> event.sender.id
                        is FriendSyncMessagePacketDTO -> event.subject.id
                        is GroupMessagePacketDTO -> event.sender.group.id
                        is GroupSyncMessagePacketDTO -> event.subject.id
                        is TempMessagePacketDTO -> event.sender.group.id
                        is TempSyncMessagePacketDTO -> event.subject.id
                        else -> error("UNKNOWN_MESSAGE_EVENT_TYPE: $event")
                    }
                )
                this@ArukuMiraiApiHttpService.launch message@ {
                    val accountNickName = queryAccountInfo(accountNo)?.nickname ?: ""
                    val source = (event as MessagePacketDTO).messageChain.find { it is MessageSourceDTO } as? MessageSourceDTO
                    if (source == null) {
                        logger.w("message chain of event $event doesn't have message source, ignoring...")
                        return@message
                    }

                    val senderId = when(event) {
                        is FriendMessagePacketDTO -> event.sender.id
                        is FriendSyncMessagePacketDTO -> accountNo
                        is GroupMessagePacketDTO -> event.sender.id
                        is GroupSyncMessagePacketDTO -> accountNo
                        is TempMessagePacketDTO -> event.sender.id
                        is TempSyncMessagePacketDTO -> accountNo
                        else -> error("UNKNOWN_MESSAGE_EVENT_TYPE: $event")
                    }

                    val message = MessageImpl(
                        account = accountNo,
                        contact = contact,
                        sender = senderId,
                        senderName = when(event) {
                            is FriendMessagePacketDTO -> event.sender.nickname
                            is FriendSyncMessagePacketDTO -> accountNickName
                            is GroupMessagePacketDTO -> event.sender.memberName
                            is GroupSyncMessagePacketDTO -> accountNickName
                            is TempMessagePacketDTO -> event.sender.memberName
                            is TempSyncMessagePacketDTO -> accountNickName
                            else -> error("UNKNOWN_MESSAGE_EVENT_TYPE: $event")
                        },
                        messageId = source.calculateMessageId(accountNo, senderId, contact.subject),
                        sequence = source.id.toLong(),
                        message = event.messageChain.toMessageElements(session, contact.subject),
                        time = source.time.toLong() * 1000 + (System.currentTimeMillis() % 1000)
                    )

                    val subscriber = messageSubscriber
                    if (subscriber == null) {
                        messageCacheQueue.offer(message)
                        return@message
                    }

                    tryOrMarkAsDead(
                        block = { subscriber.onMessage(message) },
                        onDied = { messageCacheQueue.offer(message) }
                    )
                }
            }

            // sync contact changes
            is BotJoinGroupEventDTO,
            is BotLeaveEventActiveDTO,
            is BotLeaveEventDisbandDTO,
            is BotLeaveEventKickDTO -> syncContactLock.withLock {
                tryOrMarkAsDead(
                    block = {
                        val group = when (event) {
                            is BotJoinGroupEventDTO -> event.group
                            is BotLeaveEventActiveDTO -> event.group
                            is BotLeaveEventDisbandDTO -> event.group
                            is BotLeaveEventKickDTO -> event.group
                            else -> error("event $event is not BotLeaveEvent")
                        }
                        val contact = ContactId(ContactType.GROUP, group.id)
                        contactSyncer?.onSyncContact(
                            if (event is BotJoinGroupEventDTO) ContactSyncOp.ENTRANCE else ContactSyncOp.REMOVE,
                            accountNo,
                            listOf(
                                ContactInfo(
                                    contact,
                                    group.name,
                                    getAvatarUrl(accountNo, contact)
                                )
                            )
                        )
                    },
                    onDied = { logger.w("contact syncer is died, $event is not processed.") }
                )
            }
            // TODO: friend add and friend delete event, mah currently not supported
        }
    }

    private fun closeBot(account: Long) {
        eventJob[account]?.cancel()
        session[account]?.close()
    }

    override fun logout(accountNo: Long): Boolean {
        session[accountNo] ?: return false
        if (lastState[accountNo] is AccountState.Offline) return true

        val job = eventJob[accountNo]
        if (job == null || !job.isActive) {
            logger.e("bot $accountNo is already offline.")
            return true
        }
        closeBot(accountNo)

        val rm = eventJob.remove(accountNo, job)
        if (!rm) logger.w("bot job $accountNo is not removed after logout.")
        return true
    }

    override fun removeBot(accountNo: Long): Boolean {
        val bot = session[accountNo] ?: return false
        closeBot(accountNo)

        session.remove(accountNo, bot)
        eventJob.remove(accountNo)
        return true
    }

    @OptIn(InternalSerializationApi::class)
    override fun queryFile(
        account: Long,
        contact: ContactId,
        fileId: String,
    ): RemoteFile? {
        val currSession = session[account] ?: return null
        val job = eventJob[account] ?: kotlin.run {
            logger.w("file query cannot find bot event job $account.")
            return null
        }
        if (contact.type != ContactType.GROUP) {
            logger.w("file query is not supported of $contact")
            return null
        }

        return runBlocking(job) {
            logger.i("querying file info $fileId of ${contact.subject}")
            val fileInfo = currSession.sendAndExpect<ElementResult>(
                "file_info",
                FileInfoDTO(id = fileId, target = contact.subject, group = contact.subject, withDownloadInfo = true)
            ).data.let { e -> json.decodeFromJsonElement(RemoteFileDTO::class.serializer(), e) }

            RemoteFile(
                id = fileInfo.id!!,
                url = fileInfo.downloadInfo?.url,
                name = fileInfo.name,
                md5 = fileInfo.md5?.toByteArray() ?: byteArrayOf(),
                extension = fileInfo.name.split('.').last(),
                size = fileInfo.size,
                expiryTime = fileInfo.uploadTime?.plus(7 * 24 * 60 * 60) ?: 0 // plus 7 days
            )
        }
    }

    private fun createRoamingQuerySession(
        account: Long,
        contact: ContactId
    ): RoamingQueryBridge? {
        logger.w("roaming query is currently not supported by ArukuMiraiApiHttp.")
        return null
        /*if (contact.type != ContactType.GROUP) {
            logger.w("roaming query only support group now.")
            return null
        }
        val bot = bots[account] ?: kotlin.run {
            logger.w("roaming query cannot find bot $account.")
            return null
        }
        val group = bot.getGroup(contact.subject) ?: kotlin.run {
            logger.w("roaming query group ${contact.subject} of bot $account is not found. ")
            return null
        }

        val job = botJobs[account] ?: kotlin.run {
            logger.w("roaming query cannot find bot job $account.")
            return null
        }
        if (!job.isActive) {
            logger.w("roaming query bot job $account is completed or cancelled.")
            return null
        }

        return object : RoamingQueryBridge {
            private val roamingSession by lazy { group.roamingMessages }

            override fun getMessagesBefore(
                messageId: Long,
                count: Int,
                exclude: Boolean
            ): List<Message> {
                val seq = getSeqByMessageId(messageId)
                logger.d("fetching roaming messages, account=$account, group=$group, calculatedSeq=$seq, count=$count")

                return runBlocking(job) {
                    roamingSession
                        .getMessagesBefore(if (exclude) seq - 1 else seq)
                        .asFlow()
                        .cancellable()
                        .map<MessageChain, Message> { chain ->
                            MessageImpl(
                                account = account,
                                contact = contact,
                                sender = chain.source.fromId,
                                senderName = when (contact.type) {
                                    ContactType.GROUP -> getGroupMember(
                                        account,
                                        contact.subject,
                                        chain.source.fromId
                                    )
                                        ?.remarkOrNameCardOrNick ?: chain.source.fromId.toString()

                                    else -> chain.source.fromId.toString()
                                },
                                messageId = chain.source.calculateMessageId(),
                                sequence = chain.source.ids.first().toLong(),
                                time = chain.source.time.toLong() * 1000,
                                message = chain.toMessageElements(group)
                            )
                        }
                        .catch {
                            logger.w("roaming query cannot process current: $it")
                            emit(MessageImpl.ROAMING_INVALID)
                        }
                        .take(count)
                        .toList()
                }
            }

            override fun getLastMessageId(): Long? {
                return runBlocking(job) {
                    runCatching {
                        val msg = roamingSession
                            .getMessagesBefore()
                            .asFlow()
                            .cancellable()
                            .take(1)
                            .toList()

                        if (msg.isEmpty()) {
                            logger.w("roaming getLastMessageId of contact $contact is empty.")
                        }

                        return@runCatching msg
                            .firstOrNull()
                            ?.sourceOrNull
                            ?.calculateMessageId()
                    }.onFailure {
                        logger.w("cannot query last message source of $group", it)
                    }.getOrNull()
                }
            }

        }*/
    }

    @OptIn(InternalSerializationApi::class)
    suspend inline fun <reified R : DTO> BotSession.sendAndExpect(
        command: String,
        data: DTO? = null,
        subCommand: String? = null,
        timeout: Long = 5000L
    ): R {
        awaitConnection()

        val syncId = Random.nextInt()
        send(command, subCommand, syncId, data)

        return withTimeout(timeout) {
            commandFlow()
                .first { it.syncId.toIntOrNull() == syncId }
                .run { json.decodeFromJsonElement(R::class.serializer(), this.data) }
        }
    }


    // dispatch a state to binder bridge
    private fun BotStateObserver.dispatchState(state: AccountState) {
        lastState[state.account] = state
        if (state is AccountState.Offline && state.cause == OfflineCause.REMOVE_BOT) {
            lastState.remove(state.account)
        }

        onDispatch(state)
    }

    override fun onFrontendDisconnected() {
        stateObserver = null
        contactSyncer = null
        messageSubscriber = null
    }

    private fun Throwable.unwrapMessage(): String? {
        if (message != null) return message
        if (cause != null) return cause!!.unwrapMessage()
        if (suppressed.isNotEmpty()) return suppressed.asSequence()
            .map { it.unwrapMessage() }
            .first()
        return null
    }
}