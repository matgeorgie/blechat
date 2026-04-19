package com.bitchat.android.summary

data class SummaryInputMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestampMs: Long,
    val senderPeerID: String? = null,
    val mentions: List<String> = emptyList()
)

data class SummaryItem(
    val label: String,
    val text: String,
    val sourceMessageIds: List<String>
)

data class ConversationSummaryResult(
    val messageCount: Int,
    val participantCount: Int,
    val mainTopic: SummaryItem?,
    val importantUpdates: List<SummaryItem>,
    val openQuestions: List<SummaryItem>,
    val actionItems: List<SummaryItem>,
    val decisions: List<SummaryItem>,
    val notificationText: String?,
    val topSourceMessageIds: List<String>
)

data class NotificationSummaryResult(
    val text: String,
    val sourceMessageIds: List<String>
)

enum class ConversationSummaryKind {
    GLOBAL,
    DIRECT,
    CHANNEL,
    GROUP,
    GEOHASH
}

data class ConversationSummarySheetData(
    val conversationKey: String,
    val title: String,
    val kind: ConversationSummaryKind,
    val summary: ConversationSummaryResult,
    val catchUpSummary: ConversationSummaryResult? = null
)
