@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hebo.codex.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hebo.codex.R
import com.hebo.codex.domain.ActivityStatus
import com.hebo.codex.domain.AgentMessagePhase
import com.hebo.codex.domain.isAsyncQuestion
import com.hebo.codex.domain.recentAsyncQuestion
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import com.hebo.codex.domain.ApprovalDecision
import com.hebo.codex.domain.ApprovalPolicy
import com.hebo.codex.domain.ApprovalReviewer
import com.hebo.codex.domain.ApprovalType
import com.hebo.codex.domain.ConnectionStatus
import com.hebo.codex.domain.ConversationItem
import com.hebo.codex.domain.ConversationState
import com.hebo.codex.domain.DraftAttachment
import com.hebo.codex.domain.FeatureEntry
import com.hebo.codex.domain.ModelOption
import com.hebo.codex.domain.NetworkPolicyRuleAction
import com.hebo.codex.domain.PendingServerRequest
import com.hebo.codex.domain.Personality
import com.hebo.codex.domain.PlanStepStatus
import com.hebo.codex.domain.QuickPhrase
import com.hebo.codex.domain.ReasoningSummary
import com.hebo.codex.domain.ReviewDelivery
import com.hebo.codex.domain.ReviewTarget
import com.hebo.codex.domain.SandboxMode
import com.hebo.codex.domain.ThreadGoal
import com.hebo.codex.domain.ThreadGoalStatus
import com.hebo.codex.domain.ThreadAttention
import com.hebo.codex.domain.TokenUsage
import com.hebo.codex.domain.ActiveTurnPlan
import com.hebo.codex.domain.TurnPlanStep
import com.hebo.codex.domain.TurnOptions
import com.hebo.codex.domain.UserInputPart
import com.hebo.codex.domain.UserFacingError
import com.hebo.codex.domain.projectName
import com.hebo.codex.ui.displayDescriptionRes
import com.hebo.codex.ui.displayLabelRes
import com.hebo.codex.ui.formatCompactCount
import com.hebo.codex.ui.formatPercent
import com.hebo.codex.ui.components.MessageComposer
import com.hebo.codex.ui.components.MessageComposerAction
import com.hebo.codex.ui.components.MessageComposerActionButton
import com.hebo.codex.ui.components.AnchoredDetailsPopup
import com.hebo.codex.ui.components.ExpandableErrorDetails
import com.hebo.codex.ui.components.QuickPhraseManagerDialog
import com.hebo.codex.ui.components.QuickPhraseMenuContent
import com.hebo.codex.ui.components.NavigationAttention
import com.hebo.codex.ui.components.OptionChipRow
import com.hebo.codex.ui.components.SettingDescription
import com.hebo.codex.ui.components.RemainingLinearProgressIndicator
import com.hebo.codex.ui.components.NoticeHostState
import com.hebo.codex.ui.components.NoticeScaffold
import com.hebo.codex.ui.components.ConnectionStatusButton
import com.hebo.codex.ui.quickphrases.QuickPhraseViewModel
import com.hebo.codex.ui.ratelimits.RateLimitIndicator
import com.hebo.codex.ui.ratelimits.RateLimitsUiState
import com.hebo.codex.ui.theme.statusColors
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.LocalImageWidth
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCode
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.model.ImageWidth
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.max

