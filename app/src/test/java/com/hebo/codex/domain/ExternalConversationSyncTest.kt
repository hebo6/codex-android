package com.hebo.codex.domain

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExternalConversationSyncTest {
    @Test
    fun externalChatUsesFastSnapshotsUntilWriterIsReleasedThenStopsPolling() = runTest {
        val conversation = MutableStateFlow(state(running = true))
        val connected = MutableStateFlow(true)
        var count = 0
        val job = backgroundScope.launch {
            monitorExternalConversation(conversation, connected, {
                count++
                if (count == 2) conversation.value = conversation.value.copy(accessMode = ConversationAccessMode.WRITABLE)
            }, {})
        }
        runCurrent()
        advanceTimeBy(1_999); runCurrent()
        assertEquals(0, count)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, count)
        advanceTimeBy(2_000); runCurrent()
        assertEquals(2, count)
        assertEquals("original", conversation.value.threadId)
        assertFalse(conversation.value.isViewOnly)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(2, count)
        job.cancel()
    }

    @Test
    fun idleExternalChatUsesSlowerSnapshotsAndDoesNotPollWhileDisconnected() = runTest {
        val conversation = MutableStateFlow(state())
        val connected = MutableStateFlow(false)
        var count = 0
        val job = backgroundScope.launch { monitorExternalConversation(conversation, connected, { count++ }, {}) }
        runCurrent()
        advanceTimeBy(30_000); runCurrent()
        assertEquals(0, count)
        connected.value = true
        runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertEquals(1, count)
        connected.value = false
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, count)
        job.cancel()
    }

    @Test
    fun writableAndSubAgentChatsDoNotPoll() = runTest {
        val conversation = MutableStateFlow(state().copy(accessMode = ConversationAccessMode.WRITABLE))
        val connected = MutableStateFlow(true)
        var count = 0
        val job = backgroundScope.launch { monitorExternalConversation(conversation, connected, { count++ }, {}) }
        runCurrent()
        advanceTimeBy(30_000); runCurrent()
        conversation.value = conversation.value.copy(canAcceptDirectInput = false)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(0, count)
        job.cancel()
    }

    @Test
    fun errorsAreReportedAndBackOffWhileSuccessfulSyncClearsError() = runTest {
        val conversation = MutableStateFlow(state(running = true))
        val connected = MutableStateFlow(true)
        val attempts = mutableListOf<Long>()
        val errors = mutableListOf<Throwable?>()
        val error = IllegalStateException("invalid params")
        val job = backgroundScope.launch {
            monitorExternalConversation(conversation, connected, {
                attempts += testScheduler.currentTime
                if (attempts.size <= 2) throw error
            }, { errors += it })
        }
        runCurrent()
        for (interval in listOf(2_000L, 5_000L, 10_000L, 2_000L)) {
            advanceTimeBy(interval); runCurrent()
        }
        assertEquals(listOf(2_000L, 7_000L, 17_000L, 19_000L), attempts)
        assertEquals(error, errors[0])
        assertEquals(error, errors[1])
        assertNull(errors[2])
        job.cancel()
    }

    @Test
    fun manualRecoveryDuringDelayPreventsAnExtraResume() = runTest {
        val conversation = MutableStateFlow(state())
        var count = 0
        val job = backgroundScope.launch {
            monitorExternalConversation(conversation, MutableStateFlow(true), { count++ }, {})
        }
        runCurrent()
        conversation.value = conversation.value.copy(accessMode = ConversationAccessMode.WRITABLE)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(0, count)
        job.cancel()
    }

    @Test
    fun leavingScreenCancelsInFlightReadAndDoesNotReportCancellationAsAnError() = runTest {
        var started = false
        var cancelled = false
        val errors = mutableListOf<Throwable?>()
        val job = backgroundScope.launch {
            monitorExternalConversation(MutableStateFlow(state()), MutableStateFlow(true), {
                started = true
                try { awaitCancellation() } finally { cancelled = true }
            }, { errors += it })
        }
        runCurrent()
        advanceTimeBy(5_000); runCurrent()
        assertTrue(started)
        job.cancel()
        runCurrent()
        assertTrue(cancelled)
        assertTrue(errors.isEmpty())
    }

    private fun state(running: Boolean = false) = ConversationState(
        serverId = "server", serverName = "Server", threadId = "original", title = "Original", cwd = "/workspace",
        isRunning = running, accessMode = ConversationAccessMode.EXTERNAL_WRITER,
    )
}
