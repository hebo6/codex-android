package com.hebo.codex.domain

import android.util.Log
import com.hebo.codex.data.local.ServerSettings
import com.hebo.codex.data.local.ServerStore
import com.hebo.codex.data.remote.AppServerApi
import com.hebo.codex.data.remote.AppServerClientFactory
import com.hebo.codex.data.remote.AppServerConnection
import com.hebo.codex.data.remote.ProtocolMapper
import com.hebo.codex.data.remote.RuntimeStatusMapper
import com.hebo.codex.data.remote.ProtocolException
import com.hebo.codex.data.remote.ResolvedProxyConnection
import com.hebo.codex.data.remote.ServerEvent
import com.hebo.codex.data.remote.SshHostKeyVerificationException
import com.hebo.codex.data.remote.StableFeatureApi
import com.hebo.codex.data.remote.StableRpcMethod
import com.hebo.codex.data.remote.ThreadSnapshot
import com.hebo.codex.data.remote.optionalString
import com.hebo.codex.data.remote.requiredArray
import com.hebo.codex.data.remote.requiredObject
import com.hebo.codex.data.remote.requiredString
import com.hebo.codex.util.runSuspendCatching
import com.hebo.codex.util.toUserFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

private sealed interface BackgroundTerminalChange {
    data class Upsert(val terminal: BackgroundTerminal) : BackgroundTerminalChange
    data class Remove(val itemId: String? = null, val processId: String? = null) :
        BackgroundTerminalChange
}