private fun chatMarkdownComponents(
    onRunShellCommand: (suspend (String) -> Boolean)?,
    shellCommandDisabled: Boolean,
    imageTransformer: ChatMarkdownImageTransformer,
    onPreviewImage: (ChatImageSource) -> Unit,
): MarkdownComponents = markdownComponents(
    image = { ChatMarkdownBlockImage(it, imageTransformer, onPreviewImage) },
    inlineImage = { ChatMarkdownInlineImage(it, imageTransformer, onPreviewImage) },
    codeBlock = highlightedCodeBlock,
    codeFence = if (onRunShellCommand == null) {
        highlightedCodeFence
    } else {
        { model ->
            ExecutableMarkdownCodeFence(
                model = model,
                shellCommandDisabled = shellCommandDisabled,
                onRunShellCommand = onRunShellCommand,
            )
        }
    },
    table = { model ->
        MarkdownTable(
            content = model.content,
            node = model.node,
            style = model.typography.table,
            headerBlock = { content, header, tableWidth, style ->
                MarkdownTableHeader(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            },
            rowBlock = { content, row, tableWidth, style ->
                MarkdownTableRow(
                    content = content,
                    header = row,
                    tableWidth = tableWidth,
                    style = style,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            },
        )
    },
)

private const val CHAT_LIST_END_KEY = "chat-list-end"
private const val CHAT_HISTORY_PAGING_KEY = "chat-history-paging"
private const val HISTORY_LOAD_THRESHOLD_ITEMS = 2
private const val PLAN_STEP_PROGRESS_FADE_DURATION_MILLIS = 150
private val ChatPageOverlap = 64.dp
private val ConversationHeaderItemMinHeight = 48.dp
private val ConversationHeaderVerticalPadding = 8.dp
private val ConversationHeaderExpandedActionsMinWidth = 400.dp
private val ChatHazeFadeHeight = 32.dp
private val ChatHazeBlurRadius = 22.dp
private val ChatHeaderContentSpacing = 48.dp
private val ChatComposerOverlayMinHeight = 116.dp
private val ChatComposerMaxWidth = 760.dp
private val ConversationStatisticsPopupWidth = 300.dp

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    noticeState: NoticeHostState,
    quickPhraseViewModel: QuickPhraseViewModel,
    initialAnnouncement: String?,
    rateLimitsState: RateLimitsUiState,
    navigationAttention: NavigationAttention?,
    navigationOpen: Boolean,
    onToggleNavigation: () -> Unit,
    onNewTask: (String) -> Unit,
    onReconnect: () -> Unit,
    onOpenConversation: (String, String?) -> Unit,
    onOpenRelatedConversation: (String) -> Unit,
    onRefreshRateLimits: () -> Unit,
    onConsumeRateLimitReset: (String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val quickPhraseState by quickPhraseViewModel.state.collectAsStateWithLifecycle()
    val conversation = state.conversation
    val models = (state.modelListState as? ModelListState.Ready)?.models.orEmpty()
    val conversationRows = remember(conversation?.history?.turns) {
        buildConversationRows(conversation?.history?.turns.orEmpty())
    }
    val forkTurns = remember(conversation?.history?.turns, conversation?.activeTurnId) {
        buildForkTurns(
            turns = conversation?.history?.turns.orEmpty(),
            activeTurnId = conversation?.activeTurnId,
        )
    }
    val forkTurnsById = remember(forkTurns) { forkTurns.associateBy(ForkTurn::turnId) }
    val hazeState = rememberHazeState()
    val scrollScope = rememberCoroutineScope()
    var bottomControlsHeightPx by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<ChatDialog?>(null) }
    var forkFromTurnId by remember { mutableStateOf<String?>(null) }
    var showTurnOptions by remember { mutableStateOf(false) }
    var activeRequestKey by rememberSaveable(conversation?.threadId) { mutableStateOf<String?>(null) }
    var shownNoticeId by rememberSaveable(conversation?.threadId) { mutableStateOf<String?>(null) }
    var initialAnnouncementShown by rememberSaveable(initialAnnouncement) { mutableStateOf(false) }
    var activityDetailsTurnId by rememberSaveable(conversation?.threadId) { mutableStateOf<String?>(null) }
    var remoteFileTarget by remember(conversation?.threadId) { mutableStateOf<RemoteFileTarget?>(null) }
    var imagePreviewSource by remember(viewModel) { mutableStateOf<ChatImageSource?>(null) }
    val imageLoader = LocalChatImageLoader.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val previewImage: (ChatImageSource) -> Unit = { source ->
        keyboardController?.hide()
        focusManager.clearFocus()
        imagePreviewSource = source
    }
    val activeRequest = conversation?.takeUnless { it.isViewOnly }
        ?.pendingRequests?.firstOrNull { it.requestKey == activeRequestKey }
    val density = LocalDensity.current
    val showLinkError: (String) -> Unit = { message ->
        noticeState.showError(UserFacingError(message))
    }
    val openRemotePath: (String) -> Unit = { path ->
        val target = resolveRemotePath(path)
        if (target == null) showLinkError("无法解析服务端文件路径：$path") else remoteFileTarget = target
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.resumeConversation()
        onRefreshRateLimits()
        onPauseOrDispose {
            viewModel.pauseConversation()
            viewModel.flushDraft()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.OpenConversation -> {
                    dialog = null
                    onOpenConversation(event.threadId, event.announcement)
                }
            }
        }
    }
    LaunchedEffect(initialAnnouncement, initialAnnouncementShown) {
        val announcement = initialAnnouncement ?: return@LaunchedEffect
        if (initialAnnouncementShown) return@LaunchedEffect
        initialAnnouncementShown = true
        noticeState.showMessage(announcement)
    }
    LaunchedEffect(state.screenError, conversation?.errorMessage) {
        val error = state.screenError ?: conversation?.errorMessage
        if (error != null) {
            noticeState.showError(error)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.queueFeedback) {
        state.queueFeedback?.let {
            noticeState.showMessage(it)
            viewModel.clearQueueFeedback()
        }
    }
    LaunchedEffect(conversation?.notices?.lastOrNull()?.id) {
        val notice = conversation?.notices?.lastOrNull() ?: return@LaunchedEffect
        if (notice.id == shownNoticeId) return@LaunchedEffect
        shownNoticeId = notice.id
        if (conversation.errorMessage?.technicalDetails != notice.technicalDetails ||
            conversation.errorMessage?.summary != notice.message
        ) {
            noticeState.showMessage(notice.message, notice.technicalDetails)
        }
    }
    LaunchedEffect(quickPhraseState.error) {
        quickPhraseState.error?.let { error ->
            noticeState.showError(error)
            quickPhraseViewModel.clearError()
        }
    }
    LaunchedEffect(conversation?.pendingRequests?.map { it.requestKey }) {
        if (activeRequestKey != null && activeRequest == null) {
            activeRequestKey = conversation?.pendingRequests?.firstOrNull()?.requestKey
        }
    }
    val bottomSystemPadding = maxOf(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
    )
    val bottomControlsHeight = with(density) { bottomControlsHeightPx.toDp() }
    val topContentPadding = ChatHeaderContentSpacing
    val bottomContentPadding = if (conversation == null) {
        bottomSystemPadding + 16.dp
    } else {
        chatBottomContentPadding(
            bottomControlsHeight = bottomControlsHeight,
            bottomSystemPadding = bottomSystemPadding,
        )
    }
    val topHazeHeight = ChatHazeFadeHeight
    val bottomHazeHeight = bottomControlsHeight + bottomSystemPadding + ChatHazeFadeHeight
    val hazeBackgroundColor = MaterialTheme.colorScheme.background
    val hazeStyle = remember(hazeBackgroundColor) {
        HazeStyle(
            backgroundColor = hazeBackgroundColor,
            tint = HazeTint(hazeBackgroundColor.copy(alpha = 0.50f)),
            blurRadius = ChatHazeBlurRadius,
            noiseFactor = 0f,
        )
    }
    val topHazeMask = remember(density) {
        val fadeHeightPx = with(density) { ChatHazeFadeHeight.toPx() }
        Brush.verticalGradient(
            colors = listOf(Color.Black, Color.Transparent),
            startY = 0f,
            endY = fadeHeightPx,
        )
    }
    val bottomHazeMask = remember(density) {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black),
            endY = with(density) { ChatHazeFadeHeight.toPx() },
        )
    }
    val pageOverlapPx = with(density) { ChatPageOverlap.roundToPx() }
    val liveEdgeHeightPx = with(density) { 1.dp.roundToPx() }
    val history = conversation?.history
    val historyPaging = history?.takeIf {
        it.olderTurnsCursor != null || it.isLoadingOlderTurns || it.olderTurnsError != null
    }
    val liveEdgeIndex = conversationRows.size + if (historyPaging != null) 1 else 0
    val pagingController = rememberChatPagingController(
        conversationId = conversation?.threadId,
        isRunning = conversation?.isRunning == true,
        liveEdgeIndex = liveEdgeIndex,
        pageOverlapPx = pageOverlapPx,
        liveEdgeHeightPx = liveEdgeHeightPx,
    )
    val listEndHeight = with(density) { (liveEdgeHeightPx + pagingController.pageRunwayHeightPx).toDp() }
    LaunchedEffect(
        conversation?.threadId,
        history?.olderTurnsCursor,
        history?.olderTurnsError,
        pagingController.listState,
    ) {
        if (history?.olderTurnsCursor == null || history.olderTurnsError != null) return@LaunchedEffect
        snapshotFlow {
            pagingController.listState.isScrollInProgress to
                pagingController.listState.firstVisibleItemIndex
        }.collect { (isScrolling, firstVisibleItemIndex) ->
            if (isScrolling && firstVisibleItemIndex <= HISTORY_LOAD_THRESHOLD_ITEMS) {
                viewModel.loadOlderTurns()
            }
        }
    }

    NoticeScaffold(
        noticeState = noticeState,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ConversationFloatingHeader(
                conversation = conversation,
                newConversationTitle = null,
                newConversationSubtitle = null,
                rateLimitsState = rateLimitsState,
                connectionStatus = state.connectionStatus,
                onReconnect = onReconnect,
                navigationAttention = navigationAttention,
                navigationOpen = navigationOpen,
                onToggleNavigation = onToggleNavigation,
                onOpenAttention = { onOpenConversation(it, null) },
                actions = ConversationHeaderActions(
                    onNewTask = { conversation?.let { onNewTask(it.cwd) } },
                    onRename = { dialog = ChatDialog.Rename },
                    onFork = {
                        forkFromTurnId = null
                        dialog = ChatDialog.Fork
                    },
                    onOpenGoal = { dialog = ChatDialog.Goal },
                ),
                onRefreshRateLimits = onRefreshRateLimits,
                onConsumeRateLimitReset = onConsumeRateLimitReset,
                shadowElevation = 1.dp,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                conversation == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("无法打开会话")
                }

                else -> LazyColumn(
                    state = pagingController.listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .onSizeChanged { pagingController.onViewportHeightChanged(it.height) }
                        .nestedScroll(pagingController.userScrollConnection),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = topContentPadding,
                        end = 20.dp,
                        bottom = bottomContentPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (historyPaging != null) {
                        item(CHAT_HISTORY_PAGING_KEY) {
                            HistoryPagingControl(
                                isLoading = historyPaging.isLoadingOlderTurns,
                                error = historyPaging.olderTurnsError,
                                onLoad = viewModel::loadOlderTurns,
                                onRetry = viewModel::retryOlderTurns,
                            )
                        }
                    }
                    itemsIndexed(conversationRows, key = { _, row -> row.key }) { index, row ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            when (row) {
                                is ConversationRow.Item -> ConversationItemView(
                                    item = row.item,
                                    imageLoader = imageLoader,
                                    onPreviewImage = previewImage,
                                    serverName = conversation.serverName,
                                    cwd = conversation.cwd,
                                    onOpenRemoteFile = { remoteFileTarget = it },
                                    onLinkError = showLinkError,
                                    shellCommandDisabled = conversation.isRunning || conversation.isViewOnly ||
                                        state.connectionStatus !is ConnectionStatus.Connected,
                                    onRunShellCommand = viewModel::runShellCommand,
                                    asyncReplyEnabled = !conversation.isViewOnly && !state.isSendingDraft &&
                                        state.connectionStatus is ConnectionStatus.Connected,
                                    onAsyncReply = viewModel::replyToAsyncQuestion,
                                )
                                is ConversationRow.TurnActivity -> TurnActivityCard(
                                    onOpenDetails = {
                                        activityDetailsTurnId = row.turnId
                                    },
                                )
                            }
                            val turn = forkTurnsById[row.turnId]
                            if (turn != null && conversationRows.getOrNull(index + 1)?.turnId != row.turnId) {
                                ForkFromTurnAction(
                                    turn = turn,
                                    forkRequest = state.forkRequest,
                                    onFork = {
                                        forkFromTurnId = it
                                        dialog = ChatDialog.Fork
                                    },
                                )
                            }
                        }
                    }
                    item(CHAT_LIST_END_KEY) {
                        Spacer(Modifier.height(listEndHeight))
                    }
                }
            }

            if (conversation != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(topHazeHeight)
                        .hazeEffect(state = hazeState, style = hazeStyle) {
                            mask = topHazeMask
                        },
                )
            }

            if (conversation != null && bottomControlsHeightPx > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(bottomHazeHeight)
                        .hazeEffect(state = hazeState, style = hazeStyle) {
                            mask = bottomHazeMask
                        },
                )
            }

            if (conversation != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .widthIn(max = ChatComposerMaxWidth)
                            .fillMaxWidth()
                            .onSizeChanged { bottomControlsHeightPx = it.height },
                    ) {
                        conversation.pendingRequests.firstOrNull()?.takeUnless { conversation.isViewOnly }?.let { request ->
                            PendingRequestBanner(
                                request = request,
                                requestCount = conversation.pendingRequests.size,
                                onClick = { activeRequestKey = request.requestKey },
                            )
                        }
                        conversation.parentThreadId?.let { parentThreadId ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (conversation.canAcceptDirectInput == false) "子代理由父会话控制" else "子代理会话",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { onOpenRelatedConversation(parentThreadId) }) {
                                    Text("返回父会话")
                                }
                            }
                        }
                        if (conversation.isExternalWriter ||
                            (conversation.isViewOnly && conversation.parentThreadId == null)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    requireNotNull(conversation.viewOnlyMessage),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (conversation.isExternalWriter) {
                                    TextButton(
                                        onClick = viewModel::refreshConversation,
                                        enabled = state.connectionStatus is ConnectionStatus.Connected,
                                    ) { Text(if (conversation.canSendToDesktop) "刷新" else "尝试继续") }
                                }
                            }
                        }
                        if (conversation.isExternalWriter) {
                            state.externalSyncError?.let { error ->
                                Text(
                                    error.summary,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (conversation.canAcceptDirectInput != false) {
                                OutlinedTextField(
                                    value = state.draft.message,
                                    onValueChange = viewModel::updateDraftMessage,
                                    enabled = state.queueOperationId == null && !state.isSendingDraft,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    label = { Text(if (conversation.canSendToDesktop) "在原对话中继续" else "此会话的草稿") },
                                    supportingText = { Text(
                                        if (conversation.canSendToDesktop) {
                                            if (conversation.isRunning) "电脑正在处理，完成后可发送" else "发送文字给电脑端，沿用原会话设置"
                                        } else "仅保存草稿；恢复发送后需手动发送，不会创建新对话",
                                    ) },
                                    minLines = 1,
                                    maxLines = 3,
                                )
                                if (conversation.canSendToDesktop) {
                                    if (state.draft.attachments.isNotEmpty()) {
                                        TextButton(
                                            onClick = { state.draft.attachments.forEach { viewModel.removeDraftAttachment(it.id) } },
                                            enabled = !state.isSendingDraft && state.queueOperationId == null,
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                        ) { Text("移除草稿附件（${state.draft.attachments.size}），仅发送文字") }
                                    }
                                    TextButton(
                                        onClick = {
                                            pagingController.onSubmission()
                                            viewModel.sendDesktopDraft()
                                        },
                                        enabled = !conversation.isRunning && !state.isSendingDraft &&
                                            state.draft.message.isNotBlank() &&
                                            state.draft.attachments.isEmpty() &&
                                            state.connectionStatus is ConnectionStatus.Connected,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    ) { Text(if (state.isSendingDraft) "正在发送" else "发送到原对话") }
                                }
                            }
                        }
                        ConversationAccessoryPanels(
                            conversation = if (conversation.goalLoaded) conversation else conversation.copy(goal = state.goal),
                            state = state,
                            onRetrySubAgents = viewModel::refreshSubAgents,
                            onRetryQueue = viewModel::refreshRuntimeQueue,
                            onOpenConversation = onOpenRelatedConversation,
                            onTerminateCommand = viewModel::terminateBackgroundCommand,
                            onWithdrawQueue = viewModel::withdrawQueuedInput,
                            onLocateQuestion = {
                                val question = conversation.history.recentAsyncQuestion()
                                val index = conversationRows.indexOfFirst { row ->
                                    row is ConversationRow.Item && row.item.id == question?.id && row.turnId == question.turnId
                                }
                                if (index >= 0) scrollScope.launch {
                                    pagingController.locateItem(index + if (historyPaging != null) 1 else 0)
                                }
                            },
                        )
                        if (!conversation.isViewOnly) {
                            ConversationComposer(
                                imageLoader = imageLoader,
                                onPreviewImage = previewImage,
                                serverId = conversation.serverId,
                                conversationId = conversation.threadId,
                                shellTarget = ShellCommandTarget(conversation.serverName, conversation.cwd),
                                isRunning = conversation.isRunning,
                                message = state.draft.message,
                                attachments = state.draft.attachments,
                                modelLabel = composerModelLabel(
                                    model = state.draft.options.model,
                                    reasoningEffort = state.draft.options.reasoningEffort,
                                    models = models,
                                ),
                                resources = state.inputResources,
                                quickPhrases = quickPhraseState.phrases,
                                isLoadingResources = state.isLoadingResources,
                                isSending = state.isSendingDraft,
                                onQueue = viewModel::queueDraft,
                                onPreparingAttachmentChange = viewModel::setPreparingAttachment,
                                sendEnabled = state.connectionStatus is ConnectionStatus.Connected,
                                placeholder = when {
                                    state.connectionStatus !is ConnectionStatus.Connected -> "服务器已断开，可继续编辑"
                                    conversation.isRunning -> "继续对话"
                                    else -> "向 Codex 发送消息"
                                },
                                commands = ComposerCommand.entries,
                                onLoadResources = viewModel::loadInputResources,
                                onAddQuickPhrase = quickPhraseViewModel::add,
                                onUpdateQuickPhrase = quickPhraseViewModel::update,
                                onDeleteQuickPhrase = quickPhraseViewModel::delete,
                                onMoveQuickPhraseUp = quickPhraseViewModel::moveUp,
                                onMoveQuickPhraseDown = quickPhraseViewModel::moveDown,
                                onMessageChange = viewModel::updateDraftMessage,
                                onAddAttachment = viewModel::addDraftAttachment,
                                onRemoveAttachment = viewModel::removeDraftAttachment,
                                onInputError = viewModel::reportInputError,
                                onOpenOptions = {
                                    viewModel.loadModels()
                                    showTurnOptions = true
                                },
                                onCommand = { command ->
                                    when (command) {
                                        ComposerCommand.GOAL -> dialog = ChatDialog.Goal
                                        ComposerCommand.REVIEW -> dialog = ChatDialog.Review
                                        ComposerCommand.COMPACT -> viewModel.compact()
                                        else -> Unit
                                    }
                                },
                                onSend = {
                                    pagingController.onSubmission()
                                    viewModel.sendDraft()
                                },
                                onSendQuickPhrase = { content ->
                                    pagingController.onSubmission()
                                    viewModel.sendQuickPhrase(content)
                                },
                                onStop = viewModel::interrupt,
                            )
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = conversation != null && pagingController.showReturnToOutput,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomContentPadding + 8.dp),
                enter = fadeIn(tween(160)) +
                    scaleIn(tween(160), initialScale = 0.85f),
                exit = fadeOut(tween(100)) +
                    scaleOut(tween(100), targetScale = 0.9f),
            ) {
                SmallFloatingActionButton(
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    onClick = pagingController::returnToOutput,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "回到当前输出")
                }
            }
        }
    }

    when (dialog) {
        ChatDialog.Rename -> RenameConversationDialog(
            title = conversation?.title.orEmpty(),
            onDismiss = { dialog = null },
            onRename = {
                viewModel.rename(it)
                dialog = null
            },
        )

        ChatDialog.Fork -> ForkConversationSheet(
            turns = forkTurns,
            initialLastTurnId = forkFromTurnId,
            isRunning = conversation?.isRunning == true,
            forkRequest = state.forkRequest,
            onDismiss = { dialog = null },
            onFork = { lastTurnId ->
                viewModel.fork(lastTurnId)
                dialog = null
            },
        )

        ChatDialog.Review -> ReviewDialog(
            onDismiss = { dialog = null },
            onSubmit = { target, delivery ->
                viewModel.review(target, delivery)
                dialog = null
            },
        )

        ChatDialog.Goal -> GoalDialog(
            goal = state.goal,
            onDismiss = { dialog = null },
            onSave = { objective, status, budget ->
                viewModel.setGoal(objective, status, budget)
                dialog = null
            },
            onClear = {
                viewModel.clearGoal()
                dialog = null
            },
        )

        null -> Unit
    }

    conversation?.history?.turns?.firstOrNull { it.id == activityDetailsTurnId }?.let { turn ->
        val details = activityDetailsContent(turn, conversation.activeTurnId)
        val answerIndex = finalAnswerRowIndex(conversationRows, turn.id)
        LifecycleResumeEffect(viewModel, turn.id, turn.detailsState) {
            viewModel.loadTurnDetails(turn.id)
            onPauseOrDispose { }
        }
        ActivityDetailsSheet(
            turnId = turn.id,
            turnStatus = turn.status,
            turnError = turn.error,
            detailsState = turn.detailsState,
            items = details.items,
            isSealed = details.isSealed,
            activeItemKeys = conversation.activeItemKeys,
            activeActions = if (turn.id != conversation.activeTurnId) {
                null
            } else {
                ActivityDetailsActions(
                    isRunning = conversation.isRunning && !conversation.isViewOnly,
                    pendingRequest = conversation.pendingRequests.firstOrNull()?.takeUnless { conversation.isViewOnly },
                    pendingRequestCount = if (conversation.isViewOnly) 0 else conversation.pendingRequests.size,
                    onStop = viewModel::interrupt,
                    onOpenPendingRequest = { activeRequestKey = it },
                )
            },
            cwd = conversation.cwd,
            onOpenRemoteFile = { remoteFileTarget = it },
            onLinkError = showLinkError,
            onOpenRemotePath = openRemotePath,
            onOpenConversation = { threadId ->
                activityDetailsTurnId = null
                onOpenRelatedConversation(threadId)
            },
            onRetry = { viewModel.retryTurnDetails(turn.id) },
            onViewAnswer = answerIndex?.let { index ->
                {
                    activityDetailsTurnId = null
                    scrollScope.launch {
                        pagingController.locateItem(index + if (historyPaging != null) 1 else 0)
                    }
                    Unit
                }
            },
            onDismiss = { activityDetailsTurnId = null },
        )
    }

    activeRequest?.let { request ->
        val pendingRequests = conversation.pendingRequests
        val requestIndex = pendingRequests.indexOfFirst { it.requestKey == request.requestKey }.coerceAtLeast(0)
        ModalBottomSheet(
            onDismissRequest = { activeRequestKey = null },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "待处理 ${requestIndex + 1}/${pendingRequests.size}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = { activeRequestKey = pendingRequests[requestIndex - 1].requestKey },
                        enabled = requestIndex > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一项")
                    }
                    IconButton(
                        onClick = { activeRequestKey = pendingRequests[requestIndex + 1].requestKey },
                        enabled = requestIndex < pendingRequests.lastIndex,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一项")
                    }
                }
                Box(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        PendingRequestCard(
                            request = request,
                            cwd = conversation.cwd,
                            onOpenRemoteFile = { remoteFileTarget = it },
                            onLinkError = showLinkError,
                            onApproval = viewModel::respondToApproval,
                            onExecPolicy = viewModel::respondToApprovalWithExecPolicy,
                            onNetworkPolicy = viewModel::respondToApprovalWithNetworkPolicy,
                            onQuestions = viewModel::respondToQuestions,
                            onMcp = viewModel::respondToMcpElicitation,
                            onPermissions = viewModel::respondToPermissions,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
    imagePreviewSource?.let { source ->
        ChatImagePreviewDialog(
            source = source,
            imageLoader = imageLoader,
            onDismiss = { imagePreviewSource = null },
        )
    }
    remoteFileTarget?.let { target ->
        RemoteFileSheet(
            target = target,
            serverName = conversation?.serverName.orEmpty(),
            onDismiss = { remoteFileTarget = null },
            getMetadata = viewModel::getRemoteFileMetadata,
            readPreview = viewModel::readRemoteFilePreview,
            downloadFile = viewModel::downloadRemoteFile,
            onOpenRemoteFile = { remoteFileTarget = it },
            onLinkError = showLinkError,
            onSaved = { fileName ->
                remoteFileTarget = null
                noticeState.showMessage("已保存 $fileName")
            },
        )
    }
    if (showTurnOptions) {
        TurnOptionsSheet(
            value = state.draft.options,
            modelListState = state.modelListState,
            onDismiss = { showTurnOptions = false },
            onRetryModels = { viewModel.loadModels(forceRefresh = true) },
            onSave = {
                viewModel.updateDraftOptions(it)
                showTurnOptions = false
            },
        )
    }
}

@Composable
private fun HistoryPagingControl(
    isLoading: Boolean,
    error: UserFacingError?,
    onLoad: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "正在加载更早内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            error != null -> TextButton(onClick = onRetry) {
                Text("${error.summary}，点击重试")
            }

            else -> TextButton(onClick = onLoad) {
                Text("加载更早内容")
            }
        }
    }
}

