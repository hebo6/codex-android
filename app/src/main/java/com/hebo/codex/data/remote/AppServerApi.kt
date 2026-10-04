package com.hebo.codex.data.remote

import com.hebo.codex.domain.ReviewDelivery
import com.hebo.codex.domain.ReviewTarget
import com.hebo.codex.domain.RemoteFileMetadata
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.SortDirection
import com.hebo.codex.domain.ThreadSortKey
import com.hebo.codex.domain.ThreadSourceKind
import com.hebo.codex.domain.ThreadOptions
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.OutputStream
import java.util.Base64

enum class StableRpcMethod(val wireName: String) {
    THREAD_READ("thread/read"),
    THREAD_FORK("thread/fork"),
    THREAD_ARCHIVE("thread/archive"),
    THREAD_DELETE("thread/delete"),
    THREAD_UNSUBSCRIBE("thread/unsubscribe"),
    THREAD_NAME_SET("thread/name/set"),
    THREAD_GOAL_SET("thread/goal/set"),
    THREAD_GOAL_GET("thread/goal/get"),
    THREAD_GOAL_CLEAR("thread/goal/clear"),
    THREAD_UNARCHIVE("thread/unarchive"),
    THREAD_COMPACT("thread/compact/start"),
    THREAD_SHELL_COMMAND("thread/shellCommand"),
    SKILLS_LIST("skills/list"),
    SKILLS_EXTRA_ROOTS_SET("skills/extraRoots/set"),
    SKILLS_CONFIG_WRITE("skills/config/write"),
    HOOKS_LIST("hooks/list"),
    MARKETPLACE_ADD("marketplace/add"),
    MARKETPLACE_REMOVE("marketplace/remove"),
    MARKETPLACE_UPGRADE("marketplace/upgrade"),
    PLUGIN_LIST("plugin/list"),
    PLUGIN_INSTALLED("plugin/installed"),
    PLUGIN_READ("plugin/read"),
    PLUGIN_SKILL_READ("plugin/skill/read"),
    PLUGIN_SHARE_SAVE("plugin/share/save"),
    PLUGIN_SHARE_UPDATE_TARGETS("plugin/share/updateTargets"),
    PLUGIN_SHARE_LIST("plugin/share/list"),
    PLUGIN_SHARE_CHECKOUT("plugin/share/checkout"),
    PLUGIN_SHARE_DELETE("plugin/share/delete"),
    PLUGIN_INSTALL("plugin/install"),
    PLUGIN_UNINSTALL("plugin/uninstall"),
    APP_LIST("app/list"),
    FS_GET_METADATA("fs/getMetadata"),
    FS_READ_FILE("fs/readFile"),
    REVIEW_START("review/start"),
    MODEL_LIST("model/list"),
    MODEL_PROVIDER_CAPABILITIES("modelProvider/capabilities/read"),
    PERMISSION_PROFILE_LIST("permissionProfile/list"),
    MCP_OAUTH_LOGIN("mcpServer/oauth/login"),
    MCP_CONFIG_RELOAD("config/mcpServer/reload"),
    MCP_STATUS_LIST("mcpServerStatus/list"),
    MCP_RESOURCE_READ("mcpServer/resource/read"),
    MCP_TOOL_CALL("mcpServer/tool/call"),
    ACCOUNT_LOGIN_START("account/login/start"),
    ACCOUNT_LOGIN_CANCEL("account/login/cancel"),
    ACCOUNT_LOGOUT("account/logout"),
    ACCOUNT_RATE_LIMITS("account/rateLimits/read"),
    ACCOUNT_RATE_LIMIT_RESET("account/rateLimitResetCredit/consume"),
    ACCOUNT_USAGE("account/usage/read"),
    ACCOUNT_WORKSPACE_MESSAGES("account/workspaceMessages/read"),
    ACCOUNT_SEND_CREDITS_NUDGE("account/sendAddCreditsNudgeEmail"),
    ACCOUNT_READ("account/read"),
    FEEDBACK_UPLOAD("feedback/upload"),
    CONFIG_READ("config/read"),
    CONFIG_VALUE_WRITE("config/value/write"),
    CONFIG_BATCH_WRITE("config/batchWrite"),
    CONFIG_REQUIREMENTS_READ("configRequirements/read"),
}

