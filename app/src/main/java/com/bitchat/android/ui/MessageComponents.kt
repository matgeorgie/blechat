package com.bitchat.android.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.content.Intent
import android.net.Uri
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import com.bitchat.android.mesh.BluetoothMeshService
import java.text.SimpleDateFormat
import java.util.*
import com.bitchat.android.ui.media.VoiceNotePlayer
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.bitchat.android.ui.media.FileMessageItem
import com.bitchat.android.model.BitchatMessageType
import com.bitchat.android.R
import androidx.compose.ui.res.stringResource
import com.bitchat.android.ui.theme.ChatFontFamily
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


// VoiceNotePlayer moved to com.bitchat.android.ui.media.VoiceNotePlayer

/**
 * Message display components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */

@Composable
fun MessagesList(
    messages: List<BitchatMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    modifier: Modifier = Modifier,
    forceScrollToBottom: Boolean = false,
    onScrolledUpChanged: ((Boolean) -> Unit)? = null,
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((BitchatMessage) -> Unit)? = null,
    onReplySwipe: ((BitchatMessage) -> Unit)? = null,
    onCancelTransfer: ((BitchatMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    
    // Track if this is the first time messages are being loaded
    var hasScrolledToInitialPosition by remember { mutableStateOf(false) }
    var followIncomingMessages by remember { mutableStateOf(true) }
    
    // Smart scroll: auto-scroll to bottom for initial load, then follow unless user scrolls away
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val isFirstLoad = !hasScrolledToInitialPosition
            if (isFirstLoad || followIncomingMessages) {
                listState.scrollToItem(0)
                if (isFirstLoad) {
                    hasScrolledToInitialPosition = true
                }
            }
        }
    }
    
    // Track whether user has scrolled away from the latest messages
    val isAtLatest by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1
            firstVisibleIndex <= 2
        }
    }
    LaunchedEffect(isAtLatest) {
        followIncomingMessages = isAtLatest
        onScrolledUpChanged?.invoke(!isAtLatest)
    }
    
    // Force scroll to bottom when requested (e.g., when user sends a message)
    LaunchedEffect(forceScrollToBottom) {
        if (messages.isNotEmpty()) {
            // With reverseLayout=true and reversed data, latest is at index 0
            followIncomingMessages = true
            listState.scrollToItem(0)
        }
    }
    
    val renderedMessages = remember(messages) { messages.asReversed() }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier,
        reverseLayout = true
    ) {
        itemsIndexed(
            items = renderedMessages,
            key = { _, item -> item.id }
        ) { index, message ->
            MessageItem(
                message = message,
                messages = messages,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                grouping = messageGrouping(renderedMessages, index),
                onNicknameClick = onNicknameClick,
                onMessageLongPress = onMessageLongPress,
                onReplySwipe = onReplySwipe,
                onCancelTransfer = onCancelTransfer,
                onImageClick = onImageClick
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: BitchatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    messages: List<BitchatMessage> = emptyList(),
    grouping: MessageGrouping = MessageGrouping(),
    onNicknameClick: ((String) -> Unit)? = null,
    onMessageLongPress: ((BitchatMessage) -> Unit)? = null,
    onReplySwipe: ((BitchatMessage) -> Unit)? = null,
    onCancelTransfer: ((BitchatMessage) -> Unit)? = null,
    onImageClick: ((String, List<String>, Int) -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val isSelf = message.senderPeerID == meshService.myPeerID || 
                 message.sender == currentUserNickname ||
                 message.sender.startsWith("$currentUserNickname#")
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    val bubbleColor = when {
        message.sender == "system" -> Color.Transparent
        isSelf -> if (isDark) Color(0xFF1565C0) else Color(0xFF1976D2)
        else   -> if (isDark) Color(0xFF2D2D2D) else Color(0xFFECECEC)
    }
    val bubbleTextColor = when {
        message.sender == "system" -> Color.Gray
        isSelf -> Color.White
        else -> if (isDark) Color(0xFFF2F2F2) else Color(0xFF1A1A1A)
    }
    val swipeOffset = remember(message.id) { Animatable(0f) }
    val density = LocalDensity.current
    val swipeScope = rememberCoroutineScope()
    val swipeThresholdPx = with(density) { 56.dp.toPx() }
    val swipeMaxPx = with(density) { 72.dp.toPx() }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = grouping.topSpacing,
                bottom = grouping.bottomSpacing
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (swipeOffset.value > 2f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    contentAlignment = if (isSelf) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Text(
                        text = "\u21A9",
                        color = if (isSelf) bubbleTextColor.copy(alpha = 0.82f) else colorScheme.primary.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.graphicsLayer(
                            alpha = (swipeOffset.value / swipeThresholdPx).coerceIn(0.2f, 1f)
                        )
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                    .pointerInput(message.id) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                swipeScope.launch {
                                    swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(0f, swipeMaxPx))
                                }
                            },
                            onDragEnd = {
                                val shouldReply = swipeOffset.value >= swipeThresholdPx
                                swipeScope.launch {
                                    swipeOffset.animateTo(0f, tween(durationMillis = 180))
                                }
                                if (shouldReply) {
                                    onReplySwipe?.invoke(message)
                                }
                            },
                            onDragCancel = {
                                swipeScope.launch {
                                    swipeOffset.animateTo(0f, tween(durationMillis = 180))
                                }
                            }
                        )
                    },
                horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                // Max 75% screen width for bubble — WhatsApp style
                val endPad = if (message.isPrivate && message.sender == currentUserNickname) 16.dp else 0.dp
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = bubbleColor,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .wrapContentWidth(if (isSelf) Alignment.End else Alignment.Start)
                        .padding(end = endPad)
                ) {
                    MessageTextWithClickableNicknames(
                        message = message,
                        messages = messages,
                        currentUserNickname = currentUserNickname,
                        meshService = meshService,
                        colorScheme = colorScheme,
                        timeFormatter = timeFormatter,
                        onNicknameClick = onNicknameClick,
                        onMessageLongPress = onMessageLongPress,
                        onCancelTransfer = onCancelTransfer,
                        onImageClick = onImageClick,
                        isSelf = isSelf,
                        bubbleTextColor = bubbleTextColor,
                        modifier = Modifier
                            .padding(
                                horizontal = if (isSelf) 12.dp else 14.dp,
                                vertical = if (isSelf) 8.dp else 10.dp
                            )
                    )
                }
            }

            // Delivery status for private messages (overlay, non-displacing)
            if (message.isPrivate && message.sender == currentUserNickname) {
                message.deliveryStatus?.let { status ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp)
                    ) {
                        DeliveryStatusIcon(status = status)
                    }
                }
            }
        }

        // Link previews removed; links are now highlighted inline and clickable within the message text
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
    private fun MessageTextWithClickableNicknames(
        message: BitchatMessage,
        messages: List<BitchatMessage>,
        currentUserNickname: String,
        meshService: BluetoothMeshService,
        colorScheme: ColorScheme,
        timeFormatter: SimpleDateFormat,
        onNicknameClick: ((String) -> Unit)?,
        onMessageLongPress: ((BitchatMessage) -> Unit)?,
        onCancelTransfer: ((BitchatMessage) -> Unit)?,
        onImageClick: ((String, List<String>, Int) -> Unit)?,
        isSelf: Boolean = false,
        bubbleTextColor: Color = colorScheme.onSurface,
        modifier: Modifier = Modifier
    ) {
    // Image special rendering
    if (message.type == BitchatMessageType.Image) {
        com.bitchat.android.ui.media.ImageMessageItem(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            onImageClick = onImageClick,
            modifier = modifier
        )
        return
    }

    // Voice note special rendering
    if (message.type == BitchatMessageType.Audio) {
        com.bitchat.android.ui.media.AudioMessageItem(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onCancelTransfer = onCancelTransfer,
            modifier = modifier
        )
        return
    }

    // File special rendering
    if (message.type == BitchatMessageType.File) {
        val path = message.content.trim()
        // Derive sending progress if applicable
        val (overrideProgress, _) = when (val st = message.deliveryStatus) {
            is com.bitchat.android.model.DeliveryStatus.PartiallyDelivered -> {
                if (st.total > 0 && st.reached < st.total) {
                    (st.reached.toFloat() / st.total.toFloat()) to Color(0xFF1E88E5) // blue while sending
                } else null to null
            }
            else -> null to null
        }
        Column(modifier = modifier.fillMaxWidth()) {
            // Header: nickname + timestamp line above the file, identical styling to text messages
            val headerText = formatMessageHeaderAnnotatedString(
                message = message,
                currentUserNickname = currentUserNickname,
                meshService = meshService,
                colorScheme = colorScheme,
                timeFormatter = timeFormatter
            )
            val haptic = LocalHapticFeedback.current
            var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = headerText,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurface,
                modifier = Modifier.pointerInput(message.id) {
                    detectTapGestures(onTap = { pos ->
                        val layout = headerLayout ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(pos)
                        val ann = headerText.getStringAnnotations("nickname_click", offset, offset)
                        if (ann.isNotEmpty() && onNicknameClick != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNicknameClick.invoke(ann.first().item)
                        }
                    }, onLongPress = { onMessageLongPress?.invoke(message) })
                },
                onTextLayout = { headerLayout = it }
            )

            // Try to load the file packet from the path
            val packet = try {
                val file = java.io.File(path)
                if (file.exists()) {
                    // Create a temporary BitchatFilePacket for display
                    // In a real implementation, this would be stored with the packet metadata
                    com.bitchat.android.model.BitchatFilePacket(
                        fileName = file.name,
                        fileSize = file.length(),
                        mimeType = com.bitchat.android.features.file.FileUtils.getMimeTypeFromExtension(file.name),
                        content = file.readBytes()
                    )
                } else null
            } catch (e: Exception) {
                null
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Box {
                    if (packet != null) {
                        if (overrideProgress != null) {
                            // Show sending animation while in-flight
                            com.bitchat.android.ui.media.FileSendingAnimation(
                                fileName = packet.fileName,
                                progress = overrideProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Static file display with open/save dialog
                            FileMessageItem(
                                packet = packet,
                                onFileClick = {
                                    // handled inside FileMessageItem via dialog
                                }
                            )
                        }

                        // Cancel button overlay during sending
                        val showCancel = message.sender == currentUserNickname && (message.deliveryStatus is DeliveryStatus.PartiallyDelivered)
                        if (showCancel) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .background(Color.Gray.copy(alpha = 0.6f), CircleShape)
                                    .clickable { onCancelTransfer?.invoke(message) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.cd_cancel), tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    } else {
                        Text(text = stringResource(R.string.file_unavailable), fontFamily = FontFamily.Monospace, color = Color.Gray)
                    }
                }
            }
        }
        return
    }

    // Check if this message should be animated during PoW mining
    val shouldAnimate = shouldAnimateMessage(message.id)
    val parsedReply = remember(message.content) { parseReplyMessageContent(message.content) }
    
    // If animation is needed, use the matrix animation component for content only
    if (shouldAnimate) {
        // Display message with matrix animation for content
        MessageWithMatrixAnimation(
            message = message,
            messages = messages,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter,
            onNicknameClick = onNicknameClick,
            onMessageLongPress = onMessageLongPress,
            onImageClick = onImageClick,
            modifier = modifier
        )
    } else {
        // Normal message display
        val displayContent = parsedReply?.body?.takeIf { it.isNotBlank() } ?: message.content
        val displayMessage = if (displayContent == message.content) message else message.copy(content = displayContent)
        val annotatedText = formatMessageAsAnnotatedString(
            message = displayMessage,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter
        )
        
        // Check if this message was sent by self to avoid click interactions on own nickname
        val isMessageSelf = message.senderPeerID == meshService.myPeerID || 
                     message.sender == currentUserNickname ||
                     message.sender.startsWith("$currentUserNickname#")
        
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        
        Column(
            modifier = modifier
                .pointerInput(message) {
                detectTapGestures(
                    onTap = { position ->
                        val layout = textLayoutResult ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(position)
                        // Nickname click only when not self
                        if (!isMessageSelf && onNicknameClick != null) {
                            val nicknameAnnotations = annotatedText.getStringAnnotations(
                                tag = "nickname_click",
                                start = offset,
                                end = offset
                            )
                            if (nicknameAnnotations.isNotEmpty()) {
                                val nickname = nicknameAnnotations.first().item
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNicknameClick.invoke(nickname)
                                return@detectTapGestures
                            }
                        }
                        // Geohash teleport (all messages)
                        val geohashAnnotations = annotatedText.getStringAnnotations(
                            tag = "geohash_click",
                            start = offset,
                            end = offset
                        )
                        if (geohashAnnotations.isNotEmpty()) {
                            val geohash = geohashAnnotations.first().item
                            try {
                                val locationManager = com.bitchat.android.geohash.LocationChannelManager.getInstance(
                                    context
                                )
                                val level = when (geohash.length) {
                                    in 0..2 -> com.bitchat.android.geohash.GeohashChannelLevel.REGION
                                    in 3..4 -> com.bitchat.android.geohash.GeohashChannelLevel.PROVINCE
                                    5 -> com.bitchat.android.geohash.GeohashChannelLevel.CITY
                                    6 -> com.bitchat.android.geohash.GeohashChannelLevel.NEIGHBORHOOD
                                    else -> com.bitchat.android.geohash.GeohashChannelLevel.BLOCK
                                }
                                val channel = com.bitchat.android.geohash.GeohashChannel(level, geohash.lowercase())
                                locationManager.setTeleported(true)
                                locationManager.select(com.bitchat.android.geohash.ChannelID.Location(channel))
                            } catch (_: Exception) { }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            return@detectTapGestures
                        }
                        // URL open (all messages)
                        val urlAnnotations = annotatedText.getStringAnnotations(
                            tag = "url_click",
                            start = offset,
                            end = offset
                        )
                        if (urlAnnotations.isNotEmpty()) {
                            val raw = urlAnnotations.first().item
                            val resolved = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) raw else "https://$raw"
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resolved))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) { }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            return@detectTapGestures
                        }
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMessageLongPress?.invoke(message)
                    }
                )
            }
        ) {
            parsedReply?.let { reply ->
                ReplyPreviewCard(
                    sender = reply.sender,
                    preview = reply.preview,
                    isSelf = isSelf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (displayContent.isNotBlank()) 8.dp else 0.dp)
                )
            }
            if (displayContent.isNotBlank()) {
                Text(
                    text = annotatedText,
                    fontFamily = ChatFontFamily,
                    softWrap = true,
                    overflow = TextOverflow.Visible,
                    style = androidx.compose.ui.text.TextStyle(
                        color = bubbleTextColor
                    ),
                    onTextLayout = { result -> textLayoutResult = result }
                )
            }
        }
    }
}