internal fun chatBottomContentPadding(
    bottomControlsHeight: Dp,
    bottomSystemPadding: Dp,
): Dp = maxOf(bottomControlsHeight, ChatComposerOverlayMinHeight) + bottomSystemPadding

internal data class ConversationHeaderActions(
    val onNewTask: () -> Unit,
    val onRename: () -> Unit,
    val onFork: () -> Unit,
    val onOpenGoal: () -> Unit,
)

@Composable
internal fun ConversationFloatingHeader(
    conversation: ConversationState?,
    newConversationTitle: String?,
    newConversationSubtitle: String?,
    rateLimitsState: RateLimitsUiState,
    connectionStatus: ConnectionStatus,
    onReconnect: () -> Unit,
    navigationAttention: NavigationAttention?,
    navigationOpen: Boolean,
    onToggleNavigation: () -> Unit,
    onOpenAttention: (String) -> Unit,
    actions: ConversationHeaderActions?,
    onRefreshRateLimits: () -> Unit,
    onConsumeRateLimitReset: (String?) -> Unit,
    shadowElevation: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    var statisticsExpanded by remember(conversation?.threadId) { mutableStateOf(false) }
    val headerTitle = conversation?.title ?: newConversationTitle
    val headerSubtitle = conversation?.let {
        if (it.parentThreadId != null) {
            listOfNotNull("子代理", it.agentNickname, it.agentRole).joinToString(" · ")
        } else {
            projectName(it.cwd)
        }
    } ?: newConversationSubtitle
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = ConversationHeaderVerticalPadding),
    ) {
        val showNewTaskShortcut = maxWidth >= ConversationHeaderExpandedActionsMinWidth
        if (headerTitle == null) {
            ConnectionStatusButton(
                status = connectionStatus,
                onReconnect = onReconnect,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            Surface(
                onClick = onToggleNavigation,
                modifier = Modifier.size(ConversationHeaderItemMinHeight),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                shadowElevation = shadowElevation,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    NavigationMenuIcon(navigationOpen)
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ConversationHeaderItemMinHeight),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                shadowElevation = shadowElevation,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.Button, onClick = onToggleNavigation)
                            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NavigationMenuIcon(navigationOpen)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                headerTitle,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                headerSubtitle.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    navigationAttention?.let { attention ->
                        NavigationAttentionButton(
                            attention = attention,
                            onClick = { onOpenAttention(attention.targetThreadId) },
                        )
                    }
                    ConnectionStatusButton(connectionStatus, onReconnect)
                    RateLimitIndicator(
                        state = rateLimitsState,
                        onRefresh = onRefreshRateLimits,
                        onConsumeResetCredit = onConsumeRateLimitReset,
                    )
                    if (actions != null && showNewTaskShortcut) {
                        IconButton(onClick = actions.onNewTask) {
                            Icon(Icons.Rounded.Add, contentDescription = "新建任务")
                        }
                    }
                    if (actions != null) {
                        Box {
                            IconButton(onClick = { actionsExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "会话操作")
                            }
                            DropdownMenu(
                                expanded = actionsExpanded,
                                onDismissRequest = { actionsExpanded = false },
                            ) {
                                if (!showNewTaskShortcut) {
                                    ChatActionMenuItem(
                                        text = "新建任务",
                                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                        onClick = {
                                            actionsExpanded = false
                                            actions.onNewTask()
                                        },
                                    )
                                }
                                if (conversation?.isViewOnly != true) ChatActionMenuItem(
                                    text = "重命名",
                                    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        actions.onRename()
                                    },
                                )
                                ChatActionMenuItem(
                                    text = "分叉会话",
                                    icon = { Icon(Icons.AutoMirrored.Rounded.CallSplit, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        actions.onFork()
                                    },
                                )
                                if (conversation?.isViewOnly != true) {
                                    ChatActionMenuItem(
                                        text = "目标管理",
                                        icon = { Icon(Icons.Rounded.Flag, contentDescription = null) },
                                        onClick = {
                                            actionsExpanded = false
                                            actions.onOpenGoal()
                                        },
                                    )
                                }
                                ChatActionMenuItem(
                                    text = "对话统计",
                                    icon = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
                                    onClick = {
                                        actionsExpanded = false
                                        statisticsExpanded = true
                                    },
                                )
                            }
                            if (statisticsExpanded) {
                                ConversationStatisticsPopup(
                                    usage = conversation?.tokenUsage,
                                    onDismiss = { statisticsExpanded = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationMenuIcon(
    navigationOpen: Boolean,
) {
    Icon(
        Icons.Rounded.Menu,
        contentDescription = if (navigationOpen) "收起导航" else "打开导航",
    )
}

@Composable
private fun NavigationAttentionButton(
    attention: NavigationAttention,
    onClick: () -> Unit,
) {
    val statusLabel = when (attention.targetAttention) {
        ThreadAttention.FAILED -> "失败"
        ThreadAttention.NEEDS_INPUT -> "待处理"
        ThreadAttention.READY -> "可审阅"
        ThreadAttention.RUNNING -> error("运行中的会话不应显示为待处理")
    }
    val statusColor = when (attention.targetAttention) {
        ThreadAttention.FAILED -> MaterialTheme.colorScheme.error
        ThreadAttention.NEEDS_INPUT -> MaterialTheme.statusColors.warning
        ThreadAttention.READY -> MaterialTheme.statusColors.info
        ThreadAttention.RUNNING -> error("运行中的会话不应显示为待处理")
    }
    val badgeContentColor = if (attention.targetAttention == ThreadAttention.FAILED) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.surface
    }
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = statusColor),
    ) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = statusColor,
                    contentColor = badgeContentColor,
                ) {
                    Text(if (attention.count > 99) "99+" else attention.count.toString())
                }
            },
        ) {
            Icon(
                imageVector = when (attention.targetAttention) {
                    ThreadAttention.FAILED -> Icons.Rounded.ErrorOutline
                    ThreadAttention.NEEDS_INPUT -> Icons.Rounded.PriorityHigh
                    ThreadAttention.READY -> Icons.Rounded.Circle
                    ThreadAttention.RUNNING -> error("运行中的会话不应显示为待处理")
                },
                contentDescription = "打开下一个${statusLabel}会话，共 ${attention.count} 个待处理会话",
                modifier = if (attention.targetAttention == ThreadAttention.READY) {
                    Modifier.size(12.dp)
                } else {
                    Modifier.size(24.dp)
                },
            )
        }
    }
}

