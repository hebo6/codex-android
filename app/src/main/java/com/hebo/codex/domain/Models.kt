package com.hebo.codex.domain

data class ServerProfile(
    val id: String,
    val name: String,
    val webSocketUrl: String,
    val authentication: ServerAuthentication,
    val proxyId: String? = null,
)

data class ServerConnectionDetails(
    val profile: ServerProfile,
    val token: String,
)

enum class ServerAuthentication {
    CAPABILITY_TOKEN,
}

data class ProxyProfile(
    val id: String,
    val name: String,
    val version: Long,
    val configuration: ProxyConfiguration,
    val credentialConfigured: Boolean,
    val sshHostKey: SshHostKeyRecord? = null,
    val lastTest: ProxyLastTest? = null,
    val referencedServerCount: Int = 0,
)

sealed interface ProxyConfiguration {
    data class HttpConnect(
        val url: String,
        val authentication: HttpProxyAuthentication,
        val username: String? = null,
        val headers: Map<String, String> = emptyMap(),
        val connectTimeoutMillis: Long = DEFAULT_PROXY_CONNECT_TIMEOUT_MILLIS,
        val tlsPolicy: ProxyTlsPolicy = ProxyTlsPolicy.STRICT,
    ) : ProxyConfiguration

    data class Socks5(
        val host: String,
        val port: Int,
        val authentication: Socks5Authentication,
        val username: String? = null,
        val dnsResolution: Socks5DnsResolution = Socks5DnsResolution.PROXY,
        val connectTimeoutMillis: Long = DEFAULT_PROXY_CONNECT_TIMEOUT_MILLIS,
    ) : ProxyConfiguration

    data class Ssh(
        val host: String,
        val port: Int = 22,
        val username: String,
        val authentication: SshAuthentication,
        val privateKeyUri: String? = null,
        val connectTimeoutMillis: Long = DEFAULT_PROXY_CONNECT_TIMEOUT_MILLIS,
        val keepAliveIntervalMillis: Int = DEFAULT_SSH_KEEP_ALIVE_INTERVAL_MILLIS,
        val keepAliveMaxFailures: Int = DEFAULT_SSH_KEEP_ALIVE_MAX_FAILURES,
    ) : ProxyConfiguration
}

enum class HttpProxyAuthentication {
    NONE,
    BASIC,
    BEARER,
}

enum class Socks5Authentication {
    NONE,
    USERNAME_PASSWORD,
}

enum class Socks5DnsResolution {
    PROXY,
    LOCAL,
}

enum class SshAuthentication {
    PRIVATE_KEY,
    PASSWORD,
}

enum class ProxyTlsPolicy {
    STRICT,
    ALLOW_INVALID_CERTIFICATE,
}

data class SshHostKeyRecord(
    val host: String,
    val port: Int,
    val algorithm: String,
    val sha256Fingerprint: String,
    val confirmedAtMillis: Long,
)

data class ProxyLastTest(
    val succeeded: Boolean,
    val testedAtMillis: Long,
)

sealed interface ProxyTestResult {
    data object Succeeded : ProxyTestResult

    data class HostKeyConfirmationRequired(
        val observed: SshHostKeyRecord,
        val previous: SshHostKeyRecord?,
    ) : ProxyTestResult
}

const val DEFAULT_PROXY_CONNECT_TIMEOUT_MILLIS = 10_000L
const val DEFAULT_SSH_KEEP_ALIVE_INTERVAL_MILLIS = 20_000
const val DEFAULT_SSH_KEEP_ALIVE_MAX_FAILURES = 3

sealed interface ConnectionStatus {
    data object Disconnected : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Failed(val error: UserFacingError) : ConnectionStatus
}

data class UserFacingError(
    val summary: String,
    val technicalDetails: String? = null,
)

data class ThreadSummary(
    val id: String,
    val sessionId: String,
    val title: String,
    val preview: String,
    val cwd: String,
    val createdAt: Long,
    val updatedAt: Long,
    val recencyAt: Long?,
    val status: ThreadRuntimeStatus,
    val historyMode: ThreadHistoryMode,
    val parentThreadId: String? = null,
    val forkedFromId: String? = null,
    val ephemeral: Boolean = false,
    val modelProvider: String = "",
    val source: String = "",
    val agentNickname: String? = null,
    val agentRole: String? = null,
    val canAcceptDirectInput: Boolean? = null,
    val agentPath: String? = null,
    val activeFlags: Set<ThreadActiveFlag> = emptySet(),
)