class CodexRepository(
    private val serverStore: ServerStore,
    private val clientFactory: AppServerClientFactory,
    private val applicationScope: CoroutineScope,
    private val notifyConversation: (ConversationNotification) -> Unit = {},
) {
    private val conversationMutex = Mutex()
    private val conversations = ConcurrentHashMap<ConversationKey, MutableStateFlow<ConversationState>>()
    private val conversationLeases = ConversationLeaseRegistry<ConversationKey>()
    private val pendingResumeGoalSnapshots = ConcurrentHashMap.newKeySet<ConversationKey>()
    private val pendingItemUpdates = ConcurrentHashMap<ItemRoute, MutableList<PendingItemUpdate>>()
    private val pendingRequestRoutes = ConcurrentHashMap<String, PendingRequestRoute>()
    private val asyncQuestionNotifications = AsyncQuestionNotifications()
    private val conversationEventBuffers = ConcurrentHashMap<ConversationKey, ConversationEventBuffer>()
    private val backgroundTerminalRefreshBuffers =
        ConcurrentHashMap<ConversationKey, BackgroundTerminalRefreshBuffer>()
    private val historyPagingMutexes = ConcurrentHashMap<ConversationKey, Mutex>()
    private val subAgentMonitors = ConcurrentHashMap<ConversationKey, CopyOnWriteArraySet<SubAgentMonitor>>()
    private val runtimeQueueRefreshes = ConcurrentHashMap<ConversationKey, RuntimeQueueRefresh>()
    private val goalRevisions = ConcurrentHashMap<ConversationKey, AtomicLong>()
    private val mutableConnectionStatuses = MutableStateFlow<Map<String, ConnectionStatus>>(emptyMap())
    private val mutableThreadRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableFeatureRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableRateLimitRevisions = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutableServerNotices = MutableStateFlow<Map<String, List<ConversationNotice>>>(emptyMap())
    private val threadAttentionRevision = AtomicLong()
    private val mutableThreadAttention = MutableStateFlow<Map<String, Map<String, ThreadAttentionState>>>(emptyMap())
    private val connections = ConnectionCoordinator(
        scope = applicationScope,
        isAvailable = { managed ->
            managed.connection.status.value is ConnectionStatus.Connected
        },
        openConnection = ::establishConnection,
        closeConnection = ::closeManagedConnection,
        onConnecting = { serverId ->
            clearConnectionState(serverId)
            mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Connecting)
        },
        onConnected = { serverId, managed ->
            managed.observers = observeConnection(serverId, managed)
            mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Connected)
        },
        onFailed = { serverId, error ->
            mutableConnectionStatuses.updateValue(
                serverId,
                ConnectionStatus.Failed(error.toUserFacingError("连接服务器失败")),
            )
        },
        onCancelled = { serverId ->
            mutableConnectionStatuses.updateValue(serverId, ConnectionStatus.Disconnected)
        },
    )

    val serverSettings: StateFlow<ServerSettings> = serverStore.settings.stateIn(
        scope = applicationScope,
        started = SharingStarted.Eagerly,
        initialValue = ServerSettings(servers = emptyList(), proxies = emptyList(), activeServerId = null),
    )
    val servers: StateFlow<List<ServerProfile>> = serverSettings
        .map { it.servers }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())
    val proxies: StateFlow<List<ProxyProfile>> = serverSettings
        .map { it.proxies }
        .stateIn(applicationScope, SharingStarted.Eagerly, emptyList())
    val activeServerId: StateFlow<String?> = serverSettings
        .map { it.activeServerId }
        .stateIn(applicationScope, SharingStarted.Eagerly, null)
    val connectionStatuses: StateFlow<Map<String, ConnectionStatus>> = mutableConnectionStatuses
    val threadRevisions: StateFlow<Map<String, Long>> = mutableThreadRevisions
    val featureRevisions: StateFlow<Map<String, Long>> = mutableFeatureRevisions
    val rateLimitRevisions: StateFlow<Map<String, Long>> = mutableRateLimitRevisions
    val serverNotices: StateFlow<Map<String, List<ConversationNotice>>> = mutableServerNotices
    val threadAttention: StateFlow<Map<String, Map<String, ThreadAttentionState>>> = mutableThreadAttention

    suspend fun addServer(
        name: String,
        endpoint: String,
        token: String,
        proxyId: String? = null,
    ): ServerProfile {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "请输入服务器名称" }
        require(serverStore.settings.first().servers.none { it.name == normalizedName }) { "服务器名称已存在" }
        val normalizedEndpoint = validateWebSocketEndpoint(endpoint)
        val normalizedToken = validateCapabilityToken(token)
        require(proxyId == null || serverStore.settings.first().proxies.any { it.id == proxyId }) { "代理不存在" }

        val profile = ServerProfile(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            webSocketUrl = normalizedEndpoint,
            authentication = ServerAuthentication.CAPABILITY_TOKEN,
            proxyId = proxyId,
        )
        serverStore.addServer(profile, normalizedToken)
        serverSettings.first { settings -> settings.servers.any { it.id == profile.id } }
        applicationScope.launch {
            runSuspendCatching { connect(profile.id) }
        }
        return profile
    }

    suspend fun serverConnectionDetails(serverId: String): ServerConnectionDetails = ServerConnectionDetails(
        profile = awaitServer(serverId),
        token = serverStore.readToken(serverId),
    )

    suspend fun updateServer(
        serverId: String,
        name: String,
        endpoint: String,
        token: String,
        proxyId: String? = null,
    ): ServerProfile {
        val settings = serverStore.settings.first()
        val current = settings.servers.singleOrNull { it.id == serverId } ?: error("服务器不存在")
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "请输入服务器名称" }
        require(settings.servers.none { it.id != serverId && it.name == normalizedName }) { "服务器名称已存在" }
        val normalizedEndpoint = validateWebSocketEndpoint(endpoint)
        val normalizedToken = validateCapabilityToken(token)
        require(proxyId == null || settings.proxies.any { it.id == proxyId }) { "代理不存在" }

        val profile = current.copy(name = normalizedName, webSocketUrl = normalizedEndpoint, proxyId = proxyId)
        serverStore.updateServer(profile, normalizedToken)
        serverSettings.first { stored -> stored.servers.singleOrNull { it.id == serverId } == profile }

        connections.close(serverId)
        clearConnectionState(serverId)
        conversations.entries
            .filter { it.key.serverId == serverId }
            .forEach { (_, conversation) ->
                conversation.updateState { state -> state.copy(serverName = profile.name) }
            }
        applicationScope.launch {
            runSuspendCatching { connect(serverId) }
                .onSuccess { bumpThreadRevision(serverId) }
        }
        return profile
    }

    suspend fun testServerConnection(endpoint: String, token: String, proxyId: String? = null) {
        val normalizedEndpoint = validateWebSocketEndpoint(endpoint)
        val normalizedToken = validateCapabilityToken(token)

        val connection = clientFactory.create(
            endpoint = normalizedEndpoint,
            token = normalizedToken,
            proxy = resolveProxy(proxyId),
            parentContext = applicationScope.coroutineContext,
        )
        try {
            connection.connect()
            connection.close()
        } catch (error: CancellationException) {
            connection.abort()
            throw error
        } catch (error: Throwable) {
            connection.abort()
            throw error
        }
    }

    suspend fun selectServer(serverId: String) {
        val previousServerId = serverStore.settings.first().activeServerId
        serverStore.selectServer(serverId)
        if (previousServerId != null && previousServerId != serverId) {
            connections.cancelAttempt(previousServerId)
        }
        connect(serverId)
    }

    suspend fun removeServer(serverId: String) {
        connections.close(serverId)
        clearConnectionState(serverId)
        conversations.keys.filter { it.serverId == serverId }.forEach { conversations.remove(it) }
        pendingItemUpdates.keys.removeIf { it.serverId == serverId }
        conversationEventBuffers.keys.removeIf { it.serverId == serverId }
        backgroundTerminalRefreshBuffers.keys.removeIf { it.serverId == serverId }
        historyPagingMutexes.keys.removeIf { it.serverId == serverId }
        serverStore.removeServer(serverId)
    }

    suspend fun addProxy(
        name: String,
        configuration: ProxyConfiguration,
        credential: String?,
    ): ProxyProfile {
        val settings = serverStore.settings.first()
        val normalizedName = validateProxyName(name)
        require(settings.proxies.none { it.name.equals(normalizedName, ignoreCase = true) }) { "代理名称已存在" }
        val normalizedConfiguration = validateProxyConfiguration(configuration)
        val normalizedCredential = validateProxyCredential(normalizedConfiguration, credential, false)
        val profile = ProxyProfile(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            version = 1,
            configuration = normalizedConfiguration,
            credentialConfigured = normalizedCredential != null,
        )
        serverStore.addProxy(profile, normalizedCredential)
        return serverSettings.first { stored -> stored.proxies.any { it.id == profile.id } }
            .proxies.single { it.id == profile.id }
    }

    suspend fun updateProxy(
        proxyId: String,
        name: String,
        configuration: ProxyConfiguration,
        credential: String?,
        clearCredential: Boolean = false,
    ): ProxyProfile {
        val settings = serverStore.settings.first()
        val current = settings.proxies.singleOrNull { it.id == proxyId } ?: error("代理不存在")
        val normalizedName = validateProxyName(name)
        require(settings.proxies.none {
            it.id != proxyId && it.name.equals(normalizedName, ignoreCase = true)
        }) { "代理名称已存在" }
        val normalizedConfiguration = validateProxyConfiguration(configuration)
        val normalizedCredential = validateProxyCredential(
            configuration = normalizedConfiguration,
            credential = credential,
            existingCredentialCanBeReused = !clearCredential &&
                current.configuration.hasSameCredentialPurpose(normalizedConfiguration) &&
                current.credentialConfigured,
        )
        val retainedHostKey = current.sshHostKey?.takeIf {
            normalizedConfiguration is ProxyConfiguration.Ssh &&
                it.host == normalizedConfiguration.host &&
                it.port == normalizedConfiguration.port
        }
        val nextVersion = current.version + 1
        serverStore.updateProxy(
            current.copy(
                name = normalizedName,
                version = nextVersion,
                configuration = normalizedConfiguration,
                credentialConfigured = normalizedCredential != null ||
                    (!clearCredential && current.credentialConfigured &&
                        current.configuration.hasSameCredentialPurpose(normalizedConfiguration)),
                sshHostKey = retainedHostKey,
                lastTest = null,
            ),
            normalizedCredential,
            clearCredential,
        )
        reconnectProxyServers(proxyId)
        return serverSettings.first { stored ->
            stored.proxies.singleOrNull { it.id == proxyId }?.version == nextVersion
        }.proxies.single { it.id == proxyId }
    }

    suspend fun removeProxy(proxyId: String) {
        serverStore.removeProxy(proxyId)
    }

    suspend fun testProxy(proxyId: String, serverId: String): ProxyTestResult {
        val settings = serverStore.settings.first()
        val server = settings.servers.singleOrNull { it.id == serverId } ?: error("服务器不存在")
        require(settings.proxies.any { it.id == proxyId }) { "代理不存在" }
        val connection = clientFactory.create(
            endpoint = server.webSocketUrl,
            token = serverStore.readToken(serverId),
            proxy = resolveProxy(proxyId),
            parentContext = applicationScope.coroutineContext,
        )
        return try {
            connection.connect()
            connection.close()
            serverStore.recordProxyTest(proxyId, true)
            ProxyTestResult.Succeeded
        } catch (error: CancellationException) {
            connection.abort()
            throw error
        } catch (error: Throwable) {
            connection.abort()
            when (val hostKeyError = error.findSshHostKeyError()) {
                is SshHostKeyVerificationException.Unknown -> {
                    return ProxyTestResult.HostKeyConfirmationRequired(hostKeyError.observed, null)
                }
                is SshHostKeyVerificationException.Changed -> {
                    return ProxyTestResult.HostKeyConfirmationRequired(hostKeyError.observed, hostKeyError.previous)
                }
                null -> Unit
            }
            serverStore.recordProxyTest(proxyId, false)
            throw error
        }
    }

    suspend fun confirmSshHostKey(proxyId: String, observed: SshHostKeyRecord) {
        val proxy = serverStore.settings.first().proxies.singleOrNull { it.id == proxyId } ?: error("代理不存在")
        val configuration = proxy.configuration as? ProxyConfiguration.Ssh ?: error("当前代理不是 SSH 代理")
        require(observed.host == configuration.host && observed.port == configuration.port) { "SSH 主机密钥与代理地址不匹配" }
        serverStore.confirmSshHostKey(
            proxyId,
            observed.copy(confirmedAtMillis = System.currentTimeMillis()),
        )
        reconnectProxyServers(proxyId)
    }

    suspend fun removeSshHostKey(proxyId: String) {
        serverStore.removeSshHostKey(proxyId)
        reconnectProxyServers(proxyId)
    }

    suspend fun connect(serverId: String) {
        api(serverId, retryFailed = true)
    }

    suspend fun threadPage(
        serverId: String,
        cursor: String? = null,
        cwd: String? = null,
        archived: Boolean = false,
        searchTerm: String? = null,
        sortKey: ThreadSortKey = ThreadSortKey.RECENCY_AT,
        sortDirection: SortDirection = SortDirection.DESC,
        modelProviders: List<String> = emptyList(),
        sourceKinds: Set<ThreadSourceKind> = emptySet(),
    ): ThreadPage {
        val response = api(serverId).listThreads(
            cursor = cursor,
            cwd = cwd,
            archived = archived,
            searchTerm = searchTerm,
            sortKey = sortKey,
            sortDirection = sortDirection,
            modelProviders = modelProviders,
            sourceKinds = sourceKinds,
        )
        return ThreadPage(
            data = response.requiredArray("data").map { ProtocolMapper.threadSummary(it.jsonObject) },
            nextCursor = response.optionalString("nextCursor"),
        )
    }

    suspend fun startConversation(
        serverId: String,
        cwd: String,
        options: ThreadOptions = ThreadOptions(),
    ): String {
        val normalizedCwd = cwd.trim()
        require(normalizedCwd.isNotEmpty()) { "请输入服务端项目路径" }
        val snapshot = ProtocolMapper.threadMetadata(api(serverId).startThread(normalizedCwd, options))
        val profile = awaitServer(serverId)
        val state = snapshot.toConversationState(profile)
        conversations[ConversationKey(serverId, snapshot.summary.id)] = MutableStateFlow(state)
        return snapshot.summary.id
    }

    fun observeSubAgents(serverId: String, threadId: String): Flow<SubAgentsState> = channelFlow {
        val key = ConversationKey(serverId, threadId)
        val monitor = SubAgentMonitor(this, threadId, object : SubAgentSource {
            override suspend fun listDescendants(): List<ThreadSummary> {
                val result = mutableListOf<ThreadSummary>()
                val cursors = mutableSetOf<String>()
                var cursor: String? = null
                do {
                    val page = api(serverId).listThreads(
                        cursor = cursor,
                        ancestorThreadId = threadId,
                        sourceKinds = setOf(ThreadSourceKind.SUB_AGENT_THREAD_SPAWN),
                        sortKey = ThreadSortKey.CREATED_AT,
                        sortDirection = SortDirection.ASC,
                    )
                    result += page.requiredArray("data").map { ProtocolMapper.threadSummary(it.jsonObject) }
                    cursor = page.optionalString("nextCursor")
                    check(cursor == null || cursors.add(cursor)) { "子 Agent 列表返回了重复的游标" }
                } while (cursor != null)
                return result
            }

            override suspend fun latestTurnStatus(threadId: String): ConversationTurnStatus? =
                ProtocolMapper.threadTurnPage(api(serverId).listThreadTurns(threadId, limit = 1))
                    .data.singleOrNull()?.status
        })
        subAgentMonitors.compute(key) { _, monitors ->
            (monitors ?: CopyOnWriteArraySet()).also { it.add(monitor) }
        }
        try {
            launch {
                connectionStatuses.map { it[serverId] is ConnectionStatus.Connected }
                    .distinctUntilChanged().collect(monitor::setConnected)
            }
            monitor.state.collect { send(it) }
        } finally {
            subAgentMonitors.computeIfPresent(key) { _, monitors ->
                monitors.apply { remove(monitor) }.takeIf { it.isNotEmpty() }
            }
            monitor.close()
        }
    }

    fun refreshSubAgents(serverId: String, threadId: String) {
        subAgentMonitors[ConversationKey(serverId, threadId)]?.forEach(SubAgentMonitor::refresh)
    }

    fun refreshRuntimeQueue(serverId: String, threadId: String) {
        requestRuntimeQueueRefresh(ConversationKey(serverId, threadId))
    }

    suspend fun openConversation(serverId: String, threadId: String): StateFlow<ConversationState> {
        val key = ConversationKey(serverId, threadId)
        val conversation = conversationMutex.withLock {
            conversationLeases.acquire(key)
            try {
                val existing = conversations[key]
                if (existing != null) {
                    refreshConversation(key, existing)
                    existing
                } else {
                    val profile = awaitServer(serverId)
                    val created = MutableStateFlow(
                        ConversationState(
                            serverId = profile.id,
                            serverName = profile.name,
                            threadId = threadId,
                            title = "正在加载会话",
                            cwd = "",
                        ),
                    )
                    conversations[key] = created
                    try {
                        refreshConversation(key, created, failOnError = true)
                        created
                    } catch (error: Throwable) {
                        conversations.remove(key, created)
                        throw error
                    }
                }
            } catch (error: Throwable) {
                conversationLeases.release(key)
                throw error
            }
        }
        markThreadViewed(serverId, threadId)
        requestRuntimeQueueRefresh(key)
        return conversation
    }

    suspend fun refreshConversation(serverId: String, threadId: String) {
        conversationMutex.withLock {
            val key = ConversationKey(serverId, threadId)
            refreshConversation(key, requireConversation(serverId, threadId))
        }
        requestRuntimeQueueRefresh(ConversationKey(serverId, threadId))
    }

    internal suspend fun syncExternalConversation(serverId: String, threadId: String) {
        val key = ConversationKey(serverId, threadId)
        conversationMutex.withLock {
            val state = requireConversation(serverId, threadId)
            // Manual refresh/reconnect may have already restored the live stream.
            if (!state.value.isExternalWriter) return
            refreshConversation(key, state, failOnError = true, reportErrors = false)
        }
        requestRuntimeQueueRefresh(key)
    }

    suspend fun loadOlderConversationTurns(serverId: String, threadId: String): Boolean {
        val key = ConversationKey(serverId, threadId)
        val state = requireConversation(serverId, threadId)
        val pagingMutex = historyPagingMutexes.computeIfAbsent(key) { Mutex() }
        return pagingMutex.withLock {
            val cursor = state.value.history.olderTurnsCursor ?: return@withLock false
            state.updateState { current ->
                current.copy(
                    history = current.history.copy(
                        isLoadingOlderTurns = true,
                        olderTurnsError = null,
                    ),
                )
            }
            try {
                val api = api(serverId)
                val page = ProtocolMapper.threadTurnPage(api.listThreadTurns(threadId, cursor))
                if (page.nextCursor != null && page.nextCursor == cursor) {
                    throw ProtocolException("thread/turns/list 返回了未推进的游标")
                }
                val olderPageTurns = page.data.asReversed()
                var inserted = false
                state.updateState { current ->
                    if (current.history.olderTurnsCursor != cursor) return@updateState current
                    val loadedTurnIds = current.history.turns.mapTo(mutableSetOf(), ConversationTurn::id)
                    val olderTurns = olderPageTurns.filter { it.id !in loadedTurnIds }
                    inserted = olderTurns.isNotEmpty()
                    current.copy(
                        history = current.history.copy(
                            turns = olderTurns + current.history.turns,
                            olderTurnsCursor = page.nextCursor,
                            isLoadingOlderTurns = false,
                            olderTurnsError = null,
                        ),
                    )
                }
                inserted
            } catch (error: CancellationException) {
                state.updateState { current ->
                    current.copy(
                        history = current.history.copy(isLoadingOlderTurns = false),
                    )
                }
                throw error
            } catch (error: Throwable) {
                state.updateState { current ->
                    if (current.history.olderTurnsCursor != cursor) {
                        current
                    } else {
                        current.copy(
                            history = current.history.copy(
                                isLoadingOlderTurns = false,
                                olderTurnsError = error.toUserFacingError("无法加载更早内容"),
                            ),
                        )
                    }
                }
                false
            }
        }
    }

    suspend fun sendMessage(
        serverId: String,
        threadId: String,
        input: List<UserInputPart>,
        options: TurnOptions = TurnOptions(),
    ) {
        require(input.isNotEmpty()) { "消息内容不能为空" }
        val text = input.filterIsInstance<UserInputPart.Text>().joinToString("\n") { it.text }.trim()
        require(text.isNotEmpty() || input.any { it !is UserInputPart.Text }) { "消息内容不能为空" }
        val state = requireConversation(serverId, threadId)
        state.value.requireWritable()
        val targetApi = writableApi(serverId, threadId)
        val activeTurnId = state.value.activeTurnId
        val clientId = UUID.randomUUID().toString()
        val messageTitle = input.conversationTitle()
        state.updateState { current ->
            current.copy(
                title = if (current.title == "新会话") messageTitle.take(72) else current.title,
                isRunning = true,
                errorMessage = null,
            )
        }
        setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)

        try {
            val result = if (activeTurnId == null) {
                targetApi.startTurn(threadId, clientId, input, options)
            } else {
                targetApi.steerTurn(threadId, activeTurnId, clientId, input)
            }
            val turnId = if (activeTurnId == null) {
                result.requiredObject("turn").requiredString("id")
            } else {
                result.requiredString("turnId")
            }
            state.updateState { current ->
                current.reduceConversationEvent(ConversationEvent.TurnStarted(turnId))
            }
        } catch (error: Throwable) {
            state.updateState { current ->
                current.copy(
                    isRunning = false,
                    errorMessage = error.toUserFacingError("发送消息失败"),
                )
            }
            setThreadAttention(serverId, threadId, ThreadAttention.FAILED)
            throw error
        }
    }

    suspend fun sendDesktopMessage(serverId: String, threadId: String, text: String) {
        require(text.isNotBlank()) { "消息内容不能为空" }
        val state = requireConversation(serverId, threadId)
        check(state.value.canSendToDesktop) { state.value.viewOnlyMessage ?: "电脑端连接已失效，请刷新会话" }
        val targetApi = api(serverId)
        // Reconnection may restore local ownership or lose the desktop owner.
        check(state.value.canSendToDesktop) { "会话连接已变化，请刷新后重试" }
        check(!state.value.isRunning) { "电脑正在处理此会话，请完成后再发送" }
        val result = targetApi.sendDesktopMessage(threadId, UUID.randomUUID().toString(), text)
        state.updateState { current ->
            current.reduceConversationEvent(
                ConversationEvent.TurnStarted(result.requiredObject("turn").requiredString("id")),
            )
        }
        // History is read from the original thread; the companion owns no writer.
        // Do not retry a failed delivery or clear drafts on an unconfirmed result.
    }

    suspend fun queueInput(serverId: String, threadId: String, input: List<UserInputPart>) {
        require(input.isNotEmpty()) { "消息内容不能为空" }
        val conversation = requireConversation(serverId, threadId).value
        conversation.requireWritable()
        try {
            writableApi(serverId, threadId).queueInput(threadId, UUID.randomUUID().toString(), input)
        } finally {
            requestRuntimeQueueRefresh(ConversationKey(serverId, threadId))
        }
    }

    suspend fun deleteQueuedInput(serverId: String, threadId: String, entryId: String): Boolean {
        val state = requireConversation(serverId, threadId)
        state.value.requireWritable()
        try {
            val deleted = writableApi(serverId, threadId).deleteQueuedInput(threadId, entryId)
            if (deleted) state.updateState { current ->
                current.copy(runtimeStatus = current.runtimeStatus.copy(
                    queue = current.runtimeStatus.queue?.let { queue ->
                        queue.copy(entries = queue.entries.filterNot { it.id == entryId })
                    },
                ))
            }
            return deleted
        } finally {
            requestRuntimeQueueRefresh(ConversationKey(serverId, threadId))
        }
    }

    suspend fun sendMessage(serverId: String, threadId: String, message: String) =
        sendMessage(serverId, threadId, listOf(UserInputPart.Text(message)))

    suspend fun runShellCommand(serverId: String, threadId: String, command: String) {
        val normalized = command.trim()
        require(normalized.isNotEmpty()) { "Shell 命令不能为空" }
        val state = requireConversation(serverId, threadId)
        state.value.requireWritable()
        val targetApi = writableApi(serverId, threadId)
        require(!state.value.isRunning) { "当前任务结束后才能执行 Shell 命令" }
        state.updateState { current -> current.copy(isRunning = true, errorMessage = null) }
        setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)
        try {
            targetApi.runShellCommand(threadId, normalized)
        } catch (error: Throwable) {
            state.updateState { current ->
                if (current.activeTurnId == null) {
                    current.copy(
                        isRunning = false,
                        errorMessage = error.toUserFacingError("执行 Shell 命令失败"),
                    )
                } else {
                    current
                }
            }
            setThreadAttention(serverId, threadId, ThreadAttention.FAILED)
            throw error
        }
    }

    suspend fun terminateBackgroundTerminal(serverId: String, threadId: String, processId: String) {
        requireConversation(serverId, threadId).value.requireWritable()
        require(processId.isNotBlank()) { "后台命令进程 ID 不能为空" }
        check(writableApi(serverId, threadId).terminateBackgroundTerminal(threadId, processId)) {
            "命令已经结束或无法终止"
        }
        val key = ConversationKey(serverId, threadId)
        applyBackgroundTerminalChange(key, BackgroundTerminalChange.Remove(processId = processId))
        releaseConversationIfUnused(key)
    }

    suspend fun interrupt(serverId: String, threadId: String) {
        val state = requireConversation(serverId, threadId)
        state.value.requireWritable()
        val targetApi = writableApi(serverId, threadId)
        val turnId = state.value.activeTurnId ?: return
        targetApi.interruptTurn(threadId, turnId)
    }

    suspend fun forkConversation(serverId: String, threadId: String, lastTurnId: String? = null): String {
        val snapshot = ProtocolMapper.threadMetadata(api(serverId).forkThread(threadId, lastTurnId))
        conversations[ConversationKey(serverId, snapshot.summary.id)] =
            MutableStateFlow(snapshot.toConversationState(awaitServer(serverId)))
        bumpThreadRevision(serverId)
        return snapshot.summary.id
    }

    suspend fun renameConversation(serverId: String, threadId: String, name: String) {
        checkConversationWritableIfOpen(serverId, threadId)
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "会话名称不能为空" }
        writableApi(serverId, threadId).request(
            StableRpcMethod.THREAD_NAME_SET,
            buildJsonObject {
                put("threadId", threadId)
                put("name", normalized)
            },
        )
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(title = normalized) }
        bumpThreadRevision(serverId)
    }

    suspend fun archiveConversation(serverId: String, threadId: String) {
        checkConversationWritableIfOpen(serverId, threadId)
        writableApi(serverId, threadId).request(
            StableRpcMethod.THREAD_ARCHIVE,
            buildJsonObject { put("threadId", threadId) },
        )
        val key = ConversationKey(serverId, threadId)
        conversations.remove(key)
        removePendingItemUpdates(key)
        conversationEventBuffers.remove(key)
        backgroundTerminalRefreshBuffers.remove(key)
        historyPagingMutexes.remove(key)
        bumpThreadRevision(serverId)
    }

    suspend fun unarchiveConversation(serverId: String, threadId: String) {
        checkConversationWritableIfOpen(serverId, threadId)
        writableApi(serverId, threadId).request(
            StableRpcMethod.THREAD_UNARCHIVE,
            buildJsonObject { put("threadId", threadId) },
        )
        bumpThreadRevision(serverId)
    }

    suspend fun deleteConversation(serverId: String, threadId: String) {
        checkConversationWritableIfOpen(serverId, threadId)
        writableApi(serverId, threadId).request(
            StableRpcMethod.THREAD_DELETE,
            buildJsonObject { put("threadId", threadId) },
        )
        val key = ConversationKey(serverId, threadId)
        conversations.remove(key)
        removePendingItemUpdates(key)
        conversationEventBuffers.remove(key)
        backgroundTerminalRefreshBuffers.remove(key)
        historyPagingMutexes.remove(key)
        removePendingRequests(serverId, threadId)
        bumpThreadRevision(serverId)
    }

    suspend fun compactConversation(serverId: String, threadId: String) {
        requireConversation(serverId, threadId).value.requireWritable()
        writableApi(serverId, threadId).request(
            StableRpcMethod.THREAD_COMPACT,
            buildJsonObject { put("threadId", threadId) },
        )
    }

    suspend fun getRemoteFileMetadata(serverId: String, path: String): RemoteFileMetadata =
        api(serverId).getFileMetadata(path)

    suspend fun readRemoteFilePreview(serverId: String, path: String): ByteArray =
        api(serverId).readFilePreview(path)

    suspend fun downloadRemoteFile(serverId: String, path: String, output: OutputStream): Long =
        api(serverId).downloadFile(path, output)

    suspend fun reviewConversation(
        serverId: String,
        threadId: String,
        target: ReviewTarget,
        delivery: ReviewDelivery,
    ): String {
        requireConversation(serverId, threadId).value.requireWritable()
        val response = writableApi(serverId, threadId).review(threadId, target, delivery)
        return response.requiredString("reviewThreadId")
    }

    suspend fun getGoal(serverId: String, threadId: String): ThreadGoal? {
        val targetApi = api(serverId)
        val key = ConversationKey(serverId, threadId)
        val revisions = goalRevisions.computeIfAbsent(key) { AtomicLong() }
        val revision = revisions.incrementAndGet()
        val response = targetApi.request(
            StableRpcMethod.THREAD_GOAL_GET,
            buildJsonObject { put("threadId", threadId) },
        ).jsonObject
        val goal = (response["goal"] as? JsonObject)?.let(ProtocolMapper::threadGoal)
        applyGoalResponse(key, revisions, revision, goal)
        return if (revisions.get() == revision) goal else conversations[key]?.value?.goal
    }

    suspend fun setGoal(
        serverId: String,
        threadId: String,
        objective: String,
        status: ThreadGoalStatus = ThreadGoalStatus.ACTIVE,
        tokenBudget: Long? = null,
    ): ThreadGoal {
        require(objective.isNotBlank()) { "目标不能为空" }
        requireConversation(serverId, threadId).value.requireWritable()
        val targetApi = writableApi(serverId, threadId)
        val key = ConversationKey(serverId, threadId)
        val revisions = goalRevisions.computeIfAbsent(key) { AtomicLong() }
        val revision = revisions.incrementAndGet()
        val response = targetApi.request(
            StableRpcMethod.THREAD_GOAL_SET,
            buildJsonObject {
                put("threadId", threadId)
                put("objective", objective.trim())
                put("status", status.wireValue)
                put("tokenBudget", tokenBudget?.let(::JsonPrimitive) ?: JsonNull)
            },
        ).jsonObject
        return ProtocolMapper.threadGoal(response.requiredObject("goal")).also { goal ->
            applyGoalResponse(key, revisions, revision, goal)
        }
    }

    suspend fun clearGoal(serverId: String, threadId: String) {
        requireConversation(serverId, threadId).value.requireWritable()
        val targetApi = writableApi(serverId, threadId)
        val key = ConversationKey(serverId, threadId)
        val revisions = goalRevisions.computeIfAbsent(key) { AtomicLong() }
        val revision = revisions.incrementAndGet()
        targetApi.request(
            StableRpcMethod.THREAD_GOAL_CLEAR,
            buildJsonObject { put("threadId", threadId) },
        )
        applyGoalResponse(key, revisions, revision, null)
    }

    private fun applyGoalResponse(key: ConversationKey, revisions: AtomicLong, revision: Long, goal: ThreadGoal?) {
        conversations[key]?.updateState { current ->
            if (revisions.get() != revision) current else current.copy(
                goal = goal,
                goalLoaded = true,
                runtimeStatus = current.runtimeStatus.reduce(RuntimeStatusEvent.GoalSynced),
            )
        }
    }

    fun releaseConversation(serverId: String, threadId: String) {
        val key = ConversationKey(serverId, threadId)
        applicationScope.launch {
            conversationMutex.withLock {
                conversationLeases.release(key)
                releaseConversationIfUnusedLocked(key)
            }
        }
    }

    private fun releaseConversationIfUnused(key: ConversationKey) {
        applicationScope.launch {
            conversationMutex.withLock {
                releaseConversationIfUnusedLocked(key)
            }
        }
    }

    private suspend fun releaseConversationIfUnusedLocked(key: ConversationKey) {
        if (conversationLeases.isHeld(key)) return
        val conversation = conversations[key] ?: return
        if (conversation.value.requiresRuntimeRetention) return
        if (!conversation.value.isExternalWriter) runSuspendCatching {
            api(key.serverId).request(
                StableRpcMethod.THREAD_UNSUBSCRIBE,
                buildJsonObject { put("threadId", key.threadId) },
            )
        }
        conversations.remove(key, conversation)
        pendingResumeGoalSnapshots.remove(key)
        removePendingItemUpdates(key)
        conversationEventBuffers.remove(key)
        backgroundTerminalRefreshBuffers.remove(key)
        historyPagingMutexes.remove(key)
        runtimeQueueRefreshes.remove(key)?.revision?.incrementAndGet()
        goalRevisions.remove(key)?.incrementAndGet()
        removePendingRequests(key.serverId, key.threadId)
    }

    fun clearConversationError(serverId: String, threadId: String) {
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(errorMessage = null) }
        if (mutableThreadAttention.value[serverId]?.get(threadId)?.attention == ThreadAttention.FAILED) {
            setThreadAttention(serverId, threadId, null)
        }
    }

    fun respondToApproval(
        serverId: String,
        threadId: String,
        requestKey: String,
        decision: ApprovalDecision,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject { put("decision", decision.wireValue) },
    )

    fun respondToApprovalWithExecPolicy(
        serverId: String,
        threadId: String,
        requestKey: String,
        amendment: JsonElement,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            put(
                "decision",
                buildJsonObject {
                    put(
                        "acceptWithExecpolicyAmendment",
                        buildJsonObject { put("execpolicy_amendment", amendment) },
                    )
                },
            )
        },
    )

    fun respondToApprovalWithNetworkPolicy(
        serverId: String,
        threadId: String,
        requestKey: String,
        amendment: JsonElement,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            put(
                "decision",
                buildJsonObject {
                    put(
                        "applyNetworkPolicyAmendment",
                        buildJsonObject { put("network_policy_amendment", amendment) },
                    )
                },
            )
        },
    )

    fun respondToQuestions(
        serverId: String,
        threadId: String,
        requestKey: String,
        answers: Map<String, List<String>>,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            put(
                "answers",
                buildJsonObject {
                    answers.forEach { (questionId, values) ->
                        put(
                            questionId,
                            buildJsonObject {
                                put("answers", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
                            },
                        )
                    }
                },
            )
        },
    )

    fun respondToMcpElicitation(
        serverId: String,
        threadId: String,
        requestKey: String,
        action: String,
        content: JsonElement? = null,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            put("action", action)
            put("content", content ?: JsonNull)
            put("_meta", JsonNull)
        },
    )

    fun respondToPermissions(
        serverId: String,
        threadId: String,
        requestKey: String,
        permissions: JsonObject,
        sessionScope: Boolean,
        strictAutoReview: Boolean,
    ) = respondToServerRequest(
        serverId,
        threadId,
        requestKey,
        buildJsonObject {
            require(!sessionScope || !strictAutoReview) {
                "严格自动审查只支持当前回合"
            }
            put("permissions", permissions)
            put("scope", if (sessionScope) "session" else "turn")
            if (strictAutoReview) put("strictAutoReview", true)
        },
    )

    suspend fun models(serverId: String): List<ModelOption> = StableFeatureApi(api(serverId)).models()

    suspend fun modelProviderCapabilities(serverId: String): FeatureActionResult =
        StableFeatureApi(api(serverId)).modelProviderCapabilities()

    suspend fun featureCatalog(
        serverId: String,
        area: FeatureArea,
        cwd: String? = null,
        threadId: String? = null,
        forceRefresh: Boolean = false,
    ): FeatureCatalog = StableFeatureApi(api(serverId)).catalog(area, cwd, threadId, forceRefresh)

    suspend fun executeFeatureAction(serverId: String, action: FeatureAction): FeatureActionResult =
        StableFeatureApi(api(serverId)).execute(action)

    suspend fun account(serverId: String): AccountOverview = StableFeatureApi(api(serverId)).account()

    suspend fun rateLimits(serverId: String): AccountRateLimits? = StableFeatureApi(api(serverId)).rateLimits()

    suspend fun accountUsage(serverId: String): AccountUsage? = StableFeatureApi(api(serverId)).accountUsage()

    suspend fun loginWithApiKey(serverId: String, apiKey: String): LoginChallenge =
        StableFeatureApi(api(serverId)).loginWithApiKey(apiKey)

    suspend fun loginWithChatGpt(serverId: String, deviceCode: Boolean): LoginChallenge =
        StableFeatureApi(api(serverId)).loginWithChatGpt(deviceCode)

    suspend fun cancelLogin(serverId: String, loginId: String) =
        StableFeatureApi(api(serverId)).cancelLogin(loginId)

    suspend fun logout(serverId: String) = StableFeatureApi(api(serverId)).logout()

    suspend fun consumeRateLimitReset(serverId: String, creditId: String?): FeatureActionResult =
        StableFeatureApi(api(serverId)).consumeRateLimitReset(creditId)

    suspend fun sendCreditsNudge(serverId: String, creditType: String): FeatureActionResult =
        StableFeatureApi(api(serverId)).sendCreditsNudge(creditType)

    suspend fun configuration(serverId: String, cwd: String?): ServerConfiguration =
        StableFeatureApi(api(serverId)).configuration(cwd)

    private suspend fun api(
        serverId: String,
        retryFailed: Boolean = false,
    ): AppServerApi = AppServerApi(connections.connection(serverId, retryFailed).connection)

    private suspend fun writableApi(serverId: String, threadId: String): AppServerApi {
        checkConversationWritableIfOpen(serverId, threadId)
        val targetApi = api(serverId)
        // Connecting can restore this conversation in external-writer mode.
        checkConversationWritableIfOpen(serverId, threadId)
        return targetApi
    }

    private suspend fun establishConnection(serverId: String): ManagedConnection {
        val server = awaitServer(serverId)
        val connection = clientFactory.create(
            endpoint = server.webSocketUrl,
            token = serverStore.readToken(serverId),
            proxy = resolveProxy(server.proxyId),
            parentContext = applicationScope.coroutineContext,
        )
        connection.diagnostics.serverId = serverId
        try {
            connection.connect()
            val api = AppServerApi(connection)
            restoreConversations(serverId, server, api, connection)
            check(connection.status.value is ConnectionStatus.Connected) { "服务器连接已中断" }
            return ManagedConnection(connection)
        } catch (error: CancellationException) {
            connection.abort()
            throw error
        } catch (error: Throwable) {
            connection.diagnostics.warning("connection_setup_failed", error)
            connection.abort()
            throw error
        }
    }

    private suspend fun resolveProxy(proxyId: String?): ResolvedProxyConnection? {
        if (proxyId == null) return null
        val profile = serverStore.settings.first().proxies.singleOrNull { it.id == proxyId }
            ?: error("服务器引用的代理不存在")
        return ResolvedProxyConnection(profile, serverStore.readProxyCredential(proxyId))
    }

    private suspend fun reconnectProxyServers(proxyId: String) {
        val settings = serverStore.settings.first()
        val referenced = settings.servers.filter { it.proxyId == proxyId }
        val reconnectIds = referenced.mapNotNull { server ->
            val status = mutableConnectionStatuses.value[server.id]
            server.id.takeIf {
                server.id == settings.activeServerId || status is ConnectionStatus.Connected || status is ConnectionStatus.Connecting
            }
        }
        referenced.forEach { server ->
            connections.close(server.id)
            clearConnectionState(server.id)
        }
        reconnectIds.forEach { serverId ->
            applicationScope.launch { runSuspendCatching { connect(serverId) } }
        }
    }

    private fun closeManagedConnection(managed: ManagedConnection) {
        managed.observers.forEach(Job::cancel)
        managed.connection.close()
    }

    private fun clearConnectionState(serverId: String) {
        asyncQuestionNotifications.clearServer(serverId)
        goalRevisions.filterKeys { it.serverId == serverId }.values.forEach { it.incrementAndGet() }
        pendingResumeGoalSnapshots.removeIf { it.serverId == serverId }
        clearServerPendingRequests(serverId)
        conversations.entries.filter { it.key.serverId == serverId }.forEach { (key, state) ->
            runtimeQueueRefreshes[key]?.revision?.incrementAndGet()
            state.updateState { it.copy(runtimeStatus = it.runtimeStatus.markStale()) }
        }
    }

    private suspend fun restoreConversations(
        serverId: String,
        server: ServerProfile,
        api: AppServerApi,
        connection: AppServerConnection,
    ) {
        val diagnostics = connection.diagnostics
        diagnostics.setPhase("restore")
        conversations.entries
            .filter { it.key.serverId == serverId }
            .also { diagnostics.restoreTotal = it.size }
            .forEach { (key, state) ->
                pendingResumeGoalSnapshots.add(key)
                val backgroundRefresh = beginBackgroundTerminalRefresh(key)
                val result = runSuspendCatching { resumeConversation(key, api) }
                val snapshot = result.getOrNull()
                if (snapshot != null) {
                    state.updateState { current ->
                        current.withRefreshedConversation(snapshot.toConversationState(server))
                    }
                    removeRestoredItemUpdates(key, state.value)
                    clearExternalWriterRuntime(key, state.value)
                    completeBackgroundTerminalRefresh(
                        key = key,
                        state = state,
                        buffer = backgroundRefresh,
                        result = (if (snapshot.accessMode == ConversationAccessMode.EXTERNAL_WRITER) {
                            Result.success(emptyList())
                        } else runSuspendCatching { loadAllBackgroundTerminals(api, key.threadId) })
                            .onFailure { error ->
                                if (error !is SocketTimeoutException) {
                                    diagnostics.warning("restore_background_terminals_failed", error)
                                }
                            },
                    )
                } else {
                    val error = requireNotNull(result.exceptionOrNull())
                    if (error !is SocketTimeoutException) diagnostics.warning("restore_failed", error)
                    cancelBackgroundTerminalRefresh(key, backgroundRefresh)
                    pendingResumeGoalSnapshots.remove(key)
                    state.updateState {
                        it.copy(
                            activeItemKeys = emptySet(),
                            isRunning = false,
                            errorMessage = error.toUserFacingError("恢复会话失败"),
                        )
                    }
                }
                diagnostics.restoreCompleted += 1
            }
        diagnostics.setPhase("ready")
    }

    private fun observeConnection(
        serverId: String,
        managed: ManagedConnection,
    ): List<Job> {
        val connection = managed.connection
        val statusObserver = applicationScope.launch {
            connection.status.collect { status ->
                if (connections.isCurrent(serverId, managed)) {
                    mutableConnectionStatuses.updateValue(serverId, status)
                    conversations.entries.filter { it.key.serverId == serverId }.forEach { (key, state) ->
                        if (status is ConnectionStatus.Connected) {
                            requestRuntimeQueueRefresh(key)
                        } else {
                            goalRevisions[key]?.incrementAndGet()
                            runtimeQueueRefreshes[key]?.revision?.incrementAndGet()
                            state.updateState { it.copy(runtimeStatus = it.runtimeStatus.markStale()) }
                        }
                    }
                }
            }
        }
        val eventObserver = applicationScope.launch {
            connection.events.collect { event ->
                if (!connections.isCurrent(serverId, managed)) return@collect
                try {
                    handleServerEvent(serverId, connection, event)
                } catch (error: Throwable) {
                    val threadId = event.paramsOrNull()?.optionalString("threadId")
                    if (threadId != null) {
                        conversations[ConversationKey(serverId, threadId)]?.updateState {
                            it.copy(errorMessage = error.toUserFacingError("处理服务器事件失败"))
                        }
                    }
                } finally {
                    if (connection.status.value !is ConnectionStatus.Connected) {
                        val threadId = event.paramsOrNull()?.optionalString("threadId")
                        if (threadId != null) {
                            conversations[ConversationKey(serverId, threadId)]?.updateState {
                                it.copy(runtimeStatus = it.runtimeStatus.markStale())
                            }
                        }
                    }
                }
            }
        }
        return listOf(statusObserver, eventObserver)
    }

    private fun handleServerEvent(
        serverId: String,
        connection: AppServerConnection,
        event: ServerEvent,
    ) {
        when (event) {
            is ServerEvent.Request -> handleServerRequest(serverId, connection, event)
            is ServerEvent.Notification -> handleNotification(serverId, event.method, event.params)
        }
    }

    private fun handleServerRequest(
        serverId: String,
        connection: AppServerConnection,
        request: ServerEvent.Request,
    ) {
        if (request.method !in SUPPORTED_SERVER_REQUESTS) {
            connection.respondError(request.id, METHOD_NOT_FOUND, "客户端不支持服务端请求：${request.method}")
            return
        }
        val threadId = request.params.requiredString("threadId")
        val conversation = conversations[ConversationKey(serverId, threadId)]
        if (conversation == null) {
            connection.respondError(request.id, INVALID_REQUEST, "会话尚未在客户端打开")
            return
        }
        val requestKey = requestKey(serverId, request.id)
        val pending = ProtocolMapper.pendingServerRequest(
            method = request.method,
            params = request.params,
            requestKey = requestKey,
            receivedAtMillis = System.currentTimeMillis(),
        )
        pendingRequestRoutes[requestKey] = PendingRequestRoute(request.id, threadId)
        conversation.updateState { current ->
            current.copy(pendingRequests = current.pendingRequests + pending)
        }
        setThreadAttention(serverId, threadId, ThreadAttention.NEEDS_INPUT)
        notifyConversation(
            ConversationNotification(
                serverId = serverId,
                threadId = threadId,
                title = conversation.value.title,
                message = pending.notificationMessage(),
                attention = ThreadAttention.NEEDS_INPUT,
            ),
        )
    }

    private fun handleNotification(serverId: String, method: String, params: JsonObject) {
        ProtocolMapper.subAgentEvent(method, params)?.let { event ->
            subAgentMonitors.entries.filter { it.key.serverId == serverId }
                .forEach { (_, monitors) -> monitors.forEach { it.accept(event) } }
        }
        val notification = ProtocolMapper.notification(serverId, method, params)
        if (notification.bumpFeatureRevision) bumpFeatureRevision(serverId)
        if (notification.bumpRateLimitRevision) bumpRateLimitRevision(serverId)
        if (notification.bumpThreadRevision) bumpThreadRevision(serverId)
        notification.serverNotice?.let { addServerNotice(serverId, it) }

        val threadId = notification.threadId ?: return
        val key = ConversationKey(serverId, threadId)
        if (notification.conversationEvent is ConversationEvent.GoalChanged) {
            goalRevisions.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()
        }
        RuntimeStatusMapper.notification(method, params)?.let { runtimeEvent ->
            conversations[key]?.updateState { it.copy(runtimeStatus = it.runtimeStatus.reduce(runtimeEvent)) }
            if (runtimeEvent == RuntimeStatusEvent.QueueChanged) requestRuntimeQueueRefresh(key)
        }
        val event = notification.conversationEvent ?: return
        conversations[key]?.value?.let { conversation ->
            if (asyncQuestionNotifications.accept(serverId, threadId, method, event)) {
                notifyConversation(
                    ConversationNotification(
                        serverId = serverId,
                        threadId = threadId,
                        title = conversation.title,
                        message = "Codex 有新的问题，可以打开会话回复",
                        attention = ThreadAttention.NEEDS_INPUT,
                    ),
                )
            }
        }
        event.backgroundTerminalChange()?.let { change ->
            backgroundTerminalRefreshBuffers[key]?.let { buffer ->
                synchronized(buffer) {
                    if (buffer.accepting) buffer.changes += change
                }
            }
            applyBackgroundTerminalChange(key, change)
        }
        if (tryBufferConversationEvent(key, event)) return
        applyConversationEvent(serverId, threadId, event)
        if (event is ConversationEvent.ItemUpserted && !event.isActive) {
            releaseConversationIfUnused(key)
        }
    }

    private fun applyConversationEvent(
        serverId: String,
        threadId: String,
        event: ConversationEvent,
    ) {
        val key = ConversationKey(serverId, threadId)
        val state = conversations[key]
        when (event) {
            is ConversationEvent.StatusChanged -> {
                state?.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                when (event.status) {
                    ConversationRunStatus.ACTIVE ->
                        setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)

                    ConversationRunStatus.SYSTEM_ERROR ->
                        setThreadAttention(serverId, threadId, ThreadAttention.FAILED)

                    ConversationRunStatus.INACTIVE -> Unit
                }
            }

            is ConversationEvent.Closed -> {
                removePendingRequests(serverId, threadId)
                state?.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                pendingResumeGoalSnapshots.remove(key)
                removePendingItemUpdates(key)
                conversationEventBuffers.remove(key)
                backgroundTerminalRefreshBuffers.remove(key)
                historyPagingMutexes.remove(key)
                runtimeQueueRefreshes.remove(key)?.revision?.incrementAndGet()
                goalRevisions.remove(key)?.incrementAndGet()
                conversations.remove(key)
            }

            is ConversationEvent.GoalChanged -> {
                val announce = !pendingResumeGoalSnapshots.remove(key)
                state?.updateState { current ->
                    current.reduceConversationEvent(event, announceGoal = announce)
                }
            }

            is ConversationEvent.TurnStarted -> {
                if (state == null) return
                state.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                setThreadAttention(serverId, threadId, ThreadAttention.RUNNING)
            }

            is ConversationEvent.TurnCompleted -> {
                if (state == null) return
                val completedTurnItems = state.value.items.filter { it.turnId == event.turnId }
                val shellItem = completedTurnItems.singleOrNull() as? ConversationItem.UserShellCommand
                val shellFailed = shellItem?.status == ActivityStatus.FAILED ||
                    shellItem?.status == ActivityStatus.DECLINED
                state.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                val completionAttention = if (event.error == null && !shellFailed) {
                    ThreadAttention.READY
                } else {
                    ThreadAttention.FAILED
                }
                setThreadAttention(
                    serverId,
                    threadId,
                    completedThreadAttention(
                        hasError = event.error != null || shellFailed,
                        isViewed = conversationLeases.isHeld(key),
                    ),
                )
                if (state.value.parentThreadId == null) {
                    notifyConversation(
                        ConversationNotification(
                            serverId = serverId,
                            threadId = threadId,
                            title = state.value.title,
                            message = when {
                                event.error != null -> event.error.summary
                                shellItem == null -> "任务已完成，可以查看结果"
                                shellFailed -> "Shell 命令执行失败，可以查看输出"
                                else -> "Shell 命令已完成，可以查看输出"
                            },
                            attention = completionAttention,
                        ),
                    )
                }
                removePendingRequests(serverId, threadId)
                removePendingItemUpdates(key, event.turnId)
                releaseConversationIfUnused(key)
            }

            is ConversationEvent.ItemUpserted -> {
                if (state == null) return
                state.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                val route = ItemRoute(serverId, threadId, event.item.turnId, event.item.id)
                if (event.isActive) replayPendingItemUpdates(state, route)
                else pendingItemUpdates.remove(route)
            }

            is ConversationEvent.ItemChanged -> {
                if (state == null) return
                val update = PendingItemUpdate(event.method, event.markActive, event.update)
                if (!state.updateItem(event.turnId, event.itemId, event.markActive, event.update::apply)) {
                    val route = ItemRoute(serverId, threadId, event.turnId, event.itemId)
                    var firstPendingUpdate = false
                    pendingItemUpdates.compute(route) { _, current ->
                        firstPendingUpdate = current == null
                        current.orEmpty().toMutableList().apply { add(update) }
                    }
                    if (firstPendingUpdate) {
                        val current = state.value
                        Log.w(
                            LOG_TAG,
                            "暂存缺少基础条目的流式事件：method=${event.method}, " +
                                "turnId=${event.turnId}, itemId=${event.itemId}, " +
                                "activeTurnId=${current.activeTurnId}, itemCount=${current.items.size}",
                        )
                    }
                }
            }

            is ConversationEvent.RequestResolved -> {
                pendingRequestRoutes.remove(event.requestKey)
                state?.updateState { current ->
                    current.reduceConversationEvent(event)
                }
                state?.value?.let { current ->
                    setThreadAttention(
                        serverId,
                        threadId,
                        if (current.pendingRequests.isNotEmpty()) ThreadAttention.NEEDS_INPUT
                        else if (current.isRunning) ThreadAttention.RUNNING else null,
                    )
                }
            }

            else -> state?.updateState { current ->
                current.reduceConversationEvent(event)
            }
        }
    }

    private fun removePendingRequests(serverId: String, threadId: String) {
        pendingRequestRoutes.entries
            .filter { (key, request) -> key.startsWith("$serverId:") && request.threadId == threadId }
            .forEach { pendingRequestRoutes.remove(it.key) }
        conversations[ConversationKey(serverId, threadId)]?.updateState { it.copy(pendingRequests = emptyList()) }
    }

    private fun clearServerPendingRequests(serverId: String) {
        pendingRequestRoutes.keys
            .filter { it.startsWith("$serverId:") }
            .forEach { pendingRequestRoutes.remove(it) }
        conversations.entries
            .filter { it.key.serverId == serverId }
            .forEach { it.value.updateState { state -> state.copy(pendingRequests = emptyList()) } }
        mutableThreadAttention.update { it - serverId }
    }

    private fun requireConversation(serverId: String, threadId: String): MutableStateFlow<ConversationState> =
        conversations[ConversationKey(serverId, threadId)] ?: error("会话尚未打开")

    private fun checkConversationWritableIfOpen(serverId: String, threadId: String) {
        conversations[ConversationKey(serverId, threadId)]?.value?.requireWritable()
    }

    private fun clearExternalWriterRuntime(key: ConversationKey, state: ConversationState) {
        if (!state.isExternalWriter) return
        runtimeQueueRefreshes[key]?.revision?.incrementAndGet()
        removePendingRequests(key.serverId, key.threadId)
    }

    suspend fun loadConversationTurnDetails(serverId: String, threadId: String, turnId: String) {
        requireConversation(serverId, threadId).loadTurnDetails(turnId) {
            loadAllTurnItems(api(serverId), threadId, turnId)
        }
    }

    private suspend fun resumeConversation(
        key: ConversationKey,
        api: AppServerApi,
    ): ConversationResumeResult {
        pendingResumeGoalSnapshots.add(key)
        return try {
            resumeOrReadConversation(api, key.threadId) { turnId ->
                loadAllTurnItems(api, key.threadId, turnId)
            }.also { result ->
                if (result.accessMode == ConversationAccessMode.EXTERNAL_WRITER) {
                    pendingResumeGoalSnapshots.remove(key)
                }
            }
        } catch (error: Throwable) {
            pendingResumeGoalSnapshots.remove(key)
            throw error
        }
    }

    private suspend fun refreshConversation(
        key: ConversationKey,
        state: MutableStateFlow<ConversationState>,
        failOnError: Boolean = false,
        reportErrors: Boolean = true,
    ) {
        val buffer = beginConversationEventBuffer(key)
        val backgroundRefresh = beginBackgroundTerminalRefresh(key)
        try {
            val targetApi = api(key.serverId)
            val snapshot = resumeConversation(key, targetApi)
            val backgroundTerminals = if (snapshot.accessMode == ConversationAccessMode.EXTERNAL_WRITER) {
                Result.success(emptyList())
            } else runSuspendCatching { loadAllBackgroundTerminals(targetApi, key.threadId) }
            val server = awaitServer(key.serverId)
            completeConversationRefresh(key, state, buffer) { current ->
                current.withRefreshedConversation(snapshot.toConversationState(server))
            }
            removeRestoredItemUpdates(key, state.value)
            clearExternalWriterRuntime(key, state.value)
            completeBackgroundTerminalRefresh(
                key = key,
                state = state,
                buffer = backgroundRefresh,
                result = backgroundTerminals,
            )
        } catch (error: CancellationException) {
            cancelBackgroundTerminalRefresh(key, backgroundRefresh)
            completeConversationRefresh(key, state, buffer) { it }
            throw error
        } catch (error: Throwable) {
            cancelBackgroundTerminalRefresh(key, backgroundRefresh)
            completeConversationRefresh(key, state, buffer) { current ->
                if (reportErrors) current.copy(errorMessage = error.toUserFacingError("刷新会话失败")) else current
            }
            if (failOnError) throw error
        }
    }

    private suspend fun loadAllTurnItems(
        api: AppServerApi,
        threadId: String,
        turnId: String,
    ): List<ConversationItem> {
        val items = mutableListOf<ConversationItem>()
        var cursor: String? = null
        do {
            val page = ProtocolMapper.threadItemPage(
                response = api.listThreadItems(threadId, turnId, cursor),
                expectedTurnId = turnId,
            )
            items += page.data
            if (page.nextCursor != null && page.nextCursor == cursor) {
                throw ProtocolException("thread/items/list 返回了未推进的游标")
            }
            cursor = page.nextCursor
        } while (cursor != null)
        return items
    }

    private fun requestRuntimeQueueRefresh(key: ConversationKey) {
        if (conversations[key]?.value?.isExternalWriter != false) return
        val refresh = runtimeQueueRefreshes.computeIfAbsent(key) { RuntimeQueueRefresh() }
        val revision = refresh.revision.incrementAndGet()
        applicationScope.launch {
            refresh.mutex.withLock {
                if (revision != refresh.revision.get()) return@withLock
                conversations[key]?.updateState {
                    if (revision != refresh.revision.get()) it else
                        it.copy(runtimeStatus = it.runtimeStatus.reduce(RuntimeStatusEvent.QueueChanged))
                }
                val result = runSuspendCatching {
                    val api = api(key.serverId)
                    val entries = mutableListOf<RuntimeQueuedInput>()
                    val cursors = mutableSetOf<String>()
                    var cursor: String? = null
                    do {
                        val page = RuntimeStatusMapper.queuePage(api.listQueuedInputs(key.threadId, cursor))
                        entries += page.data
                        cursor = page.nextCursor
                        check(cursor == null || cursors.add(cursor)) { "输入队列返回了重复的游标" }
                    } while (cursor != null)
                    entries
                }
                // 通知触发了更新读取或连接已断开时，丢弃此请求的旧结果
                if (revision != refresh.revision.get()) return@withLock
                conversations[key]?.updateState { current ->
                    if (revision != refresh.revision.get() || current.isExternalWriter) return@updateState current
                    val queue = result.fold(
                        onSuccess = { RuntimeInputQueue(entries = it) },
                        onFailure = {
                            (current.runtimeStatus.queue ?: RuntimeInputQueue()).copy(
                                loading = false,
                                error = it.toUserFacingError("无法同步待处理输入"),
                                stale = true,
                            )
                        },
                    )
                    current.copy(runtimeStatus = current.runtimeStatus.reduce(RuntimeStatusEvent.QueueLoaded(queue)))
                }
            }
        }
    }

    private class RuntimeQueueRefresh {
        val mutex = Mutex()
        val revision = AtomicLong()
    }

    private suspend fun loadAllBackgroundTerminals(
        api: AppServerApi,
        threadId: String,
    ): List<BackgroundTerminal> {
        val terminals = mutableListOf<BackgroundTerminal>()
        val observedAtMillis = System.currentTimeMillis()
        var cursor: String? = null
        do {
            val page = ProtocolMapper.backgroundTerminalPage(
                response = api.listBackgroundTerminals(threadId, cursor),
                observedAtMillis = observedAtMillis,
            )
            terminals += page.data
            if (page.nextCursor != null && page.nextCursor == cursor) {
                throw ProtocolException("thread/backgroundTerminals/list 返回了未推进的游标")
            }
            cursor = page.nextCursor
        } while (cursor != null)
        return terminals
    }

    private fun beginBackgroundTerminalRefresh(key: ConversationKey): BackgroundTerminalRefreshBuffer {
        val buffer = BackgroundTerminalRefreshBuffer()
        backgroundTerminalRefreshBuffers.put(key, buffer)?.let { previous ->
            synchronized(previous) { previous.accepting = false }
        }
        return buffer
    }

    private fun completeBackgroundTerminalRefresh(
        key: ConversationKey,
        state: MutableStateFlow<ConversationState>,
        buffer: BackgroundTerminalRefreshBuffer,
        result: Result<List<BackgroundTerminal>>,
    ) {
        synchronized(buffer) {
            if (!buffer.accepting) return
            state.updateState { current ->
                val refreshed = result.fold(
                    onSuccess = { terminals ->
                        val observedAtByProcess = current.backgroundTerminals.associate {
                            it.processId to it.observedAtMillis
                        }
                        current.copy(
                            backgroundTerminals = terminals.map { terminal ->
                                terminal.copy(
                                    observedAtMillis = observedAtByProcess[terminal.processId]
                                        ?: terminal.observedAtMillis,
                                )
                            },
                            backgroundTerminalsLoaded = true,
                            backgroundTerminalsError = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            backgroundTerminalsLoaded = true,
                            backgroundTerminalsError = error.toUserFacingError("无法同步运行中的命令"),
                        )
                    },
                )
                buffer.changes.fold(refreshed, ConversationState::applyBackgroundTerminalChange)
            }
            buffer.accepting = false
            backgroundTerminalRefreshBuffers.remove(key, buffer)
        }
    }

    private fun cancelBackgroundTerminalRefresh(
        key: ConversationKey,
        buffer: BackgroundTerminalRefreshBuffer,
    ) {
        synchronized(buffer) {
            buffer.accepting = false
            backgroundTerminalRefreshBuffers.remove(key, buffer)
        }
    }

    private fun applyBackgroundTerminalChange(
        key: ConversationKey,
        change: BackgroundTerminalChange,
    ) {
        conversations[key]?.updateState { current -> current.applyBackgroundTerminalChange(change) }
    }

    private fun beginConversationEventBuffer(key: ConversationKey): ConversationEventBuffer {
        val buffer = ConversationEventBuffer()
        check(conversationEventBuffers.putIfAbsent(key, buffer) == null) {
            "会话正在恢复：${key.threadId}"
        }
        return buffer
    }

    private fun completeConversationRefresh(
        key: ConversationKey,
        state: MutableStateFlow<ConversationState>,
        buffer: ConversationEventBuffer,
        transform: (ConversationState) -> ConversationState,
    ) {
        synchronized(buffer) {
            state.updateState(transform)
            buffer.events.forEach { event ->
                applyConversationEvent(key.serverId, key.threadId, event)
            }
            buffer.accepting = false
            conversationEventBuffers.remove(key, buffer)
        }
    }

    private fun tryBufferConversationEvent(
        key: ConversationKey,
        event: ConversationEvent,
    ): Boolean {
        val buffer = conversationEventBuffers[key] ?: return false
        synchronized(buffer) {
            if (!buffer.accepting) return false
            buffer.events += event
            return true
        }
    }

    private suspend fun awaitServer(serverId: String): ServerProfile =
        withTimeoutOrNull(SERVER_LOAD_TIMEOUT_MILLIS) {
            serverSettings
                .map { settings -> settings.servers.singleOrNull { it.id == serverId } }
                .first { it != null }
        } ?: error("服务器不存在")

    private fun ConversationResumeResult.toConversationState(profile: ServerProfile): ConversationState =
        snapshot.toConversationState(profile).let { state ->
            state.copy(
                accessMode = accessMode,
                desktopControlAvailable = desktopControlAvailable,
                // thread/read reports notLoaded for another app-server's thread;
                // the paginated history still identifies its in-progress turn.
                isRunning = state.isRunning ||
                    (accessMode == ConversationAccessMode.EXTERNAL_WRITER && state.activeTurnId != null),
            )
        }

    private fun ThreadSnapshot.toConversationState(
        profile: ServerProfile,
    ): ConversationState {
        return ConversationState(
            serverId = profile.id,
            serverName = profile.name,
            threadId = summary.id,
            title = summary.title,
            cwd = summary.cwd,
            parentThreadId = summary.parentThreadId,
            agentNickname = summary.agentNickname,
            agentRole = summary.agentRole,
            canAcceptDirectInput = summary.canAcceptDirectInput,
            history = ConversationHistory(
                turns = turns,
                olderTurnsCursor = olderTurnsCursor,
            ),
            activeTurnId = turns.lastOrNull { it.status == ConversationTurnStatus.IN_PROGRESS }?.id,
            isRunning = summary.status == ThreadRuntimeStatus.ACTIVE,
            model = model,
            serviceTier = serviceTier,
            reasoningEffort = reasoningEffort,
            approvalPolicy = approvalPolicy,
            approvalsReviewer = approvalsReviewer,
            sandbox = sandbox,
        )
    }

    private fun MutableStateFlow<ConversationState>.updateItem(
        turnId: String,
        itemId: String,
        markActive: Boolean = false,
        transform: (ConversationItem) -> ConversationItem,
    ): Boolean {
        var found = false
        updateState { current ->
            val updated = current.withUpdatedConversationItem(turnId, itemId, markActive, transform)
            found = updated != null
            updated ?: current
        }
        return found
    }

    private fun replayPendingItemUpdates(
        state: MutableStateFlow<ConversationState>,
        route: ItemRoute,
    ) {
        pendingItemUpdates.remove(route)?.forEach { update ->
            check(state.updateItem(route.turnId, route.itemId, update.markActive, update.update::apply)) {
                "无法重放流式事件：${update.method} ${route.turnId}/${route.itemId}"
            }
        }
    }

    private fun removeRestoredItemUpdates(key: ConversationKey, state: ConversationState) {
        val restoredKeys = state.items.mapTo(mutableSetOf(), ConversationItem::key)
        pendingItemUpdates.keys.removeIf { route ->
            route.serverId == key.serverId && route.threadId == key.threadId &&
                ConversationItemKey(route.turnId, route.itemId) in restoredKeys
        }
    }

    private fun removePendingItemUpdates(key: ConversationKey, turnId: String? = null) {
        pendingItemUpdates.keys.removeIf { route ->
            route.serverId == key.serverId && route.threadId == key.threadId &&
                (turnId == null || route.turnId == turnId)
        }
    }

    private fun <T> MutableStateFlow<T>.updateState(transform: (T) -> T) {
        update(transform)
    }

    private fun MutableStateFlow<Map<String, ConnectionStatus>>.updateValue(
        serverId: String,
        status: ConnectionStatus,
    ) {
        update { it + (serverId to status) }
    }

    private fun ServerEvent.paramsOrNull(): JsonObject? = when (this) {
        is ServerEvent.Notification -> params
        is ServerEvent.Request -> params
    }

    private fun respondToServerRequest(
        serverId: String,
        threadId: String,
        key: String,
        result: JsonObject,
    ) {
        requireConversation(serverId, threadId).value.requireWritable()
        val route = pendingRequestRoutes[key] ?: error("服务端请求已失效")
        check(route.threadId == threadId) { "服务端请求不属于当前会话" }
        val connection = connections.current(serverId)?.connection ?: error("服务器未连接")
        connection.respond(route.id, result)
        pendingRequestRoutes.remove(key)
        val conversation = conversations[ConversationKey(serverId, threadId)] ?: return
        conversation.updateState { current ->
            current.copy(pendingRequests = current.pendingRequests.filterNot { it.requestKey == key })
        }
        val current = conversation.value
        setThreadAttention(
            serverId,
            threadId,
            if (current.pendingRequests.isNotEmpty()) ThreadAttention.NEEDS_INPUT
            else if (current.isRunning) ThreadAttention.RUNNING else null,
        )
    }

    private fun List<UserInputPart>.conversationTitle(): String =
        filterIsInstance<UserInputPart.Text>()
            .firstNotNullOfOrNull { text ->
                text.text.lineSequence().firstOrNull(String::isNotBlank)?.trim()
            }
            ?: firstNotNullOfOrNull { input ->
                when (input) {
                    is UserInputPart.Text -> null
                    is UserInputPart.Image, is UserInputPart.UploadedImage, is UserInputPart.LocalImage -> "图片"
                    is UserInputPart.Audio, is UserInputPart.LocalAudio -> "音频"
                    is UserInputPart.Skill -> "使用 Skill：${input.name}"
                    is UserInputPart.Mention -> "提及：${input.name}"
                }
            }
            ?: "新消息"

    private fun requestKey(serverId: String, id: JsonPrimitive): String = "$serverId:${id}"

    private fun bumpThreadRevision(serverId: String) {
        mutableThreadRevisions.increment(serverId)
    }

    private fun bumpFeatureRevision(serverId: String) {
        mutableFeatureRevisions.increment(serverId)
    }

    private fun bumpRateLimitRevision(serverId: String) {
        mutableRateLimitRevisions.increment(serverId)
    }

    private fun MutableStateFlow<Map<String, Long>>.increment(serverId: String) {
        update { current -> current + (serverId to ((current[serverId] ?: 0L) + 1L)) }
    }

    private fun addServerNotice(serverId: String, message: String) {
        mutableServerNotices.update { current ->
            val notices = current[serverId].orEmpty() +
                ConversationNotice(UUID.randomUUID().toString(), message)
            current + (serverId to notices.takeLast(MAX_NOTICES))
        }
    }

    private fun setThreadAttention(serverId: String, threadId: String, attention: ThreadAttention?) {
        mutableThreadAttention.update { current ->
            val serverAttention = current[serverId].orEmpty()
            val updated = if (attention == null) serverAttention - threadId
            else serverAttention + (threadId to ThreadAttentionState(attention, threadAttentionRevision.incrementAndGet()))
            if (updated.isEmpty()) current - serverId else current + (serverId to updated)
        }
    }

    private fun markThreadViewed(serverId: String, threadId: String) {
        val attention = mutableThreadAttention.value[serverId]?.get(threadId)?.attention
        if (attention == ThreadAttention.READY || attention == ThreadAttention.FAILED) {
            setThreadAttention(serverId, threadId, null)
        }
    }

    private fun PendingServerRequest.notificationMessage(): String = when (this) {
        is PendingServerRequest.Approval -> if (type == ApprovalType.COMMAND_EXECUTION) {
            "命令等待确认"
        } else {
            "文件修改等待确认"
        }

        is PendingServerRequest.Questions -> "Codex 需要你的回答"
        is PendingServerRequest.McpElicitation -> "$serverName 请求确认"
        is PendingServerRequest.Permissions -> "权限请求等待处理"
    }

    private data class ConversationKey(val serverId: String, val threadId: String)

    private data class ItemRoute(
        val serverId: String,
        val threadId: String,
        val turnId: String,
        val itemId: String,
    )

    private data class PendingItemUpdate(
        val method: String,
        val markActive: Boolean,
        val update: ConversationItemUpdate,
    )

    private data class PendingRequestRoute(val id: JsonPrimitive, val threadId: String)

    private class ConversationEventBuffer {
        val events = mutableListOf<ConversationEvent>()
        var accepting = true
    }

    private class BackgroundTerminalRefreshBuffer {
        val changes = mutableListOf<BackgroundTerminalChange>()
        var accepting = true
    }

    private class ManagedConnection(
        val connection: AppServerConnection,
        var observers: List<Job> = emptyList(),
    )

    private companion object {
        const val LOG_TAG = "CodexRepository"
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_REQUEST = -32600
        const val SERVER_LOAD_TIMEOUT_MILLIS = 5_000L
        val SUPPORTED_SERVER_REQUESTS = setOf(
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            "item/tool/requestUserInput",
            "mcpServer/elicitation/request",
            "item/permissions/requestApproval",
        )
    }
}

