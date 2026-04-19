package com.bitchat.android.summary

import java.util.Locale

class ConversationSummaryEngine {
    private val stopWords = setOf(
        "the", "and", "for", "that", "with", "this", "have", "from", "your", "about", "just",
        "they", "them", "then", "into", "onto", "will", "would", "should", "could", "there",
        "here", "what", "when", "where", "which", "who", "been", "were", "over", "under",
        "after", "before", "need", "want", "like", "lets", "let", "okay", "yeah", "yep",
        "sure", "than", "also", "really", "very", "more", "some", "much", "many", "only",
        "dont", "cant", "im", "youre", "weve", "ive", "its", "our", "out", "off", "not"
    )
    private val noisyTopicTokens = setOf(
        "data", "user", "users", "com", "bitchat", "droid", "files", "file", "voicenotes",
        "voice", "outgoing", "incoming", "storage", "android", "cache", "tmp", "jpg", "png",
        "jpeg", "opus", "mp3", "m4a", "wav", "aac"
    )
    private val lowSignalRegex = Regex(
        "^(ok|okay|kk|k|cool|nice|thx|thanks|thank you|lol|lmao|haha|yep|yes|nope|nah|sure|done|omw|got it|sounds good)[!. ]*$"
    )
    private val urlRegex = Regex("(https?://\\S+|www\\.\\S+)")
    private val mentionRegex = Regex("@([a-zA-Z0-9_]+)")
    private val pathLikeRegex = Regex("(/data/user/|/storage/|files/|voicenotes/|voice_[0-9]|\\.m4a\\b|\\.mp3\\b|\\.wav\\b|\\.opus\\b|\\.jpg\\b|\\.jpeg\\b|\\.png\\b)", RegexOption.IGNORE_CASE)
    private val tokenRegex = Regex("[a-z0-9][a-z0-9'_-]{1,}")
    private val questionRegex = Regex("\\?")
    private val requestRegex = Regex(
        "\\b(can you|could you|please|need to|bring|send|share|check|confirm|let me know|remember to|can someone|who can)\\b"
    )
    private val timeRegex = Regex(
        "\\b(\\d{1,2}(:\\d{2})?\\s?(am|pm)?|tonight|tomorrow|today|later|soon|morning|afternoon|evening|weekend|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b"
    )
    private val locationRegex = Regex(
        "\\b(gate\\s?\\d+|room\\s?\\w+|lobby|entrance|exit|station|park|cafe|office|building|terminal|platform|floor|address|location|meet at|at gate|gate)\\b"
    )
    private val updateRegex = Regex("\\b(actually|changed|update|instead|moved|confirmed|latest|now at|switching|switched)\\b")
    private val urgencyRegex = Regex("\\b(urgent|asap|now|quick|immediately|right away)\\b")
    private val decisionRegex = Regex("\\b(confirmed|decided|decision|locked in|final|settled|plan is|we will|we'll|lets|let's)\\b")
    private val duplicateNormalizationRegex = Regex("[^a-z0-9 ]")

    /**
     * Deterministic extractive scoring formula:
     * +3.0 question
     * +2.5 request / action language
     * +2.5 time or date detail
     * +2.5 location detail
     * +2.0 update / change wording
     * +2.0 urgency wording
     * +2.0 direct mention of current user
     * +1.0 decision wording
     * +0.5 longer substantive text
     * -2.5 low-information chatter / acknowledgements
     * -1.5 duplicate / repeated content
     * -1.0 very short messages
     */
    fun summarizeConversation(
        messages: List<SummaryInputMessage>,
        currentUserNickname: String? = null
    ): ConversationSummaryResult {
        val analyzed = analyze(messages, currentUserNickname)
        if (analyzed.isEmpty()) {
            return ConversationSummaryResult(
                messageCount = 0,
                participantCount = 0,
                mainTopic = null,
                importantUpdates = emptyList(),
                openQuestions = emptyList(),
                actionItems = emptyList(),
                decisions = emptyList(),
                notificationText = null,
                topSourceMessageIds = emptyList()
            )
        }

        val topic = extractTopic(analyzed)
        val updates = analyzed
            .filter {
                (it.features.hasUpdate || it.features.hasTime || it.features.hasLocation || it.features.hasUrgency) &&
                    !it.features.lowInformation &&
                    !it.features.attachmentLike
            }
            .sortedByDescending { it.score }
            .distinctBy { it.dedupKey }
            .take(2)
            .map { it.toSummaryItem("Update") }
        val questions = analyzed
            .filter { it.features.hasQuestion && !it.features.lowInformation && !it.features.attachmentLike }
            .sortedByDescending { it.score }
            .distinctBy { it.dedupKey }
            .take(2)
            .map { it.toSummaryItem("Question") }
        val actions = analyzed
            .filter { it.features.looksLikeRequest && !it.features.lowInformation && !it.features.attachmentLike }
            .sortedByDescending { it.score }
            .distinctBy { it.dedupKey }
            .take(2)
            .map { it.toSummaryItem("Action") }
        val decisions = analyzed
            .filter { it.features.hasDecision && !it.features.lowInformation && !it.features.attachmentLike }
            .sortedByDescending { it.score }
            .distinctBy { it.dedupKey }
            .take(2)
            .map { it.toSummaryItem("Decision") }

        val notification = summarizeNotificationBatch(messages, currentUserNickname)

        return ConversationSummaryResult(
            messageCount = analyzed.size,
            participantCount = analyzed.map { it.message.sender }.distinct().size,
            mainTopic = topic?.let { SummaryItem("Main topic", it.text, topTopicSourceIds(analyzed)) },
            importantUpdates = updates,
            openQuestions = questions,
            actionItems = actions,
            decisions = decisions,
            notificationText = notification?.text,
            topSourceMessageIds = notification?.sourceMessageIds.orEmpty()
        )
    }