enum class ThreadHistoryMode {
    LEGACY,
    PAGINATED,
}

data class ThreadPage(
    val data: List<ThreadSummary>,
    val nextCursor: String?,
)

enum class ThreadRuntimeStatus {
    NOT_LOADED,
    IDLE,
    ACTIVE,
    SYSTEM_ERROR,
}

enum class ThreadActiveFlag {
    WAITING_APPROVAL,
    WAITING_INPUT,
}

enum class ThreadAttention {
    RUNNING,
    NEEDS_INPUT,
    READY,
    FAILED,
}

data class ThreadAttentionState(
    val attention: ThreadAttention,
    val revision: Long,
)

data class ConversationNotification(
    val serverId: String,
    val threadId: String,
    val title: String,
    val message: String,
    val attention: ThreadAttention,
)

enum class ThreadSortKey(val wireValue: String) {
    CREATED_AT("created_at"),
    UPDATED_AT("updated_at"),
    RECENCY_AT("recency_at"),
}

enum class SortDirection(val wireValue: String) {
    ASC("asc"),
    DESC("desc"),
}

enum class ThreadSourceKind(val wireValue: String) {
    CLI("cli"),
    VSCODE("vscode"),
    EXEC("exec"),
    APP_SERVER("appServer"),
    SUB_AGENT("subAgent"),
    SUB_AGENT_REVIEW("subAgentReview"),
    SUB_AGENT_COMPACT("subAgentCompact"),
    SUB_AGENT_THREAD_SPAWN("subAgentThreadSpawn"),
    SUB_AGENT_OTHER("subAgentOther"),
    UNKNOWN("unknown"),
}

data class RecentProject(
    val path: String,
    val name: String,
    val lastActiveAt: Long,
    val threads: List<ThreadSummary>,
)

enum class ConversationAccessMode {
    WRITABLE,
    EXTERNAL_WRITER,
}

data class ConversationState(
    val serverId: String,
    val serverName: String,
    val threadId: String,
    val title: String,
    val cwd: String,
    val history: ConversationHistory = ConversationHistory(),
    val activeItemKeys: Set<ConversationItemKey> = emptySet(),
    val activeTurnId: String? = null,
    val isRunning: Boolean = false,
    val backgroundTerminals: List<BackgroundTerminal> = emptyList(),
    val backgroundTerminalsLoaded: Boolean = false,
    val backgroundTerminalsError: UserFacingError? = null,
    val runtimeStatus: RuntimeStatus = RuntimeStatus(),
    val pendingRequests: List<PendingServerRequest> = emptyList(),
    val plan: ActiveTurnPlan? = null,
    val tokenUsage: TokenUsage? = null,
    val goal: ThreadGoal? = null,
    val goalLoaded: Boolean = false,
    val notices: List<ConversationNotice> = emptyList(),
    val model: String? = null,
    val serviceTier: String? = null,
    val reasoningEffort: String? = null,
    val approvalPolicy: String? = null,
    val approvalsReviewer: String? = null,
    val sandbox: String? = null,
    val errorMessage: UserFacingError? = null,
    val parentThreadId: String? = null,
    val agentNickname: String? = null,
    val agentRole: String? = null,
    val canAcceptDirectInput: Boolean? = null,
    val accessMode: ConversationAccessMode = ConversationAccessMode.WRITABLE,
    val desktopControlAvailable: Boolean = false,
) {
    val items: List<ConversationItem>
        get() = history.items

    val isViewOnly: Boolean
        get() = isExternalWriter || canAcceptDirectInput == false

    val isExternalWriter: Boolean
        get() = accessMode == ConversationAccessMode.EXTERNAL_WRITER

    // Sending through the owner does not grant local Shell, approval or settings access.
    val canSendToDesktop: Boolean
        get() = isExternalWriter && desktopControlAvailable && canAcceptDirectInput != false

    val viewOnlyMessage: String?
        get() = when {
            isExternalWriter && canAcceptDirectInput == false ->
                "此子代理正在其他 Codex 客户端中使用，自动更新历史；输入仍由父会话控制"
            canSendToDesktop -> "已连接此会话的电脑客户端，可在原对话中发送文字。Shell、审批和设置请在电脑端处理"
            isExternalWriter -> "此会话正在其他 Codex 客户端中使用，当前只读并自动更新历史；释放占用后可在此会话继续发送"
            canAcceptDirectInput == false -> "此会话当前仅可查看"
            else -> null
        }
}