@Composable
private fun ChatActionMenuItem(
    text: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        leadingIcon = icon,
    )
}

@Composable
private fun ForkFromTurnAction(
    turn: ForkTurn,
    forkRequest: ForkRequest?,
    onFork: (String) -> Unit,
) {
    val isCreating = forkRequest?.lastTurnId == turn.turnId
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            onClick = { onFork(turn.turnId) },
            enabled = forkRequest == null,
        ) {
            if (isCreating) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.CallSplit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(if (isCreating) "正在创建…" else "分叉为新对话")
        }
    }
}

@Composable
private fun ConversationItemView(
    item: ConversationItem,
    imageLoader: ChatImageLoader,
    onPreviewImage: (ChatImageSource) -> Unit,
    serverName: String,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    shellCommandDisabled: Boolean,
    onRunShellCommand: suspend (String) -> Boolean,
    asyncReplyEnabled: Boolean,
    onAsyncReply: suspend (String) -> Boolean,
) {
    when (item) {
        is ConversationItem.UserMessage -> UserMessage(item, imageLoader, onPreviewImage)
        is ConversationItem.UserShellCommand -> UserShellCommand(item, serverName)
        is ConversationItem.AgentMessage -> AgentMessage(
            item = item,
            cwd = cwd,
            onOpenRemoteFile = onOpenRemoteFile,
            onLinkError = onLinkError,
            shellCommandDisabled = shellCommandDisabled,
            onRunShellCommand = onRunShellCommand,
            asyncReplyEnabled = asyncReplyEnabled,
            onAsyncReply = onAsyncReply,
        )
        else -> error("处理过程条目应在详情面板展示")
    }
}

@Composable
private fun UserShellCommand(
    item: ConversationItem.UserShellCommand,
    serverName: String,
) {
    val statusColor = when (item.status) {
        ActivityStatus.FAILED,
        ActivityStatus.DECLINED,
        -> MaterialTheme.colorScheme.error

        ActivityStatus.IN_PROGRESS,
        ActivityStatus.COMPLETED,
        ActivityStatus.INTERRUPTED,
        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = statusColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "用户 Shell",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                )
                if (item.status == ActivityStatus.IN_PROGRESS) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = statusColor, strokeWidth = 2.dp)
                } else {
                    Text(
                        when (item.status) {
                            ActivityStatus.COMPLETED -> "已完成"
                            ActivityStatus.FAILED -> "失败"
                            ActivityStatus.DECLINED -> "已取消"
                            ActivityStatus.INTERRUPTED -> "已中断"
                            ActivityStatus.IN_PROGRESS -> error("已处理进行中状态")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                    )
                }
            }
            ShellCommandBlock(item.command)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$serverName · ${item.cwd}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CopyTextIconButton(item.cwd, "复制工作目录")
            }
            if (item.output.isNotBlank()) {
                TerminalOutputBlock(item.output)
            } else if (item.status == ActivityStatus.IN_PROGRESS) {
                Text(
                    "等待命令输出…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val result = listOfNotNull(
                item.exitCode?.let { "退出码 $it" },
                item.durationMillis?.let(::formatShellDuration),
            ).joinToString(" · ")
            if (result.isNotEmpty()) {
                Text(
                    result,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
            }
        }
    }
}

internal fun formatShellDuration(durationMillis: Long): String = when {
    durationMillis < 1_000L -> "小于 1 秒"
    durationMillis % 1_000L < 100L -> "${durationMillis / 1_000L} 秒"
    else -> "${durationMillis / 1_000L}.${durationMillis % 1_000L / 100L} 秒"
}

@Composable
private fun AgentMessage(
    item: ConversationItem.AgentMessage,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    shellCommandDisabled: Boolean,
    onRunShellCommand: suspend (String) -> Boolean,
    asyncReplyEnabled: Boolean,
    onAsyncReply: suspend (String) -> Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        if (item.isAsyncQuestion) {
            AsyncQuestionMessage(item, asyncReplyEnabled, onAsyncReply)
        } else SelectionContainer {
            ChatMarkdown(
                content = item.text.ifEmpty { "…" },
                cwd = cwd,
                onOpenRemoteFile = onOpenRemoteFile,
                onLinkError = onLinkError,
                onRunShellCommand = onRunShellCommand.takeIf {
                    item.phase == AgentMessagePhase.FINAL_ANSWER
                },
                shellCommandDisabled = shellCommandDisabled,
            )
        }
    }
}