private fun validateProxyCredential(
    configuration: ProxyConfiguration,
    credential: String?,
    existingCredentialCanBeReused: Boolean,
): String? {
    val acceptsCredential = configuration.requiresCredential() ||
        configuration is ProxyConfiguration.Ssh && configuration.authentication == SshAuthentication.PRIVATE_KEY
    if (!acceptsCredential) return null
    val value = credential?.takeIf(String::isNotEmpty)
    require(!configuration.requiresCredential() || value != null || existingCredentialCanBeReused) {
        "当前认证方式需要凭据"
    }
    value?.let {
        require(!it.contains('\r') && !it.contains('\n')) { "代理凭据不能换行" }
        when (configuration) {
            is ProxyConfiguration.HttpConnect -> {
                if (configuration.authentication == HttpProxyAuthentication.BEARER) {
                    require(it.all { character -> character.code in 0x21..0x7e }) { "代理令牌包含无效字符" }
                }
                require(it.toByteArray().size <= 8 * 1024) { "代理凭据过长" }
            }
            is ProxyConfiguration.Socks5 -> require(it.toByteArray().size <= 255) { "SOCKS5 密码过长" }
            is ProxyConfiguration.Ssh -> require(it.toByteArray().size <= 8 * 1024) { "SSH 凭据过长" }
        }
    }
    return value
}