data class BackgroundTerminal(
    val itemId: String,
    val processId: String,
    val command: String,
    val cwd: String,
    val observedAtMillis: Long,
)

data class ConversationHistory(
    val turns: List<ConversationTurn> = emptyList(),
    val olderTurnsCursor: String? = null,
    val isLoadingOlderTurns: Boolean = false,
    val olderTurnsError: UserFacingError? = null,
) {
    val items: List<ConversationItem> = turns.flatMap(ConversationTurn::items)
}

data class ConversationTurn(
    val id: String,
    val status: ConversationTurnStatus,
    val items: List<ConversationItem> = emptyList(),
    val detailsState: TurnDetailsState = TurnDetailsState.Loaded,
    val error: UserFacingError? = null,
    // The server summary used to load these details. Full items may contain
    // commentary/async messages that the server intentionally omits from it.
    val detailsSummary: List<ConversationItem>? = null,
)

sealed interface TurnDetailsState {
    data object Unloaded : TurnDetailsState
    class Loading : TurnDetailsState
    data object Loaded : TurnDetailsState
    data class Failed(val error: UserFacingError) : TurnDetailsState
}

enum class ConversationTurnStatus {
    IN_PROGRESS,
    COMPLETED,
    INTERRUPTED,
    FAILED,
}

data class ConversationItemKey(
    val turnId: String,
    val itemId: String,
)

sealed interface ConversationItem {
    val id: String
    val turnId: String

    data class UserMessage(
        override val id: String,
        override val turnId: String,
        val content: List<UserMessagePart>,
    ) : ConversationItem

    data class AgentMessage(
        override val id: String,
        override val turnId: String,
        val text: String,
        val phase: AgentMessagePhase? = null,
        val delivery: AgentMessageDelivery? = null,
        val questions: List<AsyncUserInputQuestion> = emptyList(),
    ) : ConversationItem

    data class Reasoning(
        override val id: String,
        override val turnId: String,
        val summaryParts: List<String>,
        val rawContentParts: List<String>,
    ) : ConversationItem

    data class Plan(
        override val id: String,
        override val turnId: String,
        val text: String,
    ) : ConversationItem

    data class CommandExecution(
        override val id: String,
        override val turnId: String,
        val command: String,
        val cwd: String,
        val output: String,
        val exitCode: Int?,
        val status: ActivityStatus,
        val processId: String? = null,
    ) : ConversationItem

    data class UserShellCommand(
        override val id: String,
        override val turnId: String,
        val command: String,
        val cwd: String,
        val output: String,
        val exitCode: Int?,
        val durationMillis: Long?,
        val status: ActivityStatus,
    ) : ConversationItem

    data class FileChange(
        override val id: String,
        override val turnId: String,
        val changes: List<ChangedFile>,
        val status: ActivityStatus,
    ) : ConversationItem

    data class Activity(
        override val id: String,
        override val turnId: String,
        val kind: ActivityKind,
        val title: String,
        val summary: String? = null,
        val details: String = "",
        val technicalDetails: String? = null,
        val remotePath: String? = null,
        val status: ActivityStatus,
    ) : ConversationItem

    data class AgentCollaboration(
        override val id: String,
        override val turnId: String,
        val tool: AgentTool,
        val status: ActivityStatus,
        val senderThreadId: String,
        val receiverThreadIds: List<String>,
        val prompt: String?,
        val model: String?,
        val reasoningEffort: String?,
        val agentsStates: Map<String, AgentState>,
    ) : ConversationItem

    data class SubAgentActivity(
        override val id: String,
        override val turnId: String,
        val kind: SubAgentActivityKind,
        val agentThreadId: String,
        val agentPath: String,
    ) : ConversationItem
}

enum class AgentMessagePhase {
    COMMENTARY,
    FINAL_ANSWER,
}

enum class AgentMessageDelivery {
    ASYNC,
}

data class AsyncUserInputQuestion(
    val title: String,
    val options: List<String> = emptyList(),
)

