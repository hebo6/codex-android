package com.hebo.codex.domain

import com.hebo.codex.data.remote.AppServerApi
import com.hebo.codex.data.remote.ProtocolMapper
import com.hebo.codex.data.remote.RpcException
import com.hebo.codex.data.remote.ThreadSnapshot

internal data class ConversationResumeResult(
    val snapshot: ThreadSnapshot,
    val accessMode: ConversationAccessMode,
    val desktopControlAvailable: Boolean = false,
)

internal suspend fun resumeOrReadConversation(
    api: AppServerApi,
    threadId: String,
    loadItems: suspend (String) -> List<ConversationItem>,
): ConversationResumeResult {
    // Only a rejected resume may select the external-writer path. Read, paging and
    // mapping failures must still surface as errors rather than changing ownership.
    val resumed = try {
        api.resumeThread(threadId)
    } catch (error: RpcException) {
        if (error.code != -32600 || !error.message.contains("already has an active writer")) throw error
        null
    }
    val accessMode: ConversationAccessMode
    val snapshot: ThreadSnapshot
    if (resumed != null) {
        accessMode = ConversationAccessMode.WRITABLE
        snapshot = ProtocolMapper.resumedThreadSnapshot(resumed)
    } else {
        accessMode = ConversationAccessMode.EXTERNAL_WRITER
        val metadata = ProtocolMapper.threadMetadata(api.readThread(threadId))
        val page = ProtocolMapper.threadTurnPage(api.listThreadTurns(threadId))
        snapshot = metadata.copy(turns = page.data, olderTurnsCursor = page.nextCursor)
    }
    return ConversationResumeResult(
        snapshot = snapshot.copy(turns = loadResumedTurns(
            snapshot.turns,
            loadNewestDetails = accessMode == ConversationAccessMode.EXTERNAL_WRITER,
            loadItems = loadItems,
        )),
        accessMode = accessMode,
        desktopControlAvailable = accessMode == ConversationAccessMode.EXTERNAL_WRITER &&
            snapshot.summary.canAcceptDirectInput != false && api.discoverDesktopControl(threadId),
    )
}

internal fun ConversationState.requireWritable() {
    check(!isViewOnly) { viewOnlyMessage ?: "此会话当前仅可查看" }
}