@Composable
private fun ReplyPreviewCard(
    sender: String,
    preview: String,
    isSelf: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = if (isSelf) {
        Color.White.copy(alpha = 0.10f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
    val contentColor = if (isSelf) Color.White else colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(10.dp))
            .border(1.dp, colorScheme.primary.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .background(colorScheme.primary, RoundedCornerShape(999.dp))
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = sender,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preview,
                color = contentColor.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class MessageGrouping(
    val topSpacing: Dp = 1.dp,
    val bottomSpacing: Dp = 1.dp
)

private fun messageGrouping(
    renderedMessages: List<BitchatMessage>,
    index: Int
): MessageGrouping {
    val current = renderedMessages[index]
    val newer = renderedMessages.getOrNull(index - 1)
    val older = renderedMessages.getOrNull(index + 1)
    return MessageGrouping(
        topSpacing = if (older != null && isSameSenderGroup(current, older)) 1.dp else 6.dp,
        bottomSpacing = if (newer != null && isSameSenderGroup(current, newer)) 1.dp else 4.dp
    )
}

private fun isSameSenderGroup(current: BitchatMessage, other: BitchatMessage): Boolean {
    if (current.sender == "system" || other.sender == "system") return false
    if (current.isPrivate != other.isPrivate) return false
    if (current.channel != other.channel) return false
    return senderIdentityKey(current) == senderIdentityKey(other)
}

private fun senderIdentityKey(message: BitchatMessage): String {
    return message.senderPeerID ?: message.originalSender ?: message.sender
}

private const val ReplyPrefix = "\u21AA "

internal fun buildReplyMessageContent(replyTo: BitchatMessage, body: String): String {
    val sender = replyTo.sender.ifBlank { "Unknown" }
    val preview = messagePreviewText(replyTo)
    return buildString {
        append(ReplyPrefix)
        append(sender)
        append(": ")
        append(preview)
        append('\n')
        append(body)
    }
}

internal fun messagePreviewText(message: BitchatMessage): String {
    val raw = parseReplyMessageContent(message.content)?.body ?: message.content
    val compact = raw.replace("\n", " ").trim()
    val fallback = when (message.type) {
        BitchatMessageType.Audio -> "Voice note"
        BitchatMessageType.Image -> "Image"
        BitchatMessageType.File -> "File"
        else -> compact
    }
    return fallback.ifBlank { "Message" }.take(72)
}

private data class ParsedReplyContent(
    val sender: String,
    val preview: String,
    val body: String
)

private fun parseReplyMessageContent(content: String): ParsedReplyContent? {
    if (!content.startsWith(ReplyPrefix)) return null
    val firstLineBreak = content.indexOf('\n')
    if (firstLineBreak <= ReplyPrefix.length) return null
    val header = content.substring(ReplyPrefix.length, firstLineBreak)
    val separatorIndex = header.indexOf(": ")
    if (separatorIndex <= 0) return null
    return ParsedReplyContent(
        sender = header.substring(0, separatorIndex).trim(),
        preview = header.substring(separatorIndex + 2).trim(),
        body = content.substring(firstLineBreak + 1)
    )
}

@Composable
fun DeliveryStatusIcon(status: DeliveryStatus) {
    val colorScheme = MaterialTheme.colorScheme
    
    when (status) {
        is DeliveryStatus.Sending -> {
            Text(
                text = stringResource(R.string.status_sending),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.6f)
            )
        }
        is DeliveryStatus.Sent -> {
            // Use a subtle hollow marker for Sent; single check is reserved for Delivered (iOS parity)
            Text(
                text = stringResource(R.string.status_pending),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.6f)
            )
        }
        is DeliveryStatus.Delivered -> {
            // Single check for Delivered (matches iOS expectations)
            Text(
                text = stringResource(R.string.status_sent),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.8f)
            )
        }
        is DeliveryStatus.Read -> {
            Text(
                text = stringResource(R.string.status_delivered),
                fontSize = 10.sp,
                color = Color(0xFF007AFF), // Blue
                fontWeight = FontWeight.Bold
            )
        }
        is DeliveryStatus.Failed -> {
            Text(
                text = stringResource(R.string.status_failed),
                fontSize = 10.sp,
                color = Color.Red.copy(alpha = 0.8f)
            )
        }
        is DeliveryStatus.PartiallyDelivered -> {
            // Show a single subdued check without numeric label
            Text(
                text = stringResource(R.string.status_sent),
                fontSize = 10.sp,
                color = colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}