enum class ActivityKind {
    MCP_TOOL,
    DYNAMIC_TOOL,
    WEB_SEARCH,
    CONTEXT_COMPACTION,
    REVIEW,
    HOOK,
    IMAGE_VIEW,
    IMAGE_GENERATION,
    WAIT,
    UNKNOWN,
}

data class RemoteFileMetadata(
    val isDirectory: Boolean,
    val isFile: Boolean,
    val isSymlink: Boolean,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
)

sealed interface UserMessagePart {
    data class Text(val text: String) : UserMessagePart

    data class Image(val url: String? = null, val fileId: String? = null) : UserMessagePart {
        init {
            require(url != null || fileId != null) { "图片必须提供 url 或 fileId" }
        }
    }

    data class LocalImage(val path: String) : UserMessagePart

    data class Audio(val url: String) : UserMessagePart

    data class LocalAudio(val path: String) : UserMessagePart

    data class Skill(val name: String, val path: String) : UserMessagePart

    data class Mention(val name: String, val path: String) : UserMessagePart
}

data class ChangedFile(
    val path: String,
    val kind: FileChangeKind,
    val diff: String,
)

sealed interface FileChangeKind {
    data object Add : FileChangeKind

    data object Delete : FileChangeKind

    data class Update(val movePath: String?) : FileChangeKind
}

enum class ActivityStatus {
    IN_PROGRESS,
    COMPLETED,
    INTERRUPTED,
    FAILED,
    DECLINED,
}

sealed interface PendingServerRequest {
    val requestKey: String
    val turnId: String?

    data class Approval(
        override val requestKey: String,
        val itemId: String,
        override val turnId: String,
        val type: ApprovalType,
        val reason: String?,
        val command: String?,
        val cwd: String?,
        val environmentId: String?,
        val networkHost: String?,
        val commandActions: List<CommandAction>,
        val grantRoot: String?,
        val proposedExecPolicy: ExecPolicyAmendment?,
        val proposedNetworkPolicies: List<NetworkPolicyAmendment>,
    ) : PendingServerRequest

    data class Questions(
        override val requestKey: String,
        val itemId: String,
        override val turnId: String,
        val questions: List<UserQuestion>,
        val autoResolutionMillis: Long?,
        val receivedAtMillis: Long,
    ) : PendingServerRequest

    data class McpElicitation(
        override val requestKey: String,
        override val turnId: String?,
        val serverName: String,
        val mode: String,
        val message: String,
        val url: String?,
        val requestedSchema: String?,
    ) : PendingServerRequest

    data class Permissions(
        override val requestKey: String,
        val itemId: String,
        override val turnId: String,
        val environmentId: String?,
        val cwd: String,
        val reason: String?,
        val requestedPermissions: RequestedPermissions,
    ) : PendingServerRequest
}

sealed interface CommandAction {
    val command: String

    data class Read(
        override val command: String,
        val name: String,
        val path: String,
    ) : CommandAction

    data class ListFiles(
        override val command: String,
        val path: String?,
    ) : CommandAction

    data class Search(
        override val command: String,
        val query: String?,
        val path: String?,
    ) : CommandAction

    data class Unknown(override val command: String) : CommandAction
}

data class ExecPolicyAmendment(
    val command: List<String>,
    val responseJson: String,
)

data class NetworkPolicyAmendment(
    val host: String,
    val action: NetworkPolicyRuleAction,
    val responseJson: String,
)

enum class NetworkPolicyRuleAction {
    ALLOW,
    DENY,
}

data class RequestedPermissions(
    val networkEnabled: Boolean?,
    val fileSystem: RequestedFileSystemPermissions?,
    val responseJson: String,
)

data class RequestedFileSystemPermissions(
    val entries: List<FileSystemPermissionEntry>,
    val globScanMaxDepth: Int?,
)

data class FileSystemPermissionEntry(
    val target: FileSystemPermissionTarget,
    val access: FileSystemAccessMode,
)

sealed interface FileSystemPermissionTarget {
    data class Path(val path: String) : FileSystemPermissionTarget

    data class GlobPattern(val pattern: String) : FileSystemPermissionTarget

    data object Root : FileSystemPermissionTarget

    data object Minimal : FileSystemPermissionTarget

    data class ProjectRoots(val subpath: String?) : FileSystemPermissionTarget

    data object TempDirectory : FileSystemPermissionTarget

    data object SlashTmp : FileSystemPermissionTarget