    fun summarizeNotificationBatch(
        messages: List<SummaryInputMessage>,
        currentUserNickname: String? = null
    ): NotificationSummaryResult? {
        val analyzed = analyze(messages, currentUserNickname)
        if (analyzed.isEmpty()) return null

        val topic = extractTopic(analyzed)
        val count = analyzed.size
        val senderCount = analyzed.map { it.message.sender }.distinct().size
        val topUpdate = analyzed
            .filter {
                (it.features.hasUpdate || it.features.hasTime || it.features.hasLocation || it.features.hasUrgency) &&
                    !it.features.lowInformation &&
                    !it.features.attachmentLike
            }
            .maxByOrNull { it.score }
        val directQuestion = analyzed.firstOrNull {
            it.features.hasQuestion && it.features.directMention && !it.features.lowInformation && !it.features.attachmentLike
        }
        val openQuestion = analyzed.firstOrNull {
            it.features.hasQuestion && !it.features.lowInformation && !it.features.attachmentLike
        }
        val urgent = analyzed.firstOrNull { it.features.hasUrgency && !it.features.lowInformation && !it.features.attachmentLike }
        val attentionClause = when {
            urgent != null -> " Needs attention."
            directQuestion != null || openQuestion != null -> " Awaiting reply."
            else -> ""
        }

        val text = buildString {
            append("$count new messages")
            if (senderCount > 1) {
                append(" from $senderCount people")
            }
            append(".")
            if (topUpdate == null && topic != null && topic.score >= 4.0) {
                append(" About ${topic.text}.")
            }
            when {
                topUpdate != null -> append(" Main update: ${snippet(topUpdate.message.text)}.")
                directQuestion != null -> append(" One direct question for you.")
                openQuestion != null -> append(" Open question: ${snippet(openQuestion.message.text)}.")
            }
            append(attentionClause)
        }.replace("\\s+".toRegex(), " ").trim()

        return NotificationSummaryResult(
            text = text,
            sourceMessageIds = listOfNotNull(
                topUpdate?.message?.id,
                directQuestion?.message?.id,
                openQuestion?.message?.id,
                urgent?.message?.id
            ).distinct()
        )
    }

