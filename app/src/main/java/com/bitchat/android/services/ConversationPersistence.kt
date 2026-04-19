package com.bitchat.android.services

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.BitchatMessageType
import com.bitchat.android.model.DeliveryStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

private enum class ConversationKind {
    GLOBAL,
    DIRECT,
    CHANNEL,
    GROUP
}

private data class ConversationDescriptor(
    val key: String,
    val kind: ConversationKind,
    val peerID: String? = null,
    val channelID: String? = null
)

data class PersistedChatSnapshot(
    val publicMessages: List<BitchatMessage>,
    val privateMessages: Map<String, List<BitchatMessage>>,
    val channelMessages: Map<String, List<BitchatMessage>>
)

private class ConversationDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                conversation_key TEXT PRIMARY KEY,
                conversation_kind TEXT NOT NULL,
                peer_id TEXT,
                channel_id TEXT,
                title TEXT,
                sync_anchor_message_id TEXT,
                sync_anchor_timestamp_ms INTEGER,
                last_synced_at_ms INTEGER,
                last_message_id TEXT,
                last_message_timestamp_ms INTEGER,
                metadata_json TEXT,
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                message_id TEXT PRIMARY KEY,
                conversation_key TEXT NOT NULL,
                conversation_kind TEXT NOT NULL,
                sender TEXT NOT NULL,
                content TEXT NOT NULL,
                message_type TEXT NOT NULL,
                timestamp_ms INTEGER NOT NULL,
                is_relay INTEGER NOT NULL,
                original_sender TEXT,
                is_private INTEGER NOT NULL,
                recipient_nickname TEXT,
                sender_peer_id TEXT,
                mentions_json TEXT,
                channel_id TEXT,
                encrypted_content BLOB,
                is_encrypted INTEGER NOT NULL,
                delivery_kind TEXT,
                delivery_actor TEXT,
                delivery_at_ms INTEGER,
                delivery_reason TEXT,
                delivery_reached INTEGER,
                delivery_total INTEGER,
                pow_difficulty INTEGER,
                source_transport TEXT NOT NULL,
                sync_state TEXT NOT NULL,
                sync_attempt_count INTEGER NOT NULL,
                local_direction TEXT NOT NULL,
                remote_sender_id TEXT,
                remote_recipient_id TEXT,
                received_at_ms INTEGER NOT NULL,
                last_modified_at_ms INTEGER NOT NULL,
                FOREIGN KEY(conversation_key) REFERENCES conversations(conversation_key) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_messages_conversation_timestamp ON messages(conversation_key, timestamp_ms, received_at_ms)")
        db.execSQL("CREATE INDEX idx_messages_sync_state ON messages(sync_state, last_modified_at_ms)")
        db.execSQL("CREATE INDEX idx_conversations_kind ON conversations(conversation_kind, updated_at_ms)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS conversations")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "bitchat_conversations.db"
        private const val DATABASE_VERSION = 1
    }
}

class ConversationRepository private constructor(context: Context) {
    private val helper = ConversationDatabaseHelper(context.applicationContext)
    private val gson = Gson()
    private val mentionsType = object : TypeToken<List<String>>() {}.type

    fun loadSnapshot(): PersistedChatSnapshot {
        val publicMessages = mutableListOf<BitchatMessage>()
        val privateMessages = linkedMapOf<String, MutableList<BitchatMessage>>()
        val channelMessages = linkedMapOf<String, MutableList<BitchatMessage>>()

        helper.readableDatabase.query(
            "messages",
            null,
            null,
            null,
            null,
            null,
            "timestamp_ms ASC, received_at_ms ASC, rowid ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val message = cursor.toMessage()
                when (cursor.getStringOrNull("conversation_kind")) {
                    ConversationKind.GLOBAL.name -> publicMessages.add(message)
                    ConversationKind.DIRECT.name -> {
                        val peerID = cursor.getStringOrNull("remote_recipient_id")
                            ?: cursor.getStringOrNull("remote_sender_id")
                            ?: continue
                        privateMessages.getOrPut(peerID) { mutableListOf() }.add(message)
                    }
                    ConversationKind.CHANNEL.name,
                    ConversationKind.GROUP.name -> {
                        val channelID = cursor.getStringOrNull("channel_id") ?: continue
                        channelMessages.getOrPut(channelID) { mutableListOf() }.add(message)
                    }
                }
            }
        }

