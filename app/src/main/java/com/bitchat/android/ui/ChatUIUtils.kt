package com.bitchat.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.mesh.BluetoothMeshService
import androidx.compose.material3.ColorScheme
import com.bitchat.android.ui.theme.BASE_FONT_SIZE
import com.bitchat.android.ui.theme.ChatFontFamily
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility functions for ChatScreen UI components
 * Extracted from ChatScreen.kt for better organization
 */

/**
 * Get RSSI-based color for signal strength visualization
 */
fun getRSSIColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> Color(0xFF00FF00) // Bright green
        rssi >= -60 -> Color(0xFF80FF00) // Green-yellow
        rssi >= -70 -> Color(0xFFFFFF00) // Yellow
        rssi >= -80 -> Color(0xFFFF8000) // Orange
        else -> Color(0xFFFF4444) // Red
    }
}

/**
 * Format message as annotated string with iOS-style formatting
 * Timestamp at END, peer colors, hashtag suffix handling
 */
fun formatMessageAsAnnotatedString(
    message: BitchatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f

    // Bubble text color: always white for sender (dark blue bubble), adaptive for receiver
    val isSelf = message.senderPeerID == meshService.myPeerID ||
                 message.sender == currentUserNickname ||
                 message.sender.startsWith("$currentUserNickname#")
    val bubbleTextColor = if (isSelf) Color.White else if (isDark) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)

    if (message.sender != "system") {
        if (!isSelf) {
            // Split sender into base name and hashtag suffix — we only show the base name (clean WhatsApp style)
            val (baseName, _) = splitSuffix(message.sender)

            // Sender name — bold, at the start of the bubble (WhatsApp style)
            // Peer accent color for the name label, so users can be visually identified
            val nameColor = getPeerColor(message, isDark)
            builder.pushStyle(SpanStyle(
                color = nameColor,
                fontSize = BASE_FONT_SIZE.sp,
                fontFamily = ChatFontFamily,
                fontWeight = FontWeight.Bold
            ))
            val nicknameStart = builder.length
            builder.append(truncateNickname(baseName))
            val nicknameEnd = builder.length

            // Add click annotation for nickname (store canonical sender name with hash if available)
            builder.addStringAnnotation(
                tag = "nickname_click",
                annotation = (message.originalSender ?: message.sender),
                start = nicknameStart,
                end = nicknameEnd
            )
            builder.pop()

            // Newline separating name from message content
            builder.append("\n")
        }

        appendIOSFormattedContent(builder, message.content, message.mentions, currentUserNickname, bubbleTextColor, isSelf, isDark)

        // Timestamp at the end — small, subtle (WhatsApp style: inside bubble, bottom-right)
        builder.pushStyle(SpanStyle(
            color = bubbleTextColor.copy(alpha = 0.65f),
            fontSize = (BASE_FONT_SIZE - 4).sp,
            fontFamily = ChatFontFamily
        ))
        builder.append("  ${timeFormatter.format(message.timestamp)}")
        message.powDifficulty?.let { bits ->
            if (bits > 0) {
                builder.append(" ⛨${bits}b")
            }
        }
        builder.pop()

    } else {
        // System message — centered italic
        builder.pushStyle(SpanStyle(
            color = Color.Gray,
            fontSize = (BASE_FONT_SIZE - 2).sp,
            fontFamily = ChatFontFamily,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        ))
        builder.append("* ${message.content} *")
        builder.pop()

        // Timestamp for system messages too
        builder.pushStyle(SpanStyle(
            color = Color.Gray.copy(alpha = 0.5f),
            fontSize = (BASE_FONT_SIZE - 4).sp,
            fontFamily = ChatFontFamily
        ))
        builder.append("  ${timeFormatter.format(message.timestamp)}")
        builder.pop()
    }

    return builder.toAnnotatedString()
}

/**
 * Build only the nickname + timestamp header line for a message, matching styles of normal messages.
 */