class AppServerApi(
    private val connection: AppServerConnection,
) {
    suspend fun listThreads(
        cursor: String? = null,
        cwd: String? = null,
        archived: Boolean = false,
        searchTerm: String? = null,
        sortKey: ThreadSortKey = ThreadSortKey.RECENCY_AT,
        sortDirection: SortDirection = SortDirection.DESC,
        modelProviders: List<String> = emptyList(),
        sourceKinds: Set<ThreadSourceKind> = emptySet(),
        limit: Int = THREAD_PAGE_LIMIT,
        ancestorThreadId: String? = null,
    ): JsonObject = connection.request(
        "thread/list",
        buildJsonObject {
            put("limit", limit)
            put("sortKey", sortKey.wireValue)
            put("sortDirection", sortDirection.wireValue)
            cursor?.let { put("cursor", it) }
            ancestorThreadId?.let { put("ancestorThreadId", it) }
            cwd?.let { put("cwd", it) }
            if (archived) put("archived", true)
            searchTerm?.takeIf(String::isNotBlank)?.let { put("searchTerm", it) }
            if (modelProviders.isNotEmpty()) {
                put("modelProviders", buildJsonArray { modelProviders.forEach { add(JsonPrimitive(it)) } })
            }
            if (sourceKinds.isNotEmpty()) {
                put("sourceKinds", buildJsonArray { sourceKinds.forEach { add(JsonPrimitive(it.wireValue)) } })
            }
        },
    ).jsonObject

    suspend fun startThread(cwd: String, options: ThreadOptions = ThreadOptions()): JsonObject = connection.request(
        "thread/start",
        buildJsonObject {
            put("cwd", cwd)
            put("serviceName", CLIENT_NAME)
            put("historyMode", "paginated")
            options.model?.let { put("model", it) }
            options.modelProvider?.let { put("modelProvider", it) }
            options.serviceTier?.let { put("serviceTier", it) }
            options.approvalPolicy?.let { put("approvalPolicy", it.wireValue) }
            options.approvalsReviewer?.let { put("approvalsReviewer", it.wireValue) }
            options.sandboxMode?.let { put("sandbox", it.wireValue) }
            options.configJson?.takeIf(String::isNotBlank)?.let {
                val config = Json.parseToJsonElement(it) as? JsonObject
                    ?: throw IllegalArgumentException("线程配置必须是 JSON 对象")
                put("config", config)
            }
            options.baseInstructions?.takeIf(String::isNotBlank)?.let { put("baseInstructions", it) }
            options.developerInstructions?.takeIf(String::isNotBlank)?.let { put("developerInstructions", it) }
            options.personality?.let { put("personality", it.wireValue) }
            if (options.ephemeral) put("ephemeral", true)
        },
    ).jsonObject

    suspend fun resumeThread(threadId: String): JsonObject = connection.request(
        "thread/resume",
        buildJsonObject {
            put("threadId", threadId)
            put("excludeTurns", true)
            put(
                "initialTurnsPage",
                buildJsonObject {
                    put("limit", THREAD_TURN_PAGE_LIMIT)
                    put("sortDirection", "desc")
                    put("itemsView", "summary")
                },
            )
        },
    ).jsonObject

    suspend fun readThread(threadId: String): JsonObject = request(
        StableRpcMethod.THREAD_READ,
        buildJsonObject {
            put("threadId", threadId)
            put("includeTurns", false)
        },
    ).jsonObject

    suspend fun listThreadTurns(
        threadId: String,
        cursor: String? = null,
        limit: Int = THREAD_TURN_PAGE_LIMIT,
    ): JsonObject = connection.request(
        "thread/turns/list",
        buildJsonObject {
            put("threadId", threadId)
            cursor?.let { put("cursor", it) }
            put("limit", limit)
            put("sortDirection", "desc")
            put("itemsView", "summary")
        },
    ).jsonObject

    suspend fun listThreadItems(
        threadId: String,
        turnId: String,
        cursor: String? = null,
    ): JsonObject = connection.request(
        "thread/items/list",
        buildJsonObject {
            put("threadId", threadId)
            put("turnId", turnId)
            put("limit", THREAD_ITEM_PAGE_LIMIT)
            put("sortDirection", "asc")
            cursor?.let { put("cursor", it) }
        },
    ).jsonObject

    suspend fun forkThread(threadId: String, lastTurnId: String? = null): JsonObject = request(
        StableRpcMethod.THREAD_FORK,
        buildJsonObject {
            put("threadId", threadId)
            lastTurnId?.let { put("lastTurnId", it) }
            put("excludeTurns", true)
        },
    ).jsonObject

    suspend fun startTurn(
        threadId: String,
        clientMessageId: String,
        input: List<UserInputPart>,
        options: TurnOptions = TurnOptions(),
    ): JsonObject = connection.request(
        "turn/start",
        buildTurnParams(threadId, clientMessageId, input, options),
        timeoutMillis = input.submissionTimeoutMillis(),
    ).jsonObject

    // Optional codex-android companion extension, separate from Codex CLI RPCs.
    suspend fun discoverDesktopControl(threadId: String): Boolean = try {
        val result = connection.request(
            "codexAndroid/desktop/discover",
            buildJsonObject { put("threadId", threadId) },
        ).jsonObject
        if (result.requiredString("threadId") != threadId || result.requiredInt("protocolVersion") != 1) {
            throw ProtocolException("电脑伴随服务的会话或协议不匹配")
        }
        result.requiredBoolean("available")
    } catch (error: RpcException) {
        val unsupported = error.code == -32601 || (error.code == -32600 &&
            error.message.startsWith("Invalid request: unknown variant `codexAndroid/desktop/discover`"))
        if (!unsupported) throw error
        false
    }

    suspend fun sendDesktopMessage(threadId: String, clientMessageId: String, text: String): JsonObject =
        connection.request(
            "codexAndroid/desktop/turn/start",
            buildJsonObject {
                put("threadId", threadId)
                put("clientUserMessageId", clientMessageId)
                put("text", text)
            },
            timeoutMillis = 120_000,
        ).jsonObject.also { result ->
            if (result.requiredString("threadId") != threadId ||
                result.requiredString("clientUserMessageId") != clientMessageId
            ) throw ProtocolException("电脑端的发送确认与当前消息不一致")
            result.requiredObject("turn").requiredString("id")
        }

    suspend fun steerTurn(
        threadId: String,
        turnId: String,
        clientMessageId: String,
        input: List<UserInputPart>,
    ): JsonObject = connection.request(
        "turn/steer",
        buildJsonObject {
            put("threadId", threadId)
            put("expectedTurnId", turnId)
            put("clientUserMessageId", clientMessageId)
            put("input", input.toWire())
        },
        timeoutMillis = input.submissionTimeoutMillis(),
    ).jsonObject

    suspend fun interruptTurn(threadId: String, turnId: String) {
        connection.request(
            "turn/interrupt",
            buildJsonObject {
                put("threadId", threadId)
                put("turnId", turnId)
            },
        )
    }

    suspend fun runShellCommand(threadId: String, command: String) {
        request(
            StableRpcMethod.THREAD_SHELL_COMMAND,
            buildJsonObject {
                put("threadId", threadId)
                put("command", command)
            },
        )
    }

    suspend fun listBackgroundTerminals(
        threadId: String,
        cursor: String? = null,
    ): JsonObject = connection.request(
        "thread/backgroundTerminals/list",
        buildJsonObject {
            put("threadId", threadId)
            put("limit", BACKGROUND_TERMINAL_PAGE_LIMIT)
            cursor?.let { put("cursor", it) }
        },
    ).jsonObject

    suspend fun queueInput(threadId: String, clientUserMessageId: String, input: List<UserInputPart>): JsonObject =
        connection.request(
            "thread/queue/add",
            buildJsonObject {
                put("threadId", threadId)
                put("clientUserMessageId", clientUserMessageId)
                put("input", input.toWire())
            },
            timeoutMillis = input.submissionTimeoutMillis(),
        ).jsonObject.also { response ->
            val submission = response.requiredObject("queuedSubmission")
            submission.requiredString("id")
            if (submission.requiredString("clientUserMessageId") != clientUserMessageId) {
                throw ProtocolException("排队确认与提交的消息不一致")
            }
            submission.requiredArray("input").forEach { userInputPart(it.jsonObject) }
        }

    suspend fun deleteQueuedInput(threadId: String, queuedSubmissionId: String): Boolean = connection.request(
        "thread/queue/delete",
        buildJsonObject {
            put("threadId", threadId)
            put("queuedSubmissionId", queuedSubmissionId)
        },
    ).jsonObject.requiredBoolean("deleted")

    suspend fun listQueuedInputs(threadId: String, cursor: String? = null): JsonObject = connection.request(
        "thread/queue/list",
        buildJsonObject {
            put("threadId", threadId)
            put("limit", THREAD_PAGE_LIMIT)
            cursor?.let { put("cursor", it) }
        },
    ).jsonObject

    suspend fun terminateBackgroundTerminal(threadId: String, processId: String): Boolean =
        connection.request(
            "thread/backgroundTerminals/terminate",
            buildJsonObject {
                put("threadId", threadId)
                put("processId", processId)
            },
        ).jsonObject.requiredBoolean("terminated")

    suspend fun review(
        threadId: String,
        target: ReviewTarget,
        delivery: ReviewDelivery,
    ): JsonObject = request(
        StableRpcMethod.REVIEW_START,
        buildJsonObject {
            put("threadId", threadId)
            put("delivery", delivery.wireValue)
            put("target", target.toWire())
        },
    ).jsonObject

    suspend fun request(method: StableRpcMethod, params: JsonObject? = null): JsonElement =
        connection.request(method.wireName, params)

    suspend fun getFileMetadata(path: String): RemoteFileMetadata {
        val response = request(
            StableRpcMethod.FS_GET_METADATA,
            buildJsonObject { put("path", path) },
        ).jsonObject
        return RemoteFileMetadata(
            isDirectory = response.requiredBoolean("isDirectory"),
            isFile = response.requiredBoolean("isFile"),
            isSymlink = response.requiredBoolean("isSymlink"),
            createdAtMillis = response.requiredLong("createdAtMs"),
            modifiedAtMillis = response.requiredLong("modifiedAtMs"),
        )
    }

    suspend fun readFilePreview(path: String): ByteArray {
        val encoded = readFileBase64(path)
        if (encoded.length > MAX_REMOTE_PREVIEW_BASE64_LENGTH) {
            throw ProtocolException(
                "文件超过 $MAX_REMOTE_PREVIEW_MEBIBYTES MiB 内置预览限制，仍可保存或使用其他应用打开",
            )
        }
        return try {
            Base64.getDecoder().decode(encoded)
        } catch (error: IllegalArgumentException) {
            throw ProtocolException("fs/readFile 返回了无效的 Base64 内容", error)
        }
    }

    suspend fun downloadFile(path: String, output: OutputStream): Long {
        val encoded = readFileBase64(path)
        if (encoded.length > MAX_REMOTE_DOWNLOAD_BASE64_LENGTH) {
            throw ProtocolException(
                "文件超过客户端 $MAX_REMOTE_DOWNLOAD_MEBIBYTES MiB 保存/打开限制",
            )
        }
        return withContext(Dispatchers.IO) { decodeRemoteFileBase64(encoded, output) }
    }

    private suspend fun readFileBase64(path: String): String = request(
        StableRpcMethod.FS_READ_FILE,
        buildJsonObject { put("path", path) },
    ).jsonObject.requiredString("dataBase64")

    private fun buildTurnParams(
        threadId: String,
        clientMessageId: String,
        input: List<UserInputPart>,
        options: TurnOptions,
    ): JsonObject = buildJsonObject {
        put("threadId", threadId)
        put("clientUserMessageId", clientMessageId)
        put("input", input.toWire())
        options.cwd?.takeIf(String::isNotBlank)?.let { put("cwd", it) }
        options.model?.let { put("model", it) }
        options.serviceTier?.let { put("serviceTier", it) }
        options.reasoningEffort?.let { put("effort", it) }
        options.reasoningSummary?.let { put("summary", it.wireValue) }
        options.personality?.let { put("personality", it.wireValue) }
        options.approvalPolicy?.let { put("approvalPolicy", it.wireValue) }
        options.approvalsReviewer?.let { put("approvalsReviewer", it.wireValue) }
        options.sandboxMode?.let { put("sandboxPolicy", options.toSandboxPolicyWire(it)) }
        options.outputSchema?.takeIf(String::isNotBlank)?.let {
            put("outputSchema", Json.parseToJsonElement(it))
        }
    }

    private fun TurnOptions.toSandboxPolicyWire(mode: SandboxMode): JsonObject = buildJsonObject {
        when (mode) {
            SandboxMode.READ_ONLY -> {
                put("type", "readOnly")
                put("networkAccess", sandboxNetworkAccess)
            }

            SandboxMode.WORKSPACE_WRITE -> {
                put("type", "workspaceWrite")
                put(
                    "writableRoots",
                    buildJsonArray { sandboxWritableRoots.forEach { add(JsonPrimitive(it)) } },
                )
                put("networkAccess", sandboxNetworkAccess)
                put("excludeTmpdirEnvVar", sandboxExcludeTmpdirEnvVar)
                put("excludeSlashTmp", sandboxExcludeSlashTmp)
            }

            SandboxMode.DANGER_FULL_ACCESS -> put("type", "dangerFullAccess")
        }
    }

    private fun ReviewTarget.toWire(): JsonObject = buildJsonObject {
        when (this@toWire) {
            ReviewTarget.UncommittedChanges -> put("type", "uncommittedChanges")
            is ReviewTarget.BaseBranch -> {
                put("type", "baseBranch")
                put("branch", branch)
            }

            is ReviewTarget.Commit -> {
                put("type", "commit")
                put("sha", sha)
                title?.let { put("title", it) }
            }

            is ReviewTarget.Custom -> {
                put("type", "custom")
                put("instructions", instructions)
            }
        }
    }

    private companion object {
        const val THREAD_PAGE_LIMIT = 50
        const val THREAD_TURN_PAGE_LIMIT = 25
        const val THREAD_ITEM_PAGE_LIMIT = 100
        const val BACKGROUND_TERMINAL_PAGE_LIMIT = 100
        const val CLIENT_NAME = "codex_android"
        private const val MAX_REMOTE_PREVIEW_MEBIBYTES = 16
        private const val MAX_REMOTE_PREVIEW_BYTES = MAX_REMOTE_PREVIEW_MEBIBYTES * 1024 * 1024
        private const val MAX_REMOTE_PREVIEW_BASE64_LENGTH = ((MAX_REMOTE_PREVIEW_BYTES + 2) / 3) * 4
        private const val MAX_REMOTE_DOWNLOAD_MEBIBYTES = 256
        private const val MAX_REMOTE_DOWNLOAD_BYTES = MAX_REMOTE_DOWNLOAD_MEBIBYTES * 1024 * 1024
        private const val MAX_REMOTE_DOWNLOAD_BASE64_LENGTH = ((MAX_REMOTE_DOWNLOAD_BYTES + 2) / 3) * 4
    }
}

private fun List<UserInputPart>.submissionTimeoutMillis(): Long {
    val hasEmbeddedMedia = any { part ->
        when (part) {
            is UserInputPart.Image -> part.url.startsWith("data:")
            is UserInputPart.Audio -> part.url.startsWith("data:")
            else -> false
        }
    }
    return if (hasEmbeddedMedia) MEDIA_SUBMISSION_TIMEOUT_MILLIS else DEFAULT_REQUEST_TIMEOUT_MILLIS
}

internal fun decodeRemoteFileBase64(encoded: String, output: OutputStream): Long {
    var offset = 0
    var written = 0L
    try {
        while (offset < encoded.length) {
            val end = minOf(offset + BASE64_DECODE_CHUNK_CHARACTERS, encoded.length)
            val decoded = Base64.getDecoder().decode(encoded.substring(offset, end))
            output.write(decoded)
            written += decoded.size
            offset = end
        }
    } catch (error: IllegalArgumentException) {
        throw ProtocolException("fs/readFile 返回了无效的 Base64 内容", error)
    }
    return written
}

private const val BASE64_DECODE_CHUNK_CHARACTERS = 32 * 1024
private const val MEDIA_SUBMISSION_TIMEOUT_MILLIS = 10 * 60_000L