        return PersistedChatSnapshot(
            publicMessages = publicMessages,
            privateMessages = privateMessages.mapValues { it.value.toList() },
            channelMessages = channelMessages.mapValues { it.value.toList() }
        )
    }

    fun persistPublicMessage(message: BitchatMessage, localPeerID: String?, localNickname: String?) {
        persistMessage(
            descriptor = ConversationDescriptor(key = GLOBAL_KEY, kind = ConversationKind.GLOBAL),
            message = message,
            localPeerID = localPeerID,
            localNickname = localNickname,
            sourceTransport = "mesh"
        )
    }

    fun persistPrivateMessage(peerID: String, message: BitchatMessage, localPeerID: String?, localNickname: String?) {
        persistMessage(
            descriptor = directConversation(peerID),
            message = message,
            localPeerID = localPeerID,
            localNickname = localNickname,
            sourceTransport = if (peerID.startsWith("nostr_") || peerID.startsWith("nostr:")) "nostr" else "mesh"
        )
    }

    fun persistChannelMessage(channel: String, message: BitchatMessage, localPeerID: String?, localNickname: String?) {
        persistMessage(
            descriptor = channelConversation(channel),
            message = message,
            localPeerID = localPeerID,
            localNickname = localNickname,
            sourceTransport = if (channel.startsWith("geo:")) "nostr" else "mesh"
        )
    }

    fun updateMessageStatus(messageID: String, status: DeliveryStatus) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.update(
            "messages",
            status.toContentValues().apply {
                put("last_modified_at_ms", now)
                put("sync_state", "dirty")
            },
            "message_id = ?",
            arrayOf(messageID)
        )
    }

    fun removeMessageById(messageID: String) {
        helper.writableDatabase.delete("messages", "message_id = ?", arrayOf(messageID))
    }

    fun clearConversationHistoryPublic() {
        clearConversationByKey(GLOBAL_KEY)
    }

    fun clearConversationHistoryDirect(peerID: String) {
        clearConversationByKey(directConversation(peerID).key)
    }

    fun clearConversationHistoryChannel(channel: String) {
        clearConversationByKey(channelConversation(channel).key)
    }

    fun mergePrivateConversationAliases(targetPeerID: String, keysToMerge: List<String>) {
        if (keysToMerge.isEmpty()) return
        val target = directConversation(targetPeerID)
        val now = System.currentTimeMillis()
        helper.writableDatabase.beginTransaction()
        try {
            ensureConversation(target, now)
            keysToMerge.distinct()
                .filter { it != targetPeerID }
                .forEach { alias ->
                    val aliasDescriptor = directConversation(alias)
                    helper.writableDatabase.execSQL(
                        """
                        UPDATE messages
                        SET conversation_key = ?, remote_recipient_id = ?, last_modified_at_ms = ?, sync_state = 'dirty'
                        WHERE conversation_key = ?
                        """.trimIndent(),
                        arrayOf(target.key, targetPeerID, now, aliasDescriptor.key)
                    )
                    helper.writableDatabase.delete(
                        "conversations",
                        "conversation_key = ?",
                        arrayOf(aliasDescriptor.key)
                    )
                }
            refreshConversationSummary(target.key, now)
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
    }

    fun wipeAllMessages() {
        helper.writableDatabase.beginTransaction()
        try {
            helper.writableDatabase.delete("messages", null, null)
            helper.writableDatabase.delete("conversations", null, null)
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
    }

    private fun clearConversationByKey(conversationKey: String) {
        helper.writableDatabase.beginTransaction()
        try {
            helper.writableDatabase.delete("messages", "conversation_key = ?", arrayOf(conversationKey))
            helper.writableDatabase.update(
                "conversations",
                ContentValues().apply {
                    putNull("last_message_id")
                    putNull("last_message_timestamp_ms")
                    putNull("sync_anchor_message_id")
                    putNull("sync_anchor_timestamp_ms")
                    put("updated_at_ms", System.currentTimeMillis())
                },
                "conversation_key = ?",
                arrayOf(conversationKey)
            )
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
    }

    private fun persistMessage(
        descriptor: ConversationDescriptor,
        message: BitchatMessage,
        localPeerID: String?,
        localNickname: String?,
        sourceTransport: String
    ) {
        val now = System.currentTimeMillis()
        helper.writableDatabase.beginTransaction()
        try {
            ensureConversation(descriptor, now)
            helper.writableDatabase.insertWithOnConflict(
                "messages",
                null,
                message.toContentValues(
                    descriptor = descriptor,
                    localPeerID = localPeerID,
                    localNickname = localNickname,
                    sourceTransport = sourceTransport,
                    mentionsJson = message.mentions?.let { gson.toJson(it, mentionsType) }
                ),
                SQLiteDatabase.CONFLICT_IGNORE
            )
            refreshConversationSummary(descriptor.key, now)
            helper.writableDatabase.setTransactionSuccessful()
        } finally {
            helper.writableDatabase.endTransaction()
        }
    }

    private fun ensureConversation(descriptor: ConversationDescriptor, now: Long) {
        val values = ContentValues().apply {
            put("conversation_key", descriptor.key)
            put("conversation_kind", descriptor.kind.name)
            put("peer_id", descriptor.peerID)
            put("channel_id", descriptor.channelID)
            put("created_at_ms", now)
            put("updated_at_ms", now)
            put("metadata_json", "{}")
        }
        helper.writableDatabase.insertWithOnConflict(
            "conversations",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
        helper.writableDatabase.update(
            "conversations",
            ContentValues().apply {
                put("updated_at_ms", now)
                put("peer_id", descriptor.peerID)
                put("channel_id", descriptor.channelID)
            },
            "conversation_key = ?",
            arrayOf(descriptor.key)
        )
    }

    private fun refreshConversationSummary(conversationKey: String, now: Long) {
        helper.readableDatabase.query(
            "messages",
            arrayOf("message_id", "timestamp_ms"),
            "conversation_key = ?",
            arrayOf(conversationKey),
            null,
            null,
            "timestamp_ms DESC, received_at_ms DESC, rowid DESC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                helper.writableDatabase.update(
                    "conversations",
                    ContentValues().apply {
                        put("last_message_id", cursor.getStringOrNull("message_id"))
                        put("last_message_timestamp_ms", cursor.getLongOrNull("timestamp_ms"))
                        put("sync_anchor_message_id", cursor.getStringOrNull("message_id"))
                        put("sync_anchor_timestamp_ms", cursor.getLongOrNull("timestamp_ms"))
                        put("updated_at_ms", now)
                    },
                    "conversation_key = ?",
                    arrayOf(conversationKey)
                )
            }
        }
    }

    private fun channelConversation(channel: String): ConversationDescriptor {
        val kind = if (channel.startsWith(GROUP_PREFIX)) ConversationKind.GROUP else ConversationKind.CHANNEL
        return ConversationDescriptor(
            key = if (kind == ConversationKind.GROUP) "group:$channel" else "channel:$channel",
            kind = kind,
            channelID = channel
        )
    }

    private fun directConversation(peerID: String): ConversationDescriptor {
        return ConversationDescriptor(
            key = "dm:$peerID",
            kind = ConversationKind.DIRECT,
            peerID = peerID
        )
    }

    private fun BitchatMessage.toContentValues(
        descriptor: ConversationDescriptor,
        localPeerID: String?,
        localNickname: String?,
        sourceTransport: String,
        mentionsJson: String?
    ): ContentValues {
        val now = System.currentTimeMillis()
        val delivery = deliveryStatus
        val direction = when {
            sender == "system" -> "system"
            senderPeerID != null && senderPeerID == localPeerID -> "outgoing"
            !localNickname.isNullOrBlank() && sender == localNickname -> "outgoing"
            else -> "incoming"
        }
        val remoteSenderID = if (direction == "incoming") senderPeerID else null
        val remoteRecipientID = when (descriptor.kind) {
            ConversationKind.DIRECT -> descriptor.peerID
            else -> null
        }

        return ContentValues().apply {
            put("message_id", id)
            put("conversation_key", descriptor.key)
            put("conversation_kind", descriptor.kind.name)
            put("sender", sender)
            put("content", content)
            put("message_type", type.name)
            put("timestamp_ms", timestamp.time)
            put("is_relay", if (isRelay) 1 else 0)
            put("original_sender", originalSender)
            put("is_private", if (isPrivate) 1 else 0)
            put("recipient_nickname", recipientNickname)
            put("sender_peer_id", senderPeerID)
            put("mentions_json", mentionsJson)
            put("channel_id", descriptor.channelID ?: channel)
            put("encrypted_content", encryptedContent)
            put("is_encrypted", if (isEncrypted) 1 else 0)
            put("pow_difficulty", powDifficulty)
            put("source_transport", sourceTransport)
            put("sync_state", if (direction == "incoming") "synced" else "dirty")
            put("sync_attempt_count", 0)
            put("local_direction", direction)
            put("remote_sender_id", remoteSenderID)
            put("remote_recipient_id", remoteRecipientID)
            put("received_at_ms", now)
            put("last_modified_at_ms", now)
            putAll(delivery.toContentValues())
        }
    }

    private fun android.database.Cursor.toMessage(): BitchatMessage {
        return BitchatMessage(
            id = getStringOrNull("message_id") ?: "",
            sender = getStringOrNull("sender") ?: "",
            content = getStringOrNull("content") ?: "",
            type = getStringOrNull("message_type")?.let { BitchatMessageType.valueOf(it) } ?: BitchatMessageType.Message,
            timestamp = Date(getLongOrNull("timestamp_ms") ?: 0L),
            isRelay = (getIntOrNull("is_relay") ?: 0) == 1,
            originalSender = getStringOrNull("original_sender"),
            isPrivate = (getIntOrNull("is_private") ?: 0) == 1,
            recipientNickname = getStringOrNull("recipient_nickname"),
            senderPeerID = getStringOrNull("sender_peer_id"),
            mentions = getStringOrNull("mentions_json")?.let { json ->
                runCatching { gson.fromJson<List<String>>(json, mentionsType) }.getOrNull()
            },
            channel = getStringOrNull("channel_id"),
            encryptedContent = getBlobOrNull("encrypted_content"),
            isEncrypted = (getIntOrNull("is_encrypted") ?: 0) == 1,
            deliveryStatus = toDeliveryStatus(),
            powDifficulty = getIntOrNull("pow_difficulty")
        )
    }

    private fun android.database.Cursor.toDeliveryStatus(): DeliveryStatus? {
        return when (getStringOrNull("delivery_kind")) {
            "sending" -> DeliveryStatus.Sending
            "sent" -> DeliveryStatus.Sent
            "delivered" -> DeliveryStatus.Delivered(
                to = getStringOrNull("delivery_actor") ?: "",
                at = Date(getLongOrNull("delivery_at_ms") ?: 0L)
            )
            "read" -> DeliveryStatus.Read(
                by = getStringOrNull("delivery_actor") ?: "",
                at = Date(getLongOrNull("delivery_at_ms") ?: 0L)
            )
            "failed" -> DeliveryStatus.Failed(
                reason = getStringOrNull("delivery_reason") ?: ""
            )
            "partial" -> DeliveryStatus.PartiallyDelivered(
                reached = getIntOrNull("delivery_reached") ?: 0,
                total = getIntOrNull("delivery_total") ?: 0
            )
            else -> null
        }
    }

    private fun DeliveryStatus?.toContentValues(): ContentValues {
        return ContentValues().apply {
            putNull("delivery_kind")
            putNull("delivery_actor")
            putNull("delivery_at_ms")
            putNull("delivery_reason")
            putNull("delivery_reached")
            putNull("delivery_total")
            when (this@toContentValues) {
                null -> Unit
                is DeliveryStatus.Sending -> put("delivery_kind", "sending")
                is DeliveryStatus.Sent -> put("delivery_kind", "sent")
                is DeliveryStatus.Delivered -> {
                    put("delivery_kind", "delivered")
                    put("delivery_actor", to)
                    put("delivery_at_ms", at.time)
                }
                is DeliveryStatus.Read -> {
                    put("delivery_kind", "read")
                    put("delivery_actor", by)
                    put("delivery_at_ms", at.time)
                }
                is DeliveryStatus.Failed -> {
                    put("delivery_kind", "failed")
                    put("delivery_reason", reason)
                }
                is DeliveryStatus.PartiallyDelivered -> {
                    put("delivery_kind", "partial")
                    put("delivery_reached", reached)
                    put("delivery_total", total)
                }
            }
        }
    }

    private fun android.database.Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun android.database.Cursor.getLongOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun android.database.Cursor.getIntOrNull(columnName: String): Int? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getInt(index) else null
    }

    private fun android.database.Cursor.getBlobOrNull(columnName: String): ByteArray? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getBlob(index) else null
    }

    companion object {
        private const val GLOBAL_KEY = "mesh:global"
        private const val GROUP_PREFIX = "grp_"

        @Volatile
        private var INSTANCE: ConversationRepository? = null

        fun getInstance(context: Context): ConversationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConversationRepository(context).also { INSTANCE = it }
            }
        }
    }
}