    private fun analyze(
        messages: List<SummaryInputMessage>,
        currentUserNickname: String?
    ): List<AnalyzedMessage> {
        if (messages.isEmpty()) return emptyList()

        val duplicateCounts = mutableMapOf<String, Int>()
        val prelim = messages.map { message ->
            val normalized = normalize(message.text)
            val dedupKey = duplicateNormalizationRegex.replace(normalized, " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
            duplicateCounts[dedupKey] = (duplicateCounts[dedupKey] ?: 0) + 1
            Triple(message, normalized, dedupKey)
        }

        return prelim.map { (message, normalized, dedupKey) ->
            val features = extractFeatures(message, normalized, currentUserNickname, duplicateCounts[dedupKey] ?: 1)
            AnalyzedMessage(
                message = message.copy(text = message.text.trim()),
                normalizedText = normalized,
                dedupKey = dedupKey,
                features = features,
                score = score(features)
            )
        }.sortedByDescending { it.score }
    }

    private fun normalize(text: String): String {
        return urlRegex.replace(text, " <url> ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .lowercase(Locale.US)
    }

    private fun extractFeatures(
        message: SummaryInputMessage,
        normalized: String,
        currentUserNickname: String?,
        duplicateCount: Int
    ): MessageFeatures {
        val cleaned = normalized.trim()
        val directMention = message.mentions.any { it.equals(currentUserNickname, ignoreCase = true) } ||
            (currentUserNickname?.let { mentionRegex.containsMatchIn(normalized) && normalized.contains("@${it.lowercase(Locale.US)}") } == true)
        val noUrls = cleaned.replace("<url>", "").trim()
        val tokenCount = tokenRegex.findAll(noUrls).count()
        val emojiOnly = tokenCount == 0 && noUrls.isNotBlank()
        val attachmentLike = pathLikeRegex.containsMatchIn(message.text) ||
            (message.text.contains('/') && !message.text.contains(' ') && message.text.length > 18)
        val lowInformation = lowSignalRegex.matches(noUrls) || emojiOnly || attachmentLike

        return MessageFeatures(
            hasQuestion = questionRegex.containsMatchIn(message.text),
            looksLikeRequest = requestRegex.containsMatchIn(cleaned),
            hasTime = timeRegex.containsMatchIn(cleaned),
            hasLocation = locationRegex.containsMatchIn(cleaned),
            hasUpdate = updateRegex.containsMatchIn(cleaned),
            hasUrgency = urgencyRegex.containsMatchIn(cleaned),
            hasDecision = decisionRegex.containsMatchIn(cleaned),
            directMention = directMention,
            lowInformation = lowInformation,
            attachmentLike = attachmentLike,
            duplicate = duplicateCount > 1,
            shortMessage = tokenCount in 0..2,
            substantiveLength = tokenCount >= 8
        )
    }

    private fun score(features: MessageFeatures): Double {
        var score = 0.0
        if (features.hasQuestion) score += 3.0
        if (features.looksLikeRequest) score += 2.5
        if (features.hasTime) score += 2.5
        if (features.hasLocation) score += 2.5
        if (features.hasUpdate) score += 2.0
        if (features.hasUrgency) score += 2.0
        if (features.directMention) score += 2.0
        if (features.hasDecision) score += 1.0
        if (features.substantiveLength) score += 0.5
        if (features.lowInformation) score -= 2.5
        if (features.attachmentLike) score -= 4.0
        if (features.duplicate) score -= 1.5
        if (features.shortMessage) score -= 1.0
        return score
    }

    private fun extractTopic(messages: List<AnalyzedMessage>): TopicCandidate? {
        val weights = linkedMapOf<String, Double>()
        val senderTokens = messages.map { it.message.sender.lowercase(Locale.US) }.toSet()
        messages
            .filter { !it.features.lowInformation && !it.features.attachmentLike }
            .take(20)
            .forEach { message ->
                tokenRegex.findAll(message.normalizedText)
                    .map { it.value.trim('\'', '-', '_') }
                    .filter {
                        it.length >= 3 &&
                            it !in stopWords &&
                            it !in noisyTopicTokens &&
                            it !in senderTokens &&
                            !it.all(Char::isDigit)
                    }
                    .distinct()
                    .forEach { token ->
                        weights[token] = (weights[token] ?: 0.0) + maxOf(message.score, 0.2)
                    }
            }

        val topTokens = weights.entries
            .sortedByDescending { it.value }
            .take(2)
            .map { it.key }
        if (topTokens.isEmpty()) return null
        val score = topTokens.sumOf { weights[it] ?: 0.0 }
        return TopicCandidate(topTokens.joinToString(" "), score)
    }

    private fun topTopicSourceIds(messages: List<AnalyzedMessage>): List<String> {
        return messages
            .filter { !it.features.lowInformation }
            .take(3)
            .map { it.message.id }
    }

    private fun snippet(text: String): String {
        val trimmed = text.replace("\\s+".toRegex(), " ").trim()
        return if (trimmed.length <= 72) trimmed else trimmed.take(69).trimEnd() + "..."
    }

    private fun AnalyzedMessage.toSummaryItem(label: String): SummaryItem {
        return SummaryItem(
            label = label,
            text = "${message.sender}: ${snippet(message.text)}",
            sourceMessageIds = listOf(message.id)
        )
    }

    private data class AnalyzedMessage(
        val message: SummaryInputMessage,
        val normalizedText: String,
        val dedupKey: String,
        val features: MessageFeatures,
        val score: Double
    )

    private data class MessageFeatures(
        val hasQuestion: Boolean,
        val looksLikeRequest: Boolean,
        val hasTime: Boolean,
        val hasLocation: Boolean,
        val hasUpdate: Boolean,
        val hasUrgency: Boolean,
        val hasDecision: Boolean,
        val directMention: Boolean,
        val lowInformation: Boolean,
        val attachmentLike: Boolean,
        val duplicate: Boolean,
        val shortMessage: Boolean,
        val substantiveLength: Boolean
    )

    private data class TopicCandidate(
        val text: String,
        val score: Double
    )
}