private fun ProxyConfiguration.hasSameCredentialPurpose(other: ProxyConfiguration): Boolean = when {
    this is ProxyConfiguration.HttpConnect && other is ProxyConfiguration.HttpConnect ->
        authentication == other.authentication
    this is ProxyConfiguration.Socks5 && other is ProxyConfiguration.Socks5 ->
        authentication == other.authentication
    this is ProxyConfiguration.Ssh && other is ProxyConfiguration.Ssh ->
        authentication == other.authentication
    else -> false
}

private fun Throwable.findSshHostKeyError(): SshHostKeyVerificationException? {
    var current: Throwable? = this
    while (current != null) {
        if (current is SshHostKeyVerificationException) return current
        current = current.cause
    }
    return null
}

private fun ConversationEvent.backgroundTerminalChange(): BackgroundTerminalChange? =
    when (this) {
        is ConversationEvent.ItemUpserted -> {
            val command = item as? ConversationItem.CommandExecution ?: return null
            if (isActive && command.status == ActivityStatus.IN_PROGRESS && command.processId != null) {
                BackgroundTerminalChange.Upsert(
                    BackgroundTerminal(
                        itemId = command.id,
                        processId = command.processId,
                        command = command.command,
                        cwd = command.cwd,
                        observedAtMillis = System.currentTimeMillis(),
                    ),
                )
            } else if (!isActive) {
                BackgroundTerminalChange.Remove(itemId = command.id)
            } else {
                null
            }
        }

        else -> null
    }

private fun ConversationState.applyBackgroundTerminalChange(
    change: BackgroundTerminalChange,
): ConversationState = when (change) {
    is BackgroundTerminalChange.Upsert -> {
        val observedAtMillis = backgroundTerminals
            .firstOrNull { it.processId == change.terminal.processId }
            ?.observedAtMillis
            ?: change.terminal.observedAtMillis
        copy(
            backgroundTerminals = backgroundTerminals
                .filterNot {
                    it.itemId == change.terminal.itemId ||
                        it.processId == change.terminal.processId
                } + change.terminal.copy(observedAtMillis = observedAtMillis),
            backgroundTerminalsError = null,
        )
    }

    is BackgroundTerminalChange.Remove -> copy(
        backgroundTerminals = backgroundTerminals.filterNot {
            it.itemId == change.itemId || it.processId == change.processId
        },
    )
}

internal fun completedThreadAttention(
    hasError: Boolean,
    isViewed: Boolean,
): ThreadAttention? = if (isViewed) {
    null
} else if (hasError) {
    ThreadAttention.FAILED
} else {
    ThreadAttention.READY
}