    data class Unknown(val path: String, val subpath: String?) : FileSystemPermissionTarget
}

enum class FileSystemAccessMode {
    READ,
    WRITE,
    DENY,
}

data class UserQuestion(
    val id: String,
    val header: String,
    val question: String,
    val options: List<UserQuestionOption>,
    val allowsOther: Boolean,
    val isSecret: Boolean,
)

data class UserQuestionOption(
    val label: String,
    val description: String,
)

enum class ApprovalType {
    COMMAND_EXECUTION,
    FILE_CHANGE,
}

enum class ApprovalDecision(val wireValue: String) {
    ACCEPT("accept"),
    ACCEPT_FOR_SESSION("acceptForSession"),
    DECLINE("decline"),
    CANCEL("cancel"),
}

data class ActiveTurnPlan(
    val turnId: String,
    val explanation: String?,
    val steps: List<TurnPlanStep>,
)

data class TurnPlanStep(
    val text: String,
    val status: PlanStepStatus,
)

enum class PlanStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
}

data class TokenUsage(
    val total: TokenUsageBreakdown,
    val last: TokenUsageBreakdown,
    val modelContextWindow: Long?,
)

data class TokenUsageBreakdown(
    val totalTokens: Long,
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
    val reasoningOutputTokens: Long,
)

data class ConversationNotice(
    val id: String,
    val message: String,
    val technicalDetails: String? = null,
    val requiresAttention: Boolean = false,
)

enum class ApprovalPolicy(val wireValue: String) {
    UNTRUSTED("untrusted"),
    ON_REQUEST("on-request"),
    NEVER("never"),
}

enum class ApprovalReviewer(val wireValue: String) {
    USER("user"),
    AUTO_REVIEW("auto_review"),
}

enum class SandboxMode(val wireValue: String) {
    READ_ONLY("read-only"),
    WORKSPACE_WRITE("workspace-write"),
    DANGER_FULL_ACCESS("danger-full-access"),
}

enum class Personality(val wireValue: String) {
    NONE("none"),
    FRIENDLY("friendly"),
    PRAGMATIC("pragmatic"),
}

enum class ReasoningSummary(val wireValue: String) {
    AUTO("auto"),
    CONCISE("concise"),
    DETAILED("detailed"),
    NONE("none"),
}

data class ThreadOptions(
    val model: String? = null,
    val modelProvider: String? = null,
    val serviceTier: String? = null,
    val approvalPolicy: ApprovalPolicy? = null,
    val approvalsReviewer: ApprovalReviewer? = null,
    val sandboxMode: SandboxMode? = null,
    val configJson: String? = null,
    val baseInstructions: String? = null,
    val developerInstructions: String? = null,
    val personality: Personality? = null,
    val ephemeral: Boolean = false,
)

data class TurnOptions(
    val cwd: String? = null,
    val model: String? = null,
    val serviceTier: String? = null,
    val reasoningEffort: String? = null,
    val reasoningSummary: ReasoningSummary? = null,
    val personality: Personality? = null,
    val approvalPolicy: ApprovalPolicy? = null,
    val approvalsReviewer: ApprovalReviewer? = null,
    val sandboxMode: SandboxMode? = null,
    val sandboxNetworkAccess: Boolean = false,
    val sandboxWritableRoots: List<String> = emptyList(),
    val sandboxExcludeTmpdirEnvVar: Boolean = false,
    val sandboxExcludeSlashTmp: Boolean = false,
    val outputSchema: String? = null,
)

sealed interface ReviewTarget {
    data object UncommittedChanges : ReviewTarget

    data class BaseBranch(val branch: String) : ReviewTarget

    data class Commit(val sha: String, val title: String? = null) : ReviewTarget

    data class Custom(val instructions: String) : ReviewTarget
}

enum class ReviewDelivery(val wireValue: String) {
    INLINE("inline"),
    DETACHED("detached"),
}

data class ThreadGoal(
    val objective: String,
    val status: ThreadGoalStatus,
    val tokenBudget: Long?,
    val tokensUsed: Long,
    val timeUsedSeconds: Long,
)

enum class ThreadGoalStatus(val wireValue: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    BLOCKED("blocked"),
    USAGE_LIMITED("usageLimited"),
    BUDGET_LIMITED("budgetLimited"),
    COMPLETE("complete"),
}