fun formatMessageHeaderAnnotatedString(
    message: BitchatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f

    val isSelf = message.senderPeerID == meshService.myPeerID ||
            message.sender == currentUserNickname ||
            message.sender.startsWith("$currentUserNickname#")
    val bubbleTextColor = if (isSelf) Color.White else if (isDark) Color(0xFFEEEEEE) else Color(0xFF1A1A1A)

    if (message.sender != "system") {
        if (!isSelf) {
            // Only show clean base name — WhatsApp style (no <@ > wrapping, no #abcd suffix)
            val (baseName, _) = splitSuffix(message.sender)
            val nameColor = getPeerColor(message, isDark)

            builder.pushStyle(SpanStyle(
                color = nameColor,
                fontSize = BASE_FONT_SIZE.sp,
                fontFamily = ChatFontFamily,
                fontWeight = FontWeight.Bold
            ))
            val nicknameStart = builder.length
            builder.append(truncateNickname(baseName))
            val nicknameEnd = builder.length
            builder.addStringAnnotation(
                tag = "nickname_click",
                annotation = (message.originalSender ?: message.sender),
                start = nicknameStart,
                end = nicknameEnd
            )
            builder.pop()
        }

        // Timestamp on the same header line (for media messages)
        builder.pushStyle(SpanStyle(
            color = bubbleTextColor.copy(alpha = 0.65f),
            fontSize = (BASE_FONT_SIZE - 4).sp,
            fontFamily = ChatFontFamily
        ))
        builder.append("  ${timeFormatter.format(message.timestamp)}")
        message.powDifficulty?.let { bits ->
            if (bits > 0) builder.append(" ⛨${bits}b")
        }
        builder.pop()
    } else {
        // System message header
        builder.pushStyle(SpanStyle(
            color = Color.Gray,
            fontSize = (BASE_FONT_SIZE - 2).sp,
            fontFamily = ChatFontFamily,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        ))
        builder.append("* ${message.content} *")
        builder.pop()
        builder.pushStyle(SpanStyle(
            color = Color.Gray.copy(alpha = 0.5f),
            fontSize = (BASE_FONT_SIZE - 4).sp,
            fontFamily = ChatFontFamily
        ))
        builder.append("  ${timeFormatter.format(message.timestamp)}")
        builder.pop()
    }

    return builder.toAnnotatedString()
}

/**
 * iOS-style peer color assignment using djb2 hash algorithm
 * Avoids orange (~30°) reserved for self messages
 */
fun getPeerColor(message: BitchatMessage, isDark: Boolean): Color {
    // Create seed from peer identifier (prioritizing stable keys)
    val seed = when {
        message.senderPeerID?.startsWith("nostr:") == true || message.senderPeerID?.startsWith("nostr_") == true -> {
            // For Nostr peers, use the full key if available, otherwise the peer ID
            "nostr:${message.senderPeerID.lowercase()}"
        }
        message.senderPeerID?.length == 16 -> {
            // For ephemeral peer IDs, try to get stable Noise key, fallback to peer ID
            "noise:${message.senderPeerID.lowercase()}"
        }
        message.senderPeerID?.length == 64 -> {
            // This is already a stable Noise key
            "noise:${message.senderPeerID.lowercase()}"
        }
        else -> {
            // Fallback to sender name
            message.sender.lowercase()
        }
    }

    return colorForPeerSeed(seed, isDark)
}

/**
 * Generate consistent peer color using djb2 hash (matches iOS algorithm exactly)
 */
fun colorForPeerSeed(seed: String, isDark: Boolean): Color {
    // djb2 hash algorithm (matches iOS implementation)
    var hash = 5381UL
    for (byte in seed.toByteArray()) {
        hash = ((hash shl 5) + hash) + byte.toUByte().toULong()
    }

    var hue = (hash % 360UL).toDouble() / 360.0

    // Avoid orange (~30°) reserved for self (matches iOS logic)
    val orange = 30.0 / 360.0
    if (kotlin.math.abs(hue - orange) < 0.05) {
        hue = (hue + 0.12) % 1.0
    }

    val saturation = if (isDark) 0.50 else 0.70
    val brightness = if (isDark) 0.85 else 0.35

    return Color.hsv(
        hue = (hue * 360).toFloat(),
        saturation = saturation.toFloat(),
        value = brightness.toFloat()
    )
}

