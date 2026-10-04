package com.hebo.codex.domain

import com.hebo.codex.util.runSuspendCatching
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/** Runs only while the chat screen is resumed. Snapshots converge on the same
 * thread; a successful resume switches back to its normal notification stream.
 * This never forks, sends input, or forces an existing writer to detach. */
internal suspend fun monitorExternalConversation(
    conversation: StateFlow<ConversationState>,
    connected: Flow<Boolean>,
    refresh: suspend () -> Unit,
    onError: (Throwable?) -> Unit,
) {
    val targets = combine(conversation, connected) { state, online -> state to online }
    var retryDelayMillis = 0L
    while (currentCoroutineContext().isActive) {
        val (state) = targets.first { (state, online) -> state.isExternalWriter && online }
        delay(retryDelayMillis.takeIf { it > 0 } ?: if (state.isRunning) 2_000L else 5_000L)
        val (latest, online) = targets.first()
        if (!latest.isExternalWriter || !online) {
            retryDelayMillis = 0
            continue
        }
        val error = runSuspendCatching { refresh() }.exceptionOrNull()
        onError(error)
        retryDelayMillis = if (error == null) 0 else (retryDelayMillis * 2).coerceIn(5_000L, 30_000L)
    }
}