@Composable
internal fun ChatMarkdown(
    content: String,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onRunShellCommand: (suspend (String) -> Boolean)? = null,
    shellCommandDisabled: Boolean = false,
) {
    val markdownState = rememberMarkdownState(content = content, retainState = true)
    val imageLoader = LocalChatImageLoader.current
    val imageTransformer = remember(imageLoader, cwd) { ChatMarkdownImageTransformer(imageLoader, cwd) }
    var imagePreviewSource by remember(imageLoader, cwd) { mutableStateOf<ChatImageSource?>(null) }
    val externalUriHandler = LocalUriHandler.current
    var pendingExternalUri by remember { mutableStateOf<String?>(null) }
    val currentOpenRemoteFile by rememberUpdatedState(onOpenRemoteFile)
    val currentLinkError by rememberUpdatedState(onLinkError)
    val components = remember(onRunShellCommand, shellCommandDisabled, imageTransformer) {
        chatMarkdownComponents(onRunShellCommand, shellCommandDisabled, imageTransformer) { imagePreviewSource = it }
    }
    val uriHandler = remember(cwd, externalUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                when (val destination = resolveChatLink(uri, cwd)) {
                    is ChatLinkDestination.RemoteFile -> currentOpenRemoteFile(destination.target)
                    is ChatLinkDestination.External -> pendingExternalUri = destination.uri

                    is ChatLinkDestination.Invalid -> currentLinkError(destination.message)
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalUriHandler provides uriHandler,
        LocalImageWidth provides ImageWidth.MAX_WIDTH,
    ) {
        Markdown(
            markdownState = markdownState,
            imageTransformer = imageTransformer,
            components = components,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    imagePreviewSource?.let { source ->
        ChatImagePreviewDialog(
            source = source,
            imageLoader = imageLoader,
            onDismiss = { imagePreviewSource = null },
        )
    }
    pendingExternalUri?.let { uri ->
        ExternalLinkConfirmationDialog(
            uri = uri,
            onDismiss = { pendingExternalUri = null },
            onConfirm = {
                pendingExternalUri = null
                runCatching { externalUriHandler.openUri(uri) }
                    .onFailure { error -> currentLinkError(error.message ?: "无法打开链接") }
            },
        )
    }
}

@Composable
private fun ExecutableMarkdownCodeFence(
    model: MarkdownComponentModel,
    shellCommandDisabled: Boolean,
    onRunShellCommand: suspend (String) -> Boolean,
) {
    MarkdownCodeFence(model.content, model.node, model.typography.code) { source, language, style ->
        var confirmationOpen by remember(source, language) { mutableStateOf(false) }
        var submitting by remember(source, language) { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Column {
            if (isExecutableShellCode(language, source)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { confirmationOpen = true },
                        enabled = !shellCommandDisabled && !submitting,
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(if (submitting) "执行中" else "执行")
                    }
                }
            }
            MarkdownHighlightedCode(source, language, style)
        }
        if (confirmationOpen) {
            AlertDialog(
                onDismissRequest = { confirmationOpen = false },
                title = { Text("确认执行这段 Shell 命令？") },
                text = { Text("命令将在服务端当前项目目录直接执行，不受 Codex 沙箱限制") },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmationOpen = false
                            scope.launch {
                                submitting = true
                                try {
                                    onRunShellCommand(source)
                                } finally {
                                    submitting = false
                                }
                            }
                        },
                    ) {
                        Text("执行")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmationOpen = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

internal fun isExecutableShellCode(language: String?, source: String): Boolean =
    source.isNotBlank() &&
        language?.trim()?.substringBefore(' ')?.lowercase() in setOf("bash", "sh", "zsh")

@Composable
private fun ExternalLinkConfirmationDialog(
    uri: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打开外部链接？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("即将在浏览器或其他应用中打开以下地址，请确认协议和域名")
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    SelectionContainer {
                        Text(
                            text = uri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("继续打开") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun PendingRequestBanner(
    request: PendingServerRequest,
    requestCount: Int,
    onClick: () -> Unit,
) {
    val isCritical = request is PendingServerRequest.Permissions
    val icon = when (request) {
        is PendingServerRequest.Approval -> if (request.type == ApprovalType.COMMAND_EXECUTION) {
            Icons.Rounded.Code
        } else {
            Icons.Rounded.Description
        }
        is PendingServerRequest.Questions -> Icons.AutoMirrored.Rounded.HelpOutline
        is PendingServerRequest.McpElicitation -> Icons.Rounded.Extension
        is PendingServerRequest.Permissions -> Icons.Rounded.Lock
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(pendingRequestTitle(request), style = MaterialTheme.typography.labelLarge)
                Text(
                    if (requestCount > 1) "还有 ${requestCount - 1} 项等待处理" else pendingRequestSummary(request),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("查看", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun pendingRequestTitle(request: PendingServerRequest): String = when (request) {
    is PendingServerRequest.Approval -> if (request.type == ApprovalType.COMMAND_EXECUTION) {
        "命令等待确认"
    } else {
        "文件修改等待确认"
    }

    is PendingServerRequest.Questions -> "Codex 需要你的回答"
    is PendingServerRequest.McpElicitation -> "${request.serverName} 请求确认"
    is PendingServerRequest.Permissions -> "权限请求"
}

private fun pendingRequestSummary(request: PendingServerRequest): String = when (request) {
    is PendingServerRequest.Approval -> request.reason ?: request.command ?: request.cwd ?: "查看详情后决定"
    is PendingServerRequest.Questions -> request.questions.firstOrNull()?.question ?: "请补充信息"
    is PendingServerRequest.McpElicitation -> request.message
    is PendingServerRequest.Permissions -> request.reason ?: request.cwd
}

@Composable
private fun PendingRequestCard(
    request: PendingServerRequest,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onApproval: (String, ApprovalDecision) -> Unit,
    onExecPolicy: (String, String) -> Unit,
    onNetworkPolicy: (String, String) -> Unit,
    onQuestions: (String, Map<String, List<String>>) -> Unit,
    onMcp: (String, String, String?) -> Unit,
    onPermissions: (String, String, Boolean, Boolean, Boolean) -> Unit,
) {
    when (request) {
        is PendingServerRequest.Approval -> ApprovalCard(
            approval = request,
            onDecision = { decision -> onApproval(request.requestKey, decision) },
            onExecPolicy = { amendment -> onExecPolicy(request.requestKey, amendment) },
            onNetworkPolicy = { amendment -> onNetworkPolicy(request.requestKey, amendment) },
        )

        is PendingServerRequest.Questions -> QuestionsCard(
            request = request,
            cwd = cwd,
            onOpenRemoteFile = onOpenRemoteFile,
            onLinkError = onLinkError,
            onSubmit = { answers -> onQuestions(request.requestKey, answers) },
        )

        is PendingServerRequest.McpElicitation -> McpElicitationCard(request) { action, content ->
            onMcp(request.requestKey, action, content)
        }

        is PendingServerRequest.Permissions -> PermissionsCard(request) { grant, session, strictAutoReview ->
            onPermissions(
                request.requestKey,
                request.requestedPermissions.responseJson,
                grant,
                session,
                strictAutoReview,
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: PendingServerRequest.Approval,
    onDecision: (ApprovalDecision) -> Unit,
    onExecPolicy: (String) -> Unit,
    onNetworkPolicy: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (approval.type == ApprovalType.COMMAND_EXECUTION) "命令等待审批" else "文件修改等待审批",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            approval.reason?.takeIf(String::isNotBlank)?.let { Text(it) }
            approval.command?.takeIf(String::isNotBlank)?.let { ShellCommandBlock(it) }
            approval.cwd?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            approval.networkHost?.let { Text("网络主机：$it") }
            approval.grantRoot?.let { Text("授权目录：$it") }
            if (approval.commandActions.isNotEmpty()) {
                Text("这条命令将执行", style = MaterialTheme.typography.labelLarge)
                approval.commandActions.forEach { CommandActionView(it) }
            }
            approval.proposedExecPolicy?.let { amendment ->
                PolicyProposal(
                    title = "以后允许执行同类命令",
                    description = amendment.command.joinToString(" "),
                    technicalDetails = amendment.responseJson,
                )
                OutlinedButton(
                    onClick = { onExecPolicy(amendment.responseJson) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("保存命令规则并允许本次操作")
                }
            }
            approval.proposedNetworkPolicies.forEach { amendment ->
                PolicyProposal(
                    title = if (amendment.action == NetworkPolicyRuleAction.ALLOW) {
                        "以后允许访问此主机"
                    } else {
                        "以后阻止访问此主机"
                    },
                    description = amendment.host,
                    technicalDetails = amendment.responseJson,
                )
                OutlinedButton(
                    onClick = { onNetworkPolicy(amendment.responseJson) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("保存网络规则并允许本次操作") }
            }
            Button(
                onClick = { onDecision(ApprovalDecision.ACCEPT) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("允许一次") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDecision(ApprovalDecision.ACCEPT_FOR_SESSION) },
                    modifier = Modifier.weight(1f),
                ) { Text("本会话允许") }
                TextButton(
                    onClick = { onDecision(ApprovalDecision.DECLINE) },
                    modifier = Modifier.weight(1f),
                ) { Text("拒绝") }
            }
        }
    }
}

@Composable
private fun QuestionsCard(
    request: PendingServerRequest.Questions,
    cwd: String,
    onOpenRemoteFile: (RemoteFileTarget) -> Unit,
    onLinkError: (String) -> Unit,
    onSubmit: (Map<String, List<String>>) -> Unit,
) {
    var answers by remember(request.requestKey) { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var otherQuestions by remember(request.requestKey) {
        mutableStateOf(request.questions.filter { it.options.isEmpty() }.map { it.id }.toSet())
    }
    var remainingMillis by remember(request.requestKey) {
        mutableStateOf(
            request.autoResolutionMillis?.let {
                max(0L, it - (System.currentTimeMillis() - request.receivedAtMillis))
            },
        )
    }
    LaunchedEffect(request.requestKey, request.autoResolutionMillis) {
        if (request.autoResolutionMillis != null) {
            while ((remainingMillis ?: 0L) > 0L) {
                delay(1_000)
                remainingMillis = max(
                    0L,
                    request.autoResolutionMillis - (System.currentTimeMillis() - request.receivedAtMillis),
                )
            }
        }
    }
    val canSubmit = allQuestionsAnswered(request.questions, answers) && remainingMillis != 0L
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Codex 需要你的回答", style = MaterialTheme.typography.titleMedium)
            request.questions.forEach { question ->
                Text(question.header, style = MaterialTheme.typography.labelLarge)
                SelectionContainer {
                    ChatMarkdown(
                        content = question.question,
                        cwd = cwd,
                        onOpenRemoteFile = onOpenRemoteFile,
                        onLinkError = onLinkError,
                    )
                }
                if (question.options.isNotEmpty()) {
                    question.options.forEach { option ->
                        val selected = question.id !in otherQuestions &&
                            answers[question.id].orEmpty().singleOrNull() == option.label
                        Surface(
                            onClick = {
                                otherQuestions = otherQuestions - question.id
                                answers = answers + (question.id to listOf(option.label))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(option.label, style = MaterialTheme.typography.labelLarge)
                                    if (option.description.isNotBlank()) {
                                        Text(
                                            option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (question.options.isEmpty() || question.allowsOther) {
                    val selected = question.options.isEmpty() || question.id in otherQuestions
                    if (question.options.isNotEmpty()) {
                        Surface(
                            onClick = {
                                otherQuestions = otherQuestions + question.id
                                answers = answers + (question.id to emptyList())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Text("其他回答", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    if (selected) {
                        OutlinedTextField(
                            value = answers[question.id].orEmpty().singleOrNull().orEmpty(),
                            onValueChange = { value -> answers = answers + (question.id to listOf(value)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (question.isSecret) "保密回答" else "回答") },
                            visualTransformation = if (question.isSecret) {
                                PasswordVisualTransformation()
                            } else {
                                androidx.compose.ui.text.input.VisualTransformation.None
                            },
                        )
                    }
                }
            }
            remainingMillis?.let {
                Text(
                    if (it > 0L) "将在 ${(it + 999L) / 1_000L} 秒后自动处理" else "该请求正在自动处理",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onSubmit(answers) },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("提交回答") }
        }
    }
}

@Composable
private fun McpElicitationCard(
    request: PendingServerRequest.McpElicitation,
    onDecision: (String, String?) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var content by rememberSaveable(request.requestKey) { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${request.serverName} 请求确认", style = MaterialTheme.typography.titleMedium)
            Text(request.message)
            request.requestedSchema?.let {
                Text("该服务需要结构化响应", style = MaterialTheme.typography.labelLarge)
                ExpandableTechnicalDetails(it)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("响应内容（JSON 格式）") },
                    minLines = 3,
                )
            }
            request.url?.let { url ->
                OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
                    Text("打开授权页面")
                }
            }
            Button(
                onClick = { onDecision("accept", content.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("接受") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDecision("decline", null) }, modifier = Modifier.weight(1f)) {
                    Text("拒绝")
                }
                TextButton(onClick = { onDecision("cancel", null) }, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun PermissionsCard(
    request: PendingServerRequest.Permissions,
    onDecision: (grant: Boolean, session: Boolean, strictAutoReview: Boolean) -> Unit,
) {
    var strictAutoReview by rememberSaveable(request.requestKey) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("权限请求", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            request.reason?.let { Text(it) }
            Text(request.cwd, style = MaterialTheme.typography.labelSmall)
            PermissionSummary(request.requestedPermissions)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本回合后续命令严格自动审查", modifier = Modifier.weight(1f))
                Switch(checked = strictAutoReview, onCheckedChange = { strictAutoReview = it })
            }
            Button(
                onClick = { onDecision(true, false, strictAutoReview) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("允许一次") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onDecision(true, true, false) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("本会话允许")
                }
                TextButton(onClick = { onDecision(false, false, false) }, modifier = Modifier.weight(1f)) {
                    Text("拒绝")
                }
            }
        }
    }
}

@Composable
private fun ConversationAccessoryPanels(
    conversation: ConversationState,
    state: ChatUiState,
    onRetrySubAgents: () -> Unit,
    onRetryQueue: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onTerminateCommand: (String) -> Unit,
    onWithdrawQueue: (String, Boolean) -> Unit,
    onLocateQuestion: () -> Unit,
) {
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val bottomInset = maxOf(WindowInsets.ime.getBottom(density), WindowInsets.navigationBars.getBottom(density))
    val availableHeight = with(density) { (windowHeight - bottomInset).coerceAtLeast(0).toDp() }
    val connected = state.connectionStatus is ConnectionStatus.Connected
    key(conversation.serverId, conversation.threadId) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = minOf(320.dp, availableHeight * 0.4f))
                .verticalScroll(rememberScrollState()),
        ) {
            if (conversation.history.recentAsyncQuestion() != null) {
                TextButton(onClick = onLocateQuestion) { Text("查看最近提问") }
            }
            ThreadQueuePanel(
                threadId = conversation.threadId,
                queue = conversation.runtimeStatus.queue,
                connected = connected,
                enabled = !conversation.isViewOnly && !state.isSendingDraft && !state.isPreparingAttachment,
                canRestoreToComposer = !state.draft.hasContent,
                operationId = state.queueOperationId,
                onWithdraw = onWithdrawQueue,
                onRetry = onRetryQueue,
            )
            RuntimeStatusPanel(
                conversation = conversation,
                connected = connected,
                runtimeStatus = conversation.runtimeStatus,
            )
            if (conversation.isRunning) {
                conversation.plan?.takeIf { it.steps.isNotEmpty() }?.let { plan ->
                    key(conversation.activeTurnId) { ActivePlanCard(plan) }
                }
            }
            SubAgentsPanel(
                agents = state.subAgents.agents,
                stale = state.subAgents.isStale,
                error = state.subAgents.error,
                connected = connected,
                onRetry = onRetrySubAgents,
                onOpenConversation = onOpenConversation,
            )
            BackgroundCommandsPanel(
                commands = conversation.backgroundTerminals,
                error = conversation.backgroundTerminalsError,
                terminatingProcessIds = state.terminatingBackgroundProcessIds,
                onTerminate = onTerminateCommand,
                canTerminate = connected && !conversation.isViewOnly,
            )
        }
    }
}

@Composable
internal fun ConversationComposer(
    imageLoader: ChatImageLoader,
    onPreviewImage: (ChatImageSource) -> Unit,
    serverId: String?,
    conversationId: String?,
    shellTarget: ShellCommandTarget?,
    isRunning: Boolean,
    message: String,
    attachments: List<DraftAttachment>,
    modelLabel: String,
    resources: List<FeatureEntry>,
    quickPhrases: List<QuickPhrase>,
    isLoadingResources: Boolean,
    isSending: Boolean,
    sendEnabled: Boolean,
    placeholder: String,
    commands: List<ComposerCommand>,
    onLoadResources: (Boolean) -> Unit,
    onAddQuickPhrase: (String) -> Unit,
    onUpdateQuickPhrase: (String, String) -> Unit,
    onDeleteQuickPhrase: (String) -> Unit,
    onMoveQuickPhraseUp: (String) -> Unit,
    onMoveQuickPhraseDown: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onAddAttachment: (UserInputPart, String?) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onInputError: (Throwable) -> Unit,
    onOpenOptions: () -> Unit,
    onCommand: (ComposerCommand) -> Unit,
    onSend: () -> Unit,
    onSendQuickPhrase: (String) -> Unit,
    onStop: (() -> Unit)?,
    onQueue: (() -> Unit)? = null,
    onPreparingAttachmentChange: (Boolean) -> Unit = {},
) {
    var dialog by remember { mutableStateOf<ComposerDialog?>(null) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var showQuickPhrases by remember { mutableStateOf(false) }
    var showQuickPhraseManager by remember { mutableStateOf(false) }
    var isAddingImage by remember { mutableStateOf(false) }
    var composerValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = message,
                selection = TextRange(message.length),
            ),
        )
    }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val composerScope = rememberCoroutineScope()
    LaunchedEffect(message) {
        if (message != composerValue.text) {
            composerValue = TextFieldValue(
                text = message,
                selection = TextRange(message.length),
            )
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            composerScope.launch(start = CoroutineStart.UNDISPATCHED) {
                onPreparingAttachmentChange(true)
                isAddingImage = true
                try {
                    runCatching { uri.toImageDataUrl(context) }
                        .onSuccess { onAddAttachment(UserInputPart.Image(it), uri.toString()) }
                        .onFailure(onInputError)
                } finally {
                    isAddingImage = false
                    onPreparingAttachmentChange(false)
                }
            }
        }
    }
    val activeToken = composerValue.selection.takeIf { it.collapsed }?.let {
        activeComposerToken(composerValue.text, it.start)
    }
    val paletteEntries = remember(activeToken, resources, commands) {
        composerPaletteEntries(activeToken, resources, commands)
    }
    LaunchedEffect(activeToken?.trigger) {
        if (activeToken?.trigger == '$' || activeToken?.trigger == '@') onLoadResources(false)
    }
    val hasInput = composerValue.text.isNotBlank() || attachments.isNotEmpty()
    val isShellMode = shellTarget != null && isShellCommandInput(composerValue.text)
    val shellCommand = shellCommandFromInput(composerValue.text)
    val shellBlockReason = shellCommandBlockReason(
        message = composerValue.text,
        attachmentCount = attachments.size,
        isRunning = isRunning,
    )
    fun enterShellMode(nextMessage: String) {
        val blockReason = shellModeEntryBlockReason(nextMessage, attachments.size)
        if (blockReason != null) {
            onInputError(IllegalArgumentException(blockReason))
            return
        }
        composerValue = TextFieldValue("!", selection = TextRange(1))
        onMessageChange("!")
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    fun exitShellMode() {
        val nextMessage = messageAfterExitingShellMode(composerValue.text)
        composerValue = TextFieldValue(nextMessage, selection = TextRange(nextMessage.length))
        onMessageChange(nextMessage)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp),
    ) {
        if (!isSending && activeToken != null && (paletteEntries.isNotEmpty() || isLoadingResources)) {
            ComposerPalette(
                entries = paletteEntries,
                isLoading = isLoadingResources,
                onSelect = { entry ->
                    val nextMessage = removeActiveComposerToken(composerValue.text, activeToken)
                    composerValue = composerValue.copy(
                        text = nextMessage,
                        selection = TextRange(activeToken.start.coerceAtMost(nextMessage.length)),
                        composition = null,
                    )
                    onMessageChange(nextMessage)
                    when (entry) {
                        is ComposerPaletteEntry.Resource -> onAddAttachment(entry.input, null)
                        is ComposerPaletteEntry.Command -> when (entry.command) {
                            ComposerCommand.SHELL -> enterShellMode(nextMessage)
                            ComposerCommand.PHRASE -> {
                                showQuickPhrases = true
                                addMenuExpanded = true
                            }
                            ComposerCommand.SETTINGS -> {
                                onOpenOptions()
                            }
                            ComposerCommand.ATTACH -> addMenuExpanded = true
                            else -> onCommand(entry.command)
                        }
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
        }
        if (isShellMode) {
            ShellModeBanner(
                target = requireNotNull(shellTarget),
                blockReason = shellBlockReason,
                exitEnabled = !isSending,
                onExit = ::exitShellMode,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (attachments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                attachments.forEach { attachment ->
                    DraftAttachmentChip(
                        attachment = attachment,
                        imageLoader = imageLoader,
                        onPreviewImage = onPreviewImage,
                        onRemove = { onRemoveAttachment(attachment.id) },
                        modifier = Modifier.padding(end = 8.dp),
                        removeEnabled = !isSending,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        val compactEndExtra = (if (isAddingImage) 28.dp else 0.dp) +
            (if (isRunning && hasInput) 40.dp else 0.dp) +
            (if (onQueue != null && isRunning && hasInput && !isShellMode) 40.dp else 0.dp)
        MessageComposer(
            value = composerValue,
            onValueChange = {
                composerValue = it
                onMessageChange(it.text)
            },
            placeholder = if (isShellMode) "输入要在服务端执行的命令" else placeholder,
            enabled = !isSending,
            compactEndExtra = compactEndExtra,
            leadingContent = {
                if (isShellMode) {
                    Icon(
                        Icons.Rounded.Terminal,
                        contentDescription = "Shell 模式",
                        tint = MaterialTheme.statusColors.warning,
                    )
                } else {
                    ComposerAddMenu(
                        expanded = addMenuExpanded,
                        onExpandedChange = {
                            addMenuExpanded = it
                        },
                        showQuickPhrases = showQuickPhrases,
                        onShowQuickPhrases = { showQuickPhrases = it },
                        quickPhrases = quickPhrases,
                        quickPhraseSendEnabled = sendEnabled && !isSending,
                        enabled = !isSending,
                        shellEntryEnabled = !hasInput,
                        onEnterShell = shellTarget?.let {
                            { enterShellMode(composerValue.text) }
                        },
                        onPickImage = {
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onAddImageAddress = { dialog = ComposerDialog.ImageAddress },
                        onManageQuickPhrases = {
                            addMenuExpanded = false
                            showQuickPhraseManager = true
                        },
                        onSelectQuickPhrase = { phrase ->
                            addMenuExpanded = false
                            onSendQuickPhrase(phrase.content)
                        },
                        onInsertTrigger = { trigger ->
                            val nextValue = insertComposerTrigger(composerValue, trigger)
                            composerValue = nextValue
                            onMessageChange(nextValue.text)
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        },
                    )
                }
            },
            expandedContent = {
                if (isShellMode) {
                    Text(
                        text = "服务端 Shell · ${requireNotNull(shellTarget).serverName}",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    TextButton(
                        onClick = onOpenOptions,
                        enabled = !isRunning && !isSending,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(modelLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            trailingContent = {
                if (isAddingImage) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                if (isRunning && hasInput) {
                    IconButton(
                        onClick = requireNotNull(onStop),
                        enabled = !isSending,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = "停止")
                    }
                }
                if (onQueue != null && isRunning && hasInput && !isShellMode) {
                    IconButton(onClick = onQueue, enabled = sendEnabled && !isSending && !isAddingImage, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "排队到下一回合")
                    }
                }
                val stopsActiveTurn = isRunning && !hasInput
                val action = if (stopsActiveTurn) {
                    requireNotNull(onStop)
                } else {
                    onSend
                }
                MessageComposerActionButton(
                    onClick = action,
                    enabled = if (stopsActiveTurn) {
                        !isSending
                    } else {
                        hasInput && sendEnabled && !isSending && !isAddingImage &&
                            (!isShellMode || shellCommand != null && shellBlockReason == null)
                    },
                    action = if (stopsActiveTurn) {
                        MessageComposerAction.STOP
                    } else if (isShellMode) {
                        MessageComposerAction.EXECUTE
                    } else {
                        MessageComposerAction.SEND
                    },
                    isLoading = isSending,
                    contentDescription = when {
                        stopsActiveTurn -> "停止"
                        isShellMode -> "执行 Shell 命令"
                        else -> "发送"
                    },
                )
            },
            shadowElevation = 1.dp,
            focusRequester = focusRequester,
        )
    }

    if (showQuickPhraseManager) {
        QuickPhraseManagerDialog(
            phrases = quickPhrases,
            onDismiss = { showQuickPhraseManager = false },
            onAdd = onAddQuickPhrase,
            onUpdate = onUpdateQuickPhrase,
            onDelete = onDeleteQuickPhrase,
            onMoveUp = onMoveQuickPhraseUp,
            onMoveDown = onMoveQuickPhraseDown,
        )
    }

    when (dialog) {
        ComposerDialog.ImageAddress -> ImageAddressDialog(
            onDismiss = { dialog = null },
            onAdd = {
                onAddAttachment(it, null)
                dialog = null
            },
        )

        null -> Unit
    }
}

@Composable
private fun ShellModeBanner(
    target: ShellCommandTarget,
    blockReason: String?,
    exitEnabled: Boolean,
    onExit: () -> Unit,
) {
    val color = if (blockReason == null) MaterialTheme.statusColors.warning else MaterialTheme.colorScheme.error
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, color.copy(alpha = 0.58f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Rounded.Terminal,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "服务端 Shell · ${target.serverName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                )
                Text(
                    target.cwd,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    blockReason ?: "命令将在服务端主机直接执行，不受线程沙箱和审批策略限制",
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
            IconButton(
                onClick = onExit,
                enabled = exitEnabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "退出 Shell 模式",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

internal fun composerModelLabel(
    model: String?,
    reasoningEffort: String?,
    models: List<ModelOption>,
): String {
    val selectedModel = models.firstOrNull { it.model == model }
    return listOfNotNull(
        selectedModel?.displayName ?: model ?: "默认模型",
        reasoningEffort,
    ).joinToString(" · ")
}

@Composable
private fun ActivePlanCard(plan: ActiveTurnPlan) {
    val currentStepNumber = activePlanStepNumber(plan.steps)
    val progressLabel = "$currentStepNumber/${plan.steps.size}"
    val summary = plan.steps.firstOrNull { it.status == PlanStepStatus.IN_PROGRESS }?.text
        ?: plan.steps.firstOrNull { it.status == PlanStepStatus.PENDING }?.text
        ?: "计划步骤已完成"
    var expanded by remember { mutableStateOf(false) }
    val expansionTarget = expanded
    val expansionPhase = rememberContentExpansionPhase(expansionTarget)
    val bodyAlpha by animateFloatAsState(
        targetValue = if (expansionPhase.bodyVisible) 1f else 0f,
        animationSpec = tween(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS),
        label = "工作计划内容透明度",
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (expansionTarget) 180f else 0f,
        animationSpec = tween(EXPANDABLE_CONTENT_FADE_DURATION_MILLIS),
        label = "工作计划展开箭头",
    )

    Surface(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.animateContentSize(
                animationSpec = tween(EXPANDABLE_CONTENT_SIZE_ANIMATION_DURATION_MILLIS),
            ),
        ) {
            Row(
                modifier = Modifier
                    .then(if (expansionPhase.bodyIncluded) Modifier.fillMaxWidth() else Modifier)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics {
                        stateDescription = if (expansionTarget) "已展开" else "已收起"
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 24.dp)
                        .clearAndSetSemantics {
                            contentDescription = "第 $currentStepNumber 步，共 ${plan.steps.size} 步"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = progressLabel,
                        animationSpec = tween(PLAN_STEP_PROGRESS_FADE_DURATION_MILLIS),
                        label = "工作计划步骤进度",
                    ) { visibleProgress ->
                        Text(
                            text = visibleProgress,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = summary,
                    modifier = Modifier.widthIn(max = 400.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(arrowRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (expansionPhase.bodyIncluded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .graphicsLayer { alpha = bodyAlpha },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    plan.explanation?.takeIf(String::isNotBlank)?.let { explanation ->
                        Text(
                            text = explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    plan.steps.forEach { step ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            val (icon, description) = when (step.status) {
                                PlanStepStatus.COMPLETED -> Icons.Rounded.Check to "已完成"
                                PlanStepStatus.IN_PROGRESS -> Icons.Rounded.HourglassTop to "进行中"
                                PlanStepStatus.PENDING -> Icons.Rounded.RadioButtonUnchecked to "待处理"
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = description,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = step.text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun activePlanStepNumber(steps: List<TurnPlanStep>): Int {
    require(steps.isNotEmpty())
    val currentStepIndex = steps.indexOfFirst { it.status == PlanStepStatus.IN_PROGRESS }
        .takeIf { it >= 0 }
        ?: steps.indexOfFirst { it.status == PlanStepStatus.PENDING }.takeIf { it >= 0 }
        ?: steps.lastIndex
    return currentStepIndex + 1
}

@Composable
private fun ComposerAddMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    showQuickPhrases: Boolean,
    onShowQuickPhrases: (Boolean) -> Unit,
    quickPhrases: List<QuickPhrase>,
    quickPhraseSendEnabled: Boolean,
    enabled: Boolean,
    shellEntryEnabled: Boolean,
    onEnterShell: (() -> Unit)?,
    onPickImage: () -> Unit,
    onAddImageAddress: () -> Unit,
    onManageQuickPhrases: () -> Unit,
    onSelectQuickPhrase: (QuickPhrase) -> Unit,
    onInsertTrigger: (Char) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { nextExpanded ->
            if (nextExpanded) onShowQuickPhrases(false)
            onExpandedChange(nextExpanded)
        },
    ) {
        val addButtonColors = IconButtonDefaults.iconButtonColors()
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "更多输入方式",
            modifier = Modifier
                .size(48.dp)
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.SecondaryEditable,
                    enabled = enabled,
                )
                .padding(12.dp),
            tint = if (enabled) addButtonColors.contentColor else addButtonColors.disabledContentColor,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.widthIn(min = 280.dp, max = 400.dp),
            matchAnchorWidth = false,
        ) {
            if (showQuickPhrases) {
                QuickPhraseMenuContent(
                    phrases = quickPhrases,
                    selectionEnabled = quickPhraseSendEnabled,
                    onBack = { onShowQuickPhrases(false) },
                    onManage = onManageQuickPhrases,
                    onSelect = onSelectQuickPhrase,
                )
            } else {
                DropdownMenuItem(
                    text = { Text("快捷短语") },
                    leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
                    onClick = { onShowQuickPhrases(true) },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("设备图片") },
                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onPickImage()
                    },
                )
                DropdownMenuItem(
                    text = { Text("图片地址") },
                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onAddImageAddress()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Skill") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onInsertTrigger('$')
                    },
                )
                DropdownMenuItem(
                    text = { Text("App 与 Plugin") },
                    leadingIcon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onInsertTrigger('@')
                    },
                )
                if (onEnterShell != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("服务端 Shell")
                                Text(
                                    if (shellEntryEnabled) {
                                        "在当前服务器直接执行命令"
                                    } else {
                                        "请先清空输入与附件"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                        enabled = shellEntryEnabled,
                        onClick = {
                            onExpandedChange(false)
                            onEnterShell()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageAddressDialog(
    onDismiss: () -> Unit,
    onAdd: (UserInputPart) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(ManualAttachment.IMAGE_URL) }
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加图片地址") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OptionChipRow(
                    values = ManualAttachment.entries,
                    selected = kind,
                    label = { if (it == ManualAttachment.IMAGE_URL) "图片 URL" else "服务端图片" },
                    onSelect = { kind = it },
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (kind == ManualAttachment.IMAGE_URL) "URL 或 data URL" else "服务端绝对路径",
                        )
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(
                        if (kind == ManualAttachment.IMAGE_URL) UserInputPart.Image(value.trim())
                        else UserInputPart.LocalImage(value.trim()),
                    )
                },
                enabled = value.isNotBlank(),
            ) {
                Text("添加")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TurnOptionsSheet(
    value: TurnOptions,
    modelListState: ModelListState,
    onDismiss: () -> Unit,
    onRetryModels: () -> Unit,
    onSave: (TurnOptions) -> Unit,
) {
    var options by remember(value) { mutableStateOf(value) }
    var writableRoots by remember(value) { mutableStateOf(value.sandboxWritableRoots.joinToString("\n")) }
    var showAdvanced by rememberSaveable(value) { mutableStateOf(false) }
    val models = (modelListState as? ModelListState.Ready)?.models.orEmpty()
    val selectableModels = models.filterNot(ModelOption::hidden)
    val model = selectableModels.firstOrNull { it.model == options.model }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("下一轮设置", style = MaterialTheme.typography.titleLarge)
            Text(
                "设置仅用于下一次发送，服务端确认接收后恢复会话默认值",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("模型", style = MaterialTheme.typography.labelLarge)
            when (modelListState) {
                ModelListState.Idle,
                ModelListState.Loading,
                -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("正在加载模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                is ModelListState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ExpandableErrorDetails(modelListState.error)
                    TextButton(onClick = onRetryModels) { Text("重试") }
                }

                is ModelListState.Ready -> if (selectableModels.isEmpty()) {
                    Text("当前服务端没有可选模型", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OptionChipRow(
                        values = listOf<ModelOption?>(null) + selectableModels,
                        selected = model,
                        label = { it?.displayName ?: "会话默认" },
                        onSelect = { selected ->
                            options = options.copy(
                                model = selected?.model,
                                serviceTier = selected?.defaultServiceTier,
                                reasoningEffort = selected?.defaultReasoningEffort,
                            )
                        },
                    )
                }
            }
            model?.reasoningEfforts?.takeIf(List<*>::isNotEmpty)?.let { efforts ->
                Text("推理强度", style = MaterialTheme.typography.labelLarge)
                OptionChipRow(
                    values = listOf<String?>(null) + efforts.map { it.value },
                    selected = options.reasoningEffort,
                    label = { effort -> effort ?: "默认" },
                    onSelect = { options = options.copy(reasoningEffort = it) },
                )
            }
            Text("审批者", style = MaterialTheme.typography.labelLarge)
            OptionChipRow(
                values = listOf<ApprovalReviewer?>(null) + ApprovalReviewer.entries,
                selected = options.approvalsReviewer,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(approvalsReviewer = it) },
            )
            options.approvalsReviewer?.let { SettingDescription(stringResource(it.displayDescriptionRes)) }
            Text("审批策略", style = MaterialTheme.typography.labelLarge)
            OptionChipRow(
                values = listOf<ApprovalPolicy?>(null) + ApprovalPolicy.entries,
                selected = options.approvalPolicy,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(approvalPolicy = it) },
            )
            options.approvalPolicy?.let { SettingDescription(stringResource(it.displayDescriptionRes)) }
            Text("沙箱", style = MaterialTheme.typography.labelLarge)
            OptionChipRow(
                values = listOf<SandboxMode?>(null) + SandboxMode.entries,
                selected = options.sandboxMode,
                label = { it?.let { value -> stringResource(value.displayLabelRes) }
                    ?: stringResource(R.string.common_default) },
                onSelect = { options = options.copy(sandboxMode = it) },
            )
            options.sandboxMode?.let {
                SettingDescription(
                    stringResource(it.displayDescriptionRes),
                    critical = it == SandboxMode.DANGER_FULL_ACCESS,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showAdvanced) "收起高级设置" else "展开高级设置")
            }
            if (showAdvanced) {
                OutlinedTextField(
                    value = options.cwd.orEmpty(),
                    onValueChange = { options = options.copy(cwd = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("工作目录覆盖，可留空") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = options.model.orEmpty(),
                    onValueChange = { options = options.copy(model = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型 ID，可留空") },
                    singleLine = true,
                )
                model?.serviceTiers?.takeIf(List<*>::isNotEmpty)?.let { tiers ->
                    Text("服务层级", style = MaterialTheme.typography.labelLarge)
                    OptionChipRow(
                        values = listOf<String?>(null) + tiers.map { it.value },
                        selected = options.serviceTier,
                        label = { tier -> tiers.firstOrNull { it.value == tier }?.label ?: "默认" },
                        onSelect = { options = options.copy(serviceTier = it) },
                    )
                }
                OutlinedTextField(
                    value = options.serviceTier.orEmpty(),
                    onValueChange = { options = options.copy(serviceTier = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("服务层级，可留空") },
                    singleLine = true,
                )
                Text("推理摘要", style = MaterialTheme.typography.labelLarge)
                OptionChipRow(
                    values = listOf<ReasoningSummary?>(null) + ReasoningSummary.entries,
                    selected = options.reasoningSummary,
                    label = { it?.let { value -> stringResource(value.displayLabelRes) }
                        ?: stringResource(R.string.common_default) },
                    onSelect = { options = options.copy(reasoningSummary = it) },
                )
                if (model == null || model.supportsPersonality) {
                    Text("人格", style = MaterialTheme.typography.labelLarge)
                    OptionChipRow(
                        values = listOf<Personality?>(null) + Personality.entries,
                        selected = options.personality,
                        label = { it?.let { value -> stringResource(value.displayLabelRes) }
                            ?: stringResource(R.string.common_default) },
                        onSelect = { options = options.copy(personality = it) },
                    )
                }
                if (options.sandboxMode == SandboxMode.READ_ONLY || options.sandboxMode == SandboxMode.WORKSPACE_WRITE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("允许网络访问", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxNetworkAccess,
                            onCheckedChange = { options = options.copy(sandboxNetworkAccess = it) },
                        )
                    }
                }
                if (options.sandboxMode == SandboxMode.WORKSPACE_WRITE) {
                    OutlinedTextField(
                        value = writableRoots,
                        onValueChange = { writableRoots = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("额外可写绝对路径，每行一个") },
                        minLines = 2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("排除 TMPDIR", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxExcludeTmpdirEnvVar,
                            onCheckedChange = { options = options.copy(sandboxExcludeTmpdirEnvVar = it) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("排除 /tmp", modifier = Modifier.weight(1f))
                        Switch(
                            checked = options.sandboxExcludeSlashTmp,
                            onCheckedChange = { options = options.copy(sandboxExcludeSlashTmp = it) },
                        )
                    }
                }
                OutlinedTextField(
                    value = options.outputSchema.orEmpty(),
                    onValueChange = { options = options.copy(outputSchema = it.ifBlank { null }) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("结构化输出 JSON Schema，可留空") },
                    minLines = 3,
                )
            }
            Button(
                onClick = {
                    onSave(
                        options.copy(
                            sandboxWritableRoots = writableRoots.lineSequence()
                                .map(String::trim)
                                .filter(String::isNotEmpty)
                                .toList(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("应用设置") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RenameConversationDialog(
    title: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by rememberSaveable(title) { mutableStateOf(title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("会话名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(name.trim()) },
                enabled = name.isNotBlank() && name.trim() != title,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ForkConversationSheet(
    turns: List<ForkTurn>,
    initialLastTurnId: String?,
    isRunning: Boolean,
    forkRequest: ForkRequest?,
    onDismiss: () -> Unit,
    onFork: (String?) -> Unit,
) {
    val canForkLatest = !isRunning
    var lastTurnId by rememberSaveable(initialLastTurnId) {
        mutableStateOf(initialLastTurnId ?: if (canForkLatest) null else turns.lastOrNull()?.turnId)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("分叉会话", style = MaterialTheme.typography.titleLarge)
            Text(
                "这会创建独立的新对话，后续消息不会追加到原会话。选择新对话包含的最后一个回合。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canForkLatest) {
                ForkSelectionCard(
                    title = "复制最新状态",
                    supportingText = "包含当前会话的全部已完成回合",
                    selected = lastTurnId == null,
                    onClick = { lastTurnId = null },
                )
            } else {
                Text(
                    "当前回合正在执行，仅可选择已完成回合",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (turns.isNotEmpty()) {
                Text("选择历史回合", style = MaterialTheme.typography.titleSmall)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(turns.asReversed(), key = ForkTurn::turnId) { turn ->
                    ForkSelectionCard(
                        title = turn.title,
                        supportingText = if (turn.attachmentCount > 0) {
                            "${turn.attachmentCount} 个附件"
                        } else {
                            "已完成的历史回合"
                        },
                        selected = lastTurnId == turn.turnId,
                        onClick = { lastTurnId = turn.turnId },
                    )
                }
            }
            Button(
                onClick = { onFork(lastTurnId) },
                enabled = forkRequest == null &&
                    (lastTurnId?.let { id -> turns.any { it.turnId == id } } ?: canForkLatest),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("创建并打开新对话")
            }
            TextButton(
                onClick = onDismiss,
                enabled = forkRequest == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ForkSelectionCard(
    title: String,
    supportingText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ConversationStatisticsPopup(
    usage: TokenUsage?,
    onDismiss: () -> Unit,
) {
    AnchoredDetailsPopup(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(ConversationStatisticsPopupWidth),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("对话统计", style = MaterialTheme.typography.titleMedium)
            if (usage == null) {
                Text(
                    "暂无统计数据",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ContextUsageSection(usage)
                HorizontalDivider()
                TokenUsageComparisonChart(usage)
            }
        }
    }
}

@Composable
private fun ContextUsageSection(usage: TokenUsage) {
    val contextWindow = usage.modelContextWindow?.takeIf { it > 0 }
    val contextTokens = usage.last.totalTokens
    Text("当前上下文", style = MaterialTheme.typography.titleMedium)
    if (contextWindow == null) {
        Text(
            "已用 ${formatCompactCount(contextTokens)} Token",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        val remainingTokens = (contextWindow - contextTokens).coerceIn(0L, contextWindow)
        val remainingRatio = remainingTokens.toFloat() / contextWindow
        Text(
            "剩余 ${formatCompactCount(remainingTokens)} / ${formatCompactCount(contextWindow)} Token" +
                " · ${formatPercent(remainingRatio * 100.0)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        RemainingLinearProgressIndicator(
            remainingRatio = remainingRatio,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TokenUsageComparisonChart(
    usage: TokenUsage,
) {
    val rows = tokenUsageComparisonRows(usage)
    val axisMaximum = rows.maxOf(TokenUsageComparisonRow::cumulativeTokens)
    val previousColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)
    val latestColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f)
    Text("Token 用量", style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TokenUsageLegend(color = previousColor, label = "此前调用")
        Spacer(Modifier.width(12.dp))
        TokenUsageLegend(color = latestColor, label = "最近一次")
    }
    rows.forEach { row ->
        Column(
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "${row.label}，会话累计 ${formatCompactCount(row.cumulativeTokens)} Token，" +
                    "最近一次 ${formatCompactCount(row.latestTokens)} Token"
            },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (row.isDetail) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.isDetail) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    "累计 ${formatCompactCount(row.cumulativeTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "最近 ${formatCompactCount(row.latestTokens)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                if (axisMaximum > 0L && row.cumulativeTokens > 0L) {
                    val cumulativeWidth = size.width * row.cumulativeTokens.toFloat() / axisMaximum.toFloat()
                    val previousWidth = size.width * row.previousTokens.toFloat() / axisMaximum.toFloat()
                    drawRoundRect(
                        color = previousColor,
                        size = androidx.compose.ui.geometry.Size(cumulativeWidth, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                    )
                    if (row.latestTokens > 0L) {
                        clipRect(left = previousWidth, right = cumulativeWidth) {
                            drawRoundRect(
                                color = latestColor,
                                size = androidx.compose.ui.geometry.Size(cumulativeWidth, size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenUsageLegend(color: androidx.compose.ui.graphics.Color, label: String) {
    Box(Modifier.size(8.dp).background(color, CircleShape))
    Spacer(Modifier.width(5.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal data class TokenUsageComparisonRow(
    val label: String,
    val cumulativeTokens: Long,
    val latestTokens: Long,
    val isDetail: Boolean = false,
) {
    val previousTokens: Long = cumulativeTokens - latestTokens

    init {
        require(latestTokens >= 0L) { "$label 的最近一次 Token 不能为负数" }
        require(previousTokens >= 0L) { "$label 的最近一次 Token 不能超过会话累计 Token" }
    }
}

internal fun tokenUsageComparisonRows(usage: TokenUsage): List<TokenUsageComparisonRow> = listOf(
    TokenUsageComparisonRow("总计", usage.total.totalTokens, usage.last.totalTokens),
    TokenUsageComparisonRow("输入", usage.total.inputTokens, usage.last.inputTokens),
    TokenUsageComparisonRow("其中缓存", usage.total.cachedInputTokens, usage.last.cachedInputTokens, isDetail = true),
    TokenUsageComparisonRow("输出", usage.total.outputTokens, usage.last.outputTokens),
    TokenUsageComparisonRow(
        "其中推理",
        usage.total.reasoningOutputTokens,
        usage.last.reasoningOutputTokens,
        isDetail = true,
    ),
)

@Composable
private fun ReviewDialog(
    onDismiss: () -> Unit,
    onSubmit: (ReviewTarget, ReviewDelivery) -> Unit,
) {
    var type by rememberSaveable { mutableStateOf(ReviewType.UNCOMMITTED) }
    var value by rememberSaveable { mutableStateOf("") }
    var detached by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("代码审查") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OptionChipRow(
                    values = ReviewType.entries,
                    selected = type,
                    label = { it.label },
                    onSelect = { type = it },
                )
                if (type != ReviewType.UNCOMMITTED) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(type.inputLabel) },
                        minLines = if (type == ReviewType.CUSTOM) 3 else 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("在独立会话中审查", modifier = Modifier.weight(1f))
                    Switch(checked = detached, onCheckedChange = { detached = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = when (type) {
                        ReviewType.UNCOMMITTED -> ReviewTarget.UncommittedChanges
                        ReviewType.BASE_BRANCH -> ReviewTarget.BaseBranch(value.trim())
                        ReviewType.COMMIT -> ReviewTarget.Commit(value.trim())
                        ReviewType.CUSTOM -> ReviewTarget.Custom(value.trim())
                    }
                    onSubmit(target, if (detached) ReviewDelivery.DETACHED else ReviewDelivery.INLINE)
                },
                enabled = type == ReviewType.UNCOMMITTED || value.isNotBlank(),
            ) { Text("开始") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun GoalDialog(
    goal: ThreadGoal?,
    onDismiss: () -> Unit,
    onSave: (String, ThreadGoalStatus, Long?) -> Unit,
    onClear: () -> Unit,
) {
    var objective by rememberSaveable(goal?.objective) { mutableStateOf(goal?.objective.orEmpty()) }
    var budget by rememberSaveable(goal?.tokenBudget) { mutableStateOf(goal?.tokenBudget?.toString().orEmpty()) }
    var status by rememberSaveable(goal?.status) { mutableStateOf(goal?.status ?: ThreadGoalStatus.ACTIVE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目标管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标") },
                    minLines = 3,
                )
                Text("状态", style = MaterialTheme.typography.labelLarge)
                OptionChipRow(
                    values = ThreadGoalStatus.entries,
                    selected = status,
                    label = { stringResource(it.displayLabelRes) },
                    onSelect = { status = it },
                )
                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Token 预算，可留空") },
                    singleLine = true,
                )
                if (goal != null) TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("清除目标") }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(objective.trim(), status, budget.toLongOrNull()) }, enabled = objective.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private sealed interface ChatDialog {
    data object Rename : ChatDialog
    data object Fork : ChatDialog
    data object Review : ChatDialog
    data object Goal : ChatDialog
}

private enum class ComposerDialog {
    ImageAddress,
}

private enum class ManualAttachment {
    IMAGE_URL,
    LOCAL_IMAGE,
}

private enum class ReviewType(val label: String, val inputLabel: String) {
    UNCOMMITTED("未提交修改", ""),
    BASE_BRANCH("相对分支", "基础分支"),
    COMMIT("提交", "提交 SHA"),
    CUSTOM("自定义", "审查说明"),
}