/**
 * Split a name into base and a '#abcd' suffix if present (matches iOS splitSuffix exactly)
 */
fun splitSuffix(name: String): Pair<String, String> {
    if (name.length < 5) return Pair(name, "")

    val suffix = name.takeLast(5)
    if (suffix.startsWith("#") && suffix.drop(1).all {
        it.isDigit() || it.lowercaseChar() in 'a'..'f'
    }) {
        val base = name.dropLast(5)
        return Pair(base, suffix)
    }

    return Pair(name, "")
}

/**
 * iOS-style content formatting with proper hashtag and mention handling
 */
private fun appendIOSFormattedContent(
    builder: AnnotatedString.Builder,
    content: String,
    mentions: List<String>?,
    currentUserNickname: String,
    baseColor: Color,
    isSelf: Boolean,
    isDark: Boolean
) {
    // iOS-style patterns: allow optional '#abcd' suffix in mentions
    val hashtagPattern = "#([a-zA-Z0-9_]+)".toRegex()
    val mentionPattern = "@([\\p{L}0-9_]+(?:#[a-fA-F0-9]{4})?)".toRegex()

    val hashtagMatches = hashtagPattern.findAll(content).toList()
    val mentionMatches = mentionPattern.findAll(content).toList()

    // Combine and sort matches, but exclude hashtags that overlap with mentions
    val mentionRanges = mentionMatches.map { it.range }
    fun overlapsMention(range: IntRange): Boolean {
        return mentionRanges.any { mentionRange ->
            range.first < mentionRange.last && range.last > mentionRange.first
        }
    }

    val allMatches = mutableListOf<Pair<IntRange, String>>()

    // Add hashtag matches that don't overlap with mentions
    for (match in hashtagMatches) {
        if (!overlapsMention(match.range)) {
            allMatches.add(match.range to "hashtag")
        }
    }

    // Add all mention matches
    for (match in mentionMatches) {
        allMatches.add(match.range to "mention")
    }

    // Add standalone geohash matches (e.g., "#9q") that are not part of another word
    // We use MessageSpecialParser to find exact ranges; then merge with existing ranges avoiding overlaps
    val geoMatches = MessageSpecialParser.findStandaloneGeohashes(content)
    for (gm in geoMatches) {
        val range = gm.start until gm.endExclusive
        if (!overlapsMention(range)) {
            allMatches.add(range to "geohash")
        }
    }

    // Add URL matches (http/https/www/bare domains). Exclude overlaps with mentions.
    val urlMatches = MessageSpecialParser.findUrls(content)
    for (um in urlMatches) {
        val range = um.start until um.endExclusive
        if (!overlapsMention(range)) {
            allMatches.add(range to "url")
        }
    }

    // Remove generic hashtag matches that overlap with detected geohash ranges to avoid duplicate rendering
    fun rangesOverlap(a: IntRange, b: IntRange): Boolean {
        return a.first < b.last && a.last > b.first
    }
    val urlRanges = allMatches.filter { it.second == "url" }.map { it.first }
    val geoRanges = allMatches.filter { it.second == "geohash" }.map { it.first }
    if (geoRanges.isNotEmpty() || urlRanges.isNotEmpty()) {
        val iterator = allMatches.listIterator()
        while (iterator.hasNext()) {
            val (range, type) = iterator.next()
            // Remove generic hashtags that overlap with geohashes, and geohashes that overlap with URLs
            val overlapsGeo = geoRanges.any { rangesOverlap(range, it) }
            val overlapsUrl = urlRanges.any { rangesOverlap(range, it) }
            if ((type == "hashtag" && overlapsGeo) || (type == "geohash" && overlapsUrl)) iterator.remove()
        }
    }

    allMatches.sortBy { it.first.first }

    var lastEnd = 0
    val isMentioned = mentions?.contains(currentUserNickname) == true

    for ((range, type) in allMatches) {
        // Add text before match
        if (lastEnd < range.first) {
            val beforeText = content.substring(lastEnd, range.first)
            if (beforeText.isNotEmpty()) {
                builder.pushStyle(SpanStyle(
                    color = baseColor,
                    fontSize = BASE_FONT_SIZE.sp,
                    fontFamily = ChatFontFamily,
                    fontWeight = FontWeight.Normal
                ))
                if (isMentioned) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(beforeText)
                    builder.pop()
                } else {
                    builder.append(beforeText)
                }
                builder.pop()
            }
        }

        // Add styled match
        val matchText = content.substring(range.first, range.last + 1)
        when (type) {
            "mention" -> {
                // iOS-style mention with hashtag suffix support
                val mentionWithoutAt = matchText.removePrefix("@")
                val (mBase, mSuffix) = splitSuffix(mentionWithoutAt)

                // Check if this mention targets current user
                val isMentionToMe = mBase == currentUserNickname
                val mentionColor = if (isMentionToMe) Color(0xFFFF9500) else baseColor

                // "@" symbol
                builder.pushStyle(SpanStyle(
                    color = mentionColor,
                    fontSize = BASE_FONT_SIZE.sp,
                    fontWeight = FontWeight.SemiBold
                ))
                builder.append("@")
                builder.pop()

                // Base name (truncate for rendering)
                builder.pushStyle(SpanStyle(
                    color = mentionColor,
                    fontSize = BASE_FONT_SIZE.sp,
                    fontWeight = FontWeight.SemiBold
                ))
                builder.append(truncateNickname(mBase))
                builder.pop()

                // Hashtag suffix in lighter color
                if (mSuffix.isNotEmpty()) {
                    builder.pushStyle(SpanStyle(
                        color = mentionColor.copy(alpha = 0.6f),
                        fontSize = BASE_FONT_SIZE.sp,
                        fontWeight = FontWeight.SemiBold
                    ))
                    builder.append(mSuffix)
                    builder.pop()
                }
            }
            "hashtag" -> {
                // Render general hashtags like normal content
                builder.pushStyle(SpanStyle(
                    color = baseColor,
                    fontSize = BASE_FONT_SIZE.sp,
                    fontWeight = FontWeight.Normal
                ))
                if (isMentioned) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(matchText)
                    builder.pop()
                } else {
                    builder.append(matchText)
                }
                builder.pop()
            }
            else -> {
                if (type == "geohash") {
                    // Style geohash in blue, underlined, and add click annotation
                    builder.pushStyle(SpanStyle(
                        color = Color(0xFF007AFF),
                        fontSize = BASE_FONT_SIZE.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline
                    ))
                    val start = builder.length
                    builder.append(matchText)
                    val end = builder.length
                    val geohash = matchText.removePrefix("#").lowercase()
                    builder.addStringAnnotation(
                        tag = "geohash_click",
                        annotation = geohash,
                        start = start,
                        end = end
                    )
                    builder.pop()
                } else if (type == "url") {
                    // Style URL in blue, underlined, and add click annotation with the raw text
                    builder.pushStyle(SpanStyle(
                        color = Color(0xFF007AFF),
                        fontSize = BASE_FONT_SIZE.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline
                    ))
                    val start = builder.length
                    builder.append(matchText)
                    val end = builder.length
                    builder.addStringAnnotation(
                        tag = "url_click",
                        annotation = matchText,
                        start = start,
                        end = end
                    )
                    builder.pop()
                } else {
                    // Fallback: treat as normal text
                    builder.pushStyle(SpanStyle(
                        color = baseColor,
                        fontSize = BASE_FONT_SIZE.sp,
                        fontWeight = FontWeight.Normal
                    ))
                    builder.append(matchText)
                    builder.pop()
                }
            }
        }

        lastEnd = range.last + 1
    }

    // Add remaining text
    if (lastEnd < content.length) {
        val remainingText = content.substring(lastEnd)
        builder.pushStyle(SpanStyle(
            color = baseColor,
            fontSize = BASE_FONT_SIZE.sp,
            fontFamily = ChatFontFamily,
            fontWeight = FontWeight.Normal
        ))
        if (isMentioned) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(remainingText)
            builder.pop()
        } else {
            builder.append(remainingText)
        }
        builder.pop()
    }
}
