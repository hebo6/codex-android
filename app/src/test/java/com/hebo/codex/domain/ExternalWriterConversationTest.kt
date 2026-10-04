package com.hebo.codex.domain

import com.hebo.codex.data.local.ServerSettings
import com.hebo.codex.data.local.ServerStore
import com.hebo.codex.data.remote.AppServerClientFactory
import com.hebo.codex.data.remote.AppServerConnection
import com.hebo.codex.data.remote.AppServerWebSocket
import com.hebo.codex.data.remote.AppServerWebSocketFactory
import com.hebo.codex.data.remote.RpcException
import com.hebo.codex.data.remote.requiredObject
import com.hebo.codex.data.remote.requiredString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExternalWriterConversationTest {
    @Test
    fun normalResumeRemainsWritableAndDoesNotReadThread() = runTest {
        withFixture { fixture ->
            val state = fixture.repository.openConversation("server", "thread").value
            assertEquals(ConversationAccessMode.WRITABLE, state.accessMode)
            assertFalse(state.isViewOnly)
            assertEquals(listOf("old", "running"), state.history.turns.map { it.id })
            assertEquals("older", state.history.olderTurnsCursor)
            assertFalse("thread/read" in fixture.methods)
            assertFalse("thread/turns/list" in fixture.methods)
        }
    }

    @Test
    fun conflictReadsMetadataAndPagesHistoryWithoutClaimingWriter() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            val flow = fixture.repository.openConversation("server", "thread")
            val state = flow.value
            assertTrue(state.isExternalWriter)
            assertTrue(state.isViewOnly)
            assertEquals(true, state.canAcceptDirectInput)
            assertEquals(null, state.parentThreadId)
            assertEquals("running", state.activeTurnId)
            assertEquals(listOf("old", "running"), state.history.turns.map { it.id })
            assertEquals(listOf("answer-1", "answer-2"), state.history.turns.last().items.map { it.id })
            assertEquals(TurnDetailsState.Loaded, state.history.turns.last().detailsState)
            assertEquals(TurnDetailsState.Unloaded, state.history.turns.first().detailsState)
            assertEquals("older", state.history.olderTurnsCursor)
            assertEquals(
                listOf("thread/resume", "thread/read", "thread/turns/list", "thread/items/list", "thread/items/list", "codexAndroid/desktop/discover"),
                fixture.methods,
            )
            assertEquals(json("""{"threadId":"thread","includeTurns":false}"""),
                fixture.requests[1].requiredObject("params"))
            assertEquals(json("""{"threadId":"thread","limit":25,"sortDirection":"desc","itemsView":"summary"}"""),
                fixture.requests[2].requiredObject("params"))

            assertTrue(fixture.repository.loadOlderConversationTurns("server", "thread"))
            assertEquals(listOf("oldest", "old", "running"), flow.value.history.turns.map { it.id })
            assertEquals(null, flow.value.history.olderTurnsCursor)
            fixture.repository.loadConversationTurnDetails("server", "thread", "oldest")
            assertEquals(2, flow.value.history.turns.first().items.size)
            assertEquals(1, fixture.methods.count { it == "thread/resume" })
            assertFalse(flow.value.requiresRuntimeRetention)
        }
    }

    @Test
    fun otherRpcErrorsAreNotSwallowed() = runTest {
        for (error in listOf(RpcException(-32600, "invalid thread", null),
            RpcException(-32001, "already has an active writer", null))) {
            withFixture { fixture ->
                fixture.resumeError = error
                try {
                    fixture.repository.openConversation("server", "thread")
                    fail("Expected resume failure")
                } catch (actual: RpcException) {
                    assertEquals(error.code, actual.code)
                    assertEquals(error.message, actual.message)
                }
                // The connection's existing overload retries remain intact.
                assertEquals(List(if (error.code == -32001) 3 else 1) { "thread/resume" }, fixture.methods)
            }
        }
    }

    @Test
    fun failedReadAfterConflictStillFailsOpening() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.readError = RpcException(-32600, "thread not found", null)
            try {
                fixture.repository.openConversation("server", "thread")
                fail("Expected read failure")
            } catch (error: RpcException) {
                assertEquals("thread not found", error.message)
            }
            assertEquals(listOf("thread/resume", "thread/read"), fixture.methods)
        }
    }

    @Test
    fun failedRefreshKeepsReadOnlyStateAndReportsTheRpcError() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            val flow = fixture.repository.openConversation("server", "thread")
            val readCount = fixture.methods.count { it == "thread/read" }
            fixture.resumeError = RpcException(-32600, "invalid resume params", null)
            fixture.repository.refreshConversation("server", "thread")
            assertTrue(flow.value.isExternalWriter)
            assertEquals(readCount, fixture.methods.count { it == "thread/read" })
            assertTrue(requireNotNull(flow.value.errorMessage).technicalDetails!!.contains("invalid resume params"))
        }
    }

    @Test
    fun readOnlyRepositoryRejectsWritesBeforeSendingRpcOrChangingState() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            val flow = fixture.repository.openConversation("server", "thread")
            val before = flow.value
            val requestCount = fixture.requests.size
            val mutations: List<suspend () -> Unit> = listOf(
                { fixture.repository.sendMessage("server", "thread", "hello") },
                { fixture.repository.queueInput("server", "thread", listOf(UserInputPart.Text("hello"))) },
                { fixture.repository.deleteQueuedInput("server", "thread", "entry"); Unit },
                { fixture.repository.runShellCommand("server", "thread", "pwd") },
                { fixture.repository.interrupt("server", "thread") },
                { fixture.repository.terminateBackgroundTerminal("server", "thread", "process") },
                { fixture.repository.compactConversation("server", "thread") },
                { fixture.repository.reviewConversation("server", "thread", ReviewTarget.UncommittedChanges, ReviewDelivery.INLINE); Unit },
                { fixture.repository.renameConversation("server", "thread", "name") },
                { fixture.repository.archiveConversation("server", "thread") },
                { fixture.repository.unarchiveConversation("server", "thread") },
                { fixture.repository.deleteConversation("server", "thread") },
                { fixture.repository.setGoal("server", "thread", "goal"); Unit },
                { fixture.repository.clearGoal("server", "thread") },
                { fixture.repository.respondToApproval("server", "thread", "request", ApprovalDecision.ACCEPT) },
                { fixture.repository.respondToApprovalWithExecPolicy("server", "thread", "request", json("{}")) },
                { fixture.repository.respondToApprovalWithNetworkPolicy("server", "thread", "request", json("{}")) },
                { fixture.repository.respondToQuestions("server", "thread", "request", emptyMap()) },
                { fixture.repository.respondToMcpElicitation("server", "thread", "request", "accept") },
                { fixture.repository.respondToPermissions("server", "thread", "request", json("{}"), false, false) },
            )
            for ((index, mutation) in mutations.withIndex()) {
                try {
                    mutation()
                    fail("Mutation $index should be blocked")
                } catch (error: IllegalStateException) {
                    assertEquals(before.viewOnlyMessage, error.message)
                }
            }
            assertEquals(requestCount, fixture.requests.size)
            assertEquals(before, flow.value)
            assertTrue(fixture.responses.isEmpty())
        }
    }

    @Test
    fun forkIsAvailableWhileSourceRemainsReadOnly() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            val source = fixture.repository.openConversation("server", "thread")
            val forkId = fixture.repository.forkConversation("server", "thread", "old")
            assertEquals("fork", forkId)
            assertEquals("thread/fork", fixture.methods.last())
            assertEquals(json("""{"threadId":"thread","lastTurnId":"old","excludeTurns":true}"""),
                fixture.requests.last().requiredObject("params"))
            assertTrue(source.value.isExternalWriter)
            fixture.resumeError = null
            assertFalse(fixture.repository.openConversation("server", forkId).value.isViewOnly)
        }
    }

    @Test
    fun refreshRetriesResumeAndRestoresWritableStateAndOptions() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            val flow = fixture.repository.openConversation("server", "thread")
            fixture.repository.refreshConversation("server", "thread")
            assertTrue(flow.value.isExternalWriter)
            fixture.resumeError = null
            fixture.repository.refreshConversation("server", "thread")
            assertFalse(flow.value.isViewOnly)
            assertEquals(ConversationAccessMode.WRITABLE, flow.value.accessMode)
            assertEquals("gpt-test", flow.value.model)
            assertEquals("high", flow.value.reasoningEffort)
            assertEquals(null, flow.value.errorMessage)
            assertEquals(3, fixture.methods.count { it == "thread/resume" })
            fixture.repository.sendMessage("server", "thread", "continue")
            assertTrue("turn/steer" in fixture.methods)
        }
    }

    @Test
    fun automaticSyncReadsDesktopUpdatesThenSendsOnOriginalThreadWithoutForking() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            val flow = fixture.repository.openConversation("server", "thread")
            fixture.repository.loadOlderConversationTurns("server", "thread")
            val monitor = backgroundScope.launch {
                monitorExternalConversation(flow, MutableStateFlow(true), {
                    fixture.repository.syncExternalConversation("server", "thread")
                }, {})
            }
            runCurrent()
            fixture.historyPage = """{"data":[${turn("next", "inProgress")},${turn("running", "completed")},${turn("old", "completed")}],"nextCursor":"older"}"""
            advanceTimeBy(2_000); runCurrent()
            assertTrue(flow.value.isExternalWriter)
            assertEquals("next", flow.value.activeTurnId)
            assertEquals(listOf("oldest", "old", "running", "next"), flow.value.history.turns.map { it.id })
            assertEquals(2, flow.value.history.turns.last().items.size)
            fixture.resumeError = null
            advanceTimeBy(2_000); runCurrent()
            assertFalse(flow.value.isViewOnly)
            val count = fixture.requests.size
            advanceTimeBy(30_000); runCurrent()
            assertEquals(count, fixture.requests.size)
            fixture.repository.sendMessage("server", "thread", "continue in original")
            assertEquals("thread", fixture.requests.last().requiredObject("params").requiredString("threadId"))
            assertFalse("thread/fork" in fixture.methods)
            monitor.cancel()
        }
    }

    @Test
    fun automaticSyncPropagatesOtherErrorsWithoutReplacingTheSnapshotOrClaimingWriter() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            val flow = fixture.repository.openConversation("server", "thread")
            val before = flow.value
            val reads = fixture.methods.count { it == "thread/read" }
            fixture.resumeError = RpcException(-32600, "invalid resume params", null)
            try {
                fixture.repository.syncExternalConversation("server", "thread")
                fail("Expected sync error")
            } catch (error: RpcException) {
                assertEquals("invalid resume params", error.message)
            }
            assertEquals(before, flow.value)
            assertEquals(reads, fixture.methods.count { it == "thread/read" })
            assertFalse("thread/fork" in fixture.methods)
        }
    }

    @Test
    fun externalNewestTurnDetailsStayLoadedAndUpdateWhenDiskReportsInterrupted() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.isRunning = false
            fixture.historyPage = """{"data":[${turn("latest", "interrupted")},${turn("old", "completed")}],"nextCursor":null}"""
            fixture.itemPage = """{"data":[{"turnId":"latest","item":{"id":"progress","type":"agentMessage","phase":"commentary","text":"处理中"}}],"nextCursor":null}"""
            val flow = fixture.repository.openConversation("server", "thread")
            assertEquals(TurnDetailsState.Loaded, flow.value.history.turns.last().detailsState)
            assertEquals("处理中", (flow.value.history.turns.last().items.single() as ConversationItem.AgentMessage).text)
            assertEquals(TurnDetailsState.Unloaded, flow.value.history.turns.first().detailsState)
            fixture.itemPage = fixture.itemPage!!.replace("处理中", "处理进展已更新")
            fixture.repository.syncExternalConversation("server", "thread")
            assertEquals(TurnDetailsState.Loaded, flow.value.history.turns.last().detailsState)
            assertEquals("处理进展已更新", (flow.value.history.turns.last().items.single() as ConversationItem.AgentMessage).text)
            assertEquals(2, fixture.methods.count { it == "thread/items/list" })
            assertFalse("thread/fork" in fixture.methods)
        }
    }

    @Test
    fun reconnectRechecksOwnershipBeforeSendingFromPreviouslyWritableState() = runTest {
        withFixture { fixture ->
            val flow = fixture.repository.openConversation("server", "thread")
            assertFalse(flow.value.isViewOnly)
            fixture.prepareReconnect()
            fixture.resumeError = activeWriterError()
            try {
                fixture.repository.sendMessage("server", "thread", "continue")
                fail("Expected read-only rejection after reconnect")
            } catch (error: IllegalStateException) {
                assertEquals(flow.value.viewOnlyMessage, error.message)
            }
            assertTrue(flow.value.isExternalWriter)
            assertFalse("turn/start" in fixture.methods)
            assertFalse("turn/steer" in fixture.methods)
            fixture.resumeError = null
            fixture.prepareReconnect()
            fixture.repository.connect("server")
            assertFalse(flow.value.isViewOnly)
        }
    }

    @Test
    fun reopeningRetriesResumeAndDoesNotUnsubscribeExternalWriter() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.repository.openConversation("server", "thread")
            fixture.repository.releaseConversation("server", "thread")
            runCurrent()
            assertFalse("thread/unsubscribe" in fixture.methods)
            fixture.resumeError = null
            assertFalse(fixture.repository.openConversation("server", "thread").value.isViewOnly)
            assertEquals(2, fixture.methods.count { it == "thread/resume" })
        }
    }

    @Test
    fun cancelledResumeDoesNotReadOrAcquireWriter() = runTest {
        withFixture { fixture ->
            fixture.holdResume = true
            val job = launch { fixture.repository.openConversation("server", "thread") }
            runCurrent()
            job.cancel(CancellationException("test cancellation"))
            job.join()
            assertEquals(listOf("thread/resume"), fixture.methods)
        }
    }

    @Test
    fun childInputRestrictionsRemainIndependentOfWriterOwnership() {
        val child = ConversationState("server", "Server", "child", "Child", "/workspace",
            canAcceptDirectInput = false, parentThreadId = "parent")
        val external = child.copy(accessMode = ConversationAccessMode.EXTERNAL_WRITER)
        assertTrue(external.isExternalWriter)
        assertTrue(requireNotNull(external.viewOnlyMessage).contains("其他 Codex 客户端"))
        val restored = external.withRefreshedConversation(child)
        assertFalse(restored.isExternalWriter)
        assertTrue(restored.isViewOnly)
    }

    @Test
    fun companionSendsTextThroughOwnerInSameThreadAndKeepsWriteGuards() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.desktopAvailable = true
            fixture.historyPage = idlePage
            fixture.isRunning = false
            val flow = fixture.repository.openConversation("server", "thread")
            assertTrue(flow.value.canSendToDesktop)
            assertTrue(flow.value.isViewOnly)
            fixture.requests.clear()
            fixture.repository.sendDesktopMessage("server", "thread", "continue here")
            assertEquals(listOf("codexAndroid/desktop/turn/start"), fixture.methods)
            val params = fixture.requests.single().requiredObject("params")
            assertEquals("thread", params.requiredString("threadId"))
            assertEquals("continue here", params.requiredString("text"))
            assertEquals(setOf("threadId", "clientUserMessageId", "text"), params.keys)
            assertEquals("desktop-turn", flow.value.activeTurnId)
            assertEquals("thread", flow.value.threadId)
            assertTrue(flow.value.isExternalWriter)
            try {
                fixture.repository.runShellCommand("server", "thread", "pwd")
                fail("Desktop message capability must not grant Shell access")
            } catch (_: IllegalStateException) { }
            assertEquals(1, fixture.requests.size)
        }
    }

    @Test
    fun companionLossDoesNotRetryForkOrChangeConversationOnFailedSend() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.desktopAvailable = true
            fixture.historyPage = idlePage
            fixture.isRunning = false
            val flow = fixture.repository.openConversation("server", "thread")
            val before = flow.value
            fixture.desktopSendError = RpcException(-32070, "client-disconnected", null)
            fixture.requests.clear()
            try {
                fixture.repository.sendDesktopMessage("server", "thread", "hello")
                fail("Delivery failure must surface")
            } catch (error: RpcException) { assertEquals(-32070, error.code) }
            assertEquals(before, flow.value)
            assertEquals(listOf("codexAndroid/desktop/turn/start"), fixture.methods)
            fixture.desktopAvailable = false
            fixture.repository.refreshConversation("server", "thread")
            assertFalse(flow.value.canSendToDesktop)
            assertTrue(flow.value.isViewOnly)
            fixture.resumeError = null
            fixture.repository.refreshConversation("server", "thread")
            assertFalse(flow.value.isViewOnly)
            assertFalse(flow.value.desktopControlAvailable)
        }
    }

    @Test
    fun runningDesktopConversationDoesNotAcceptAnotherStart() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.desktopAvailable = true
            val flow = fixture.repository.openConversation("server", "thread")
            assertTrue(flow.value.canSendToDesktop)
            val before = fixture.requests.size
            try {
                fixture.repository.sendDesktopMessage("server", "thread", "hello")
                fail("Wait for the Desktop turn to finish")
            } catch (_: IllegalStateException) { }
            assertEquals(before, fixture.requests.size)
        }
    }

    @Test
    fun externalInProgressHistoryDisablesSendWhenReaderDoesNotReportActive() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.desktopAvailable = true
            fixture.isRunning = false
            val state = fixture.repository.openConversation("server", "thread").value
            assertEquals("running", state.activeTurnId)
            assertTrue(state.isRunning)
            val before = fixture.requests.size
            try {
                fixture.repository.sendDesktopMessage("server", "thread", "hello")
                fail("External history must gate a second start")
            } catch (_: IllegalStateException) { }
            assertEquals(before, fixture.requests.size)
        }
    }

    @Test
    fun onlyMethodNotFoundErrorsDisableOptionalCompanion() = runTest {
        for (error in listOf(RpcException(-32601, "Method not found", null),
            RpcException(-32600, "Invalid request: unknown variant `codexAndroid/desktop/discover`, expected one of ...", null))) {
            withFixture { fixture ->
                fixture.resumeError = activeWriterError()
                fixture.desktopDiscoverError = error
                val state = fixture.repository.openConversation("server", "thread").value
                assertTrue(state.isExternalWriter)
                assertFalse(state.canSendToDesktop)
            }
        }
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.desktopDiscoverError = RpcException(-32600, "Permission denied", null)
            try {
                fixture.repository.openConversation("server", "thread")
                fail("Other errors must surface")
            } catch (error: RpcException) { assertEquals("Permission denied", error.message) }
        }
    }

    @Test
    fun mismatchedDesktopConfirmationIsNotAccepted() = runTest {
        withFixture { fixture ->
            fixture.resumeError = activeWriterError()
            fixture.desktopAvailable = true
            fixture.historyPage = idlePage
            fixture.isRunning = false
            val flow = fixture.repository.openConversation("server", "thread")
            val before = flow.value
            fixture.desktopConfirmationThread = "different-thread"
            try {
                fixture.repository.sendDesktopMessage("server", "thread", "hello")
                fail("Confirmation must identify the original thread")
            } catch (_: com.hebo.codex.data.remote.ProtocolException) { }
            assertEquals(before, flow.value)
        }
    }

    @Test
    fun companionDoesNotOverrideSubAgentInputPolicy() {
        val state = ConversationState("server", "Server", "child", "Child", "",
            accessMode = ConversationAccessMode.EXTERNAL_WRITER, desktopControlAvailable = true,
            canAcceptDirectInput = false)
        assertFalse(state.canSendToDesktop)
        assertTrue(state.isViewOnly)
    }

    private suspend fun TestScope.withFixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture(this)
        try {
            fixture.connect()
            block(fixture)
        } finally {
            fixture.connection.close()
        }
    }

    private class Fixture(private val scope: TestScope) {
        val requests = mutableListOf<JsonObject>()
        val responses = mutableListOf<JsonObject>()
        val methods get() = requests.map { it.requiredString("method") }
        var resumeError: RpcException? = null
        var readError: RpcException? = null
        var desktopAvailable = false
        var desktopDiscoverError: RpcException? = null
        var desktopSendError: RpcException? = null
        var desktopConfirmationThread = "thread"
        var isRunning = true
        var holdResume = false
        var historyPage = initialPage
        var itemPage: String? = null
        var connection = createConnection()

        private fun createConnection() = AppServerConnection(
            webSocketFactory = AppServerWebSocketFactory { listener ->
                object : AppServerWebSocket {
                    override fun start() = listener.onOpen(this)
                    override fun setResponseTimeout(timeoutMillis: Long) = Unit
                    override fun send(text: String): Boolean {
                        val request = json(text)
                        if (request["method"] == null) {
                            responses += request
                            return true
                        }
                        val method = request.requiredString("method")
                        if (method == "initialized") return true
                        if (method != "initialize") requests += request
                        if (method == "thread/resume" && holdResume) return true
                        val error = when (method) {
                            "thread/resume" -> resumeError
                            "thread/read" -> readError
                            "codexAndroid/desktop/discover" -> desktopDiscoverError
                            "codexAndroid/desktop/turn/start" -> desktopSendError
                            else -> null
                        }
                        val response = buildJsonObject {
                            put("id", requireNotNull(request["id"]))
                            if (error != null) put("error", buildJsonObject {
                                put("code", error.code)
                                put("message", error.message)
                            }) else put("result", result(method, request["params"] as? JsonObject))
                        }
                        listener.onMessage(this, response.toString())
                        return true
                    }
                    override fun close(code: Int, reason: String): Boolean = true
                    override fun cancel() = Unit
                }
            },
            json = Json,
            connectTimeoutMillis = 1_000,
            parentContext = scope.backgroundScope.coroutineContext,
        )
        private val server = ServerProfile("server", "Server", "ws://test:1234", ServerAuthentication.CAPABILITY_TOKEN)
        private val store = Mockito.mock(ServerStore::class.java)
        private val factory = Mockito.mock(AppServerClientFactory::class.java)
        val repository: CodexRepository

        init {
            Mockito.`when`(store.settings).thenReturn(flowOf(ServerSettings(listOf(server), emptyList(), "server")))
            repository = CodexRepository(store, factory, scope.backgroundScope)
        }

        suspend fun prepareReconnect() {
            scope.runCurrent()
            connection.close()
            scope.runCurrent()
            connection = createConnection()
            stubConnection()
        }

        private suspend fun stubConnection() {
            Mockito.`when`(store.readToken("server")).thenReturn("test-token")
            Mockito.`when`(factory.create(server.webSocketUrl, "test-token", null, scope.backgroundScope.coroutineContext))
                .thenReturn(connection)
        }

        suspend fun connect() {
            stubConnection()
            repository.connect("server")
            scope.runCurrent()
            requests.clear()
        }

        private fun result(method: String, params: JsonObject?): JsonObject = when (method) {
            "initialize" -> json("""{"userAgent":"test","codexHome":"/tmp/codex","platformFamily":"unix","platformOs":"linux"}""")
            "thread/read" -> json("""{"thread":${fixtureThread("thread")}}""")
            "thread/resume" -> json("""{"thread":${fixtureThread(params!!.requiredString("threadId"))},"model":"gpt-test","reasoningEffort":"high","initialTurnsPage":$historyPage}""")
            "thread/fork" -> json("""{"thread":${thread("fork")}}""")
            "codexAndroid/desktop/discover" -> json("""{"threadId":"thread","protocolVersion":1,"available":$desktopAvailable}""")
            "codexAndroid/desktop/turn/start" -> json("""{"threadId":"$desktopConfirmationThread","clientUserMessageId":"${params!!.requiredString("clientUserMessageId")}","turn":{"id":"desktop-turn","status":"inProgress","items":[]}}""")
            "thread/turns/list" -> if (params?.get("cursor") != null) json("""{"data":[${turn("oldest", "completed")}],"nextCursor":null}""") else json(historyPage)
            "thread/items/list" -> itemPage?.let(::json) ?: run {
                val turnId = params!!.requiredString("turnId")
                val isFirst = params["cursor"] == null
                json("""{"data":[{"turnId":"$turnId","item":{"id":"answer-${if (isFirst) 1 else 2}","type":"agentMessage","text":"history"}}],"nextCursor":${if (isFirst) "\"next-items\"" else "null"}}""")
            }
            "turn/steer" -> json("""{"turnId":"running"}""")
            else -> json("""{"data":[],"nextCursor":null}""")
        }

        private fun fixtureThread(id: String) = thread(id).let {
            if (isRunning) it else it.replace("\"type\":\"active\",\"activeFlags\":[]", "\"type\":\"idle\"")
        }
    }

    companion object {
        private fun activeWriterError() = RpcException(-32600, "thread-store conflict: thread thread already has an active writer", null)
        private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
        private fun thread(id: String) = """{"id":"$id","sessionId":"session","name":"Test","preview":"history","cwd":"/workspace","createdAt":1,"updatedAt":2,"status":{"type":"active","activeFlags":[]},"historyMode":"paginated","canAcceptDirectInput":true}"""
        private fun turn(id: String, status: String) = """{"id":"$id","status":"$status","itemsView":"summary","items":[]}"""
        private val initialPage = """{"data":[${turn("running", "inProgress")},${turn("old", "completed")}],"nextCursor":"older"}"""
        private val idlePage = """{"data":[${turn("old", "completed")}],"nextCursor":null}"""
    }
}
