package com.bitchat.android.services

import android.content.Context
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.DeliveryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide chat state that now mirrors durable conversation storage.
 * UI still consumes the same flows, but messages are persisted immediately.
 */
object AppStateStore {
    private val seenMessageIds = mutableSetOf<String>()
    @Volatile private var repository: ConversationRepository? = null
    @Volatile private var localPeerID: String? = null
    @Volatile private var localNickname: String? = null

    private val _peers = MutableStateFlow<List<String>>(emptyList())
    val peers: StateFlow<List<String>> = _peers.asStateFlow()

    private val _publicMessages = MutableStateFlow<List<BitchatMessage>>(emptyList())
    val publicMessages: StateFlow<List<BitchatMessage>> = _publicMessages.asStateFlow()

    private val _privateMessages = MutableStateFlow<Map<String, List<BitchatMessage>>>(emptyMap())
    val privateMessages: StateFlow<Map<String, List<BitchatMessage>>> = _privateMessages.asStateFlow()

    private val _channelMessages = MutableStateFlow<Map<String, List<BitchatMessage>>>(emptyMap())
    val channelMessages: StateFlow<Map<String, List<BitchatMessage>>> = _channelMessages.asStateFlow()

    fun initialize(context: Context) {
        if (repository != null) return
        synchronized(this) {
            if (repository != null) return
            repository = ConversationRepository.getInstance(context.applicationContext)
            applySnapshot(repository!!.loadSnapshot())
        }
    }

    fun setLocalIdentity(peerID: String?, nickname: String?) {
        localPeerID = peerID
        localNickname = nickname
    }

    fun setPeers(ids: List<String>) {
        _peers.value = ids
    }

    fun addPublicMessage(msg: BitchatMessage) {
        synchronized(this) {
            if (seenMessageIds.contains(msg.id)) return
            seenMessageIds.add(msg.id)
            repository?.persistPublicMessage(msg, localPeerID, localNickname)
            _publicMessages.value = _publicMessages.value + msg
        }
    }

    fun addPrivateMessage(peerID: String, msg: BitchatMessage) {
        synchronized(this) {
            if (seenMessageIds.contains(msg.id)) return
            seenMessageIds.add(msg.id)
            repository?.persistPrivateMessage(peerID, msg, localPeerID, localNickname)
            val map = _privateMessages.value.toMutableMap()
            map[peerID] = (map[peerID] ?: emptyList()) + msg
            _privateMessages.value = map
        }
    }

    private fun statusPriority(status: DeliveryStatus?): Int = when (status) {
        null -> 0
        is DeliveryStatus.Sending -> 1
        is DeliveryStatus.Sent -> 2
        is DeliveryStatus.PartiallyDelivered -> 3
        is DeliveryStatus.Delivered -> 4
        is DeliveryStatus.Read -> 5
        is DeliveryStatus.Failed -> 0
    }

    fun updateMessageStatus(messageID: String, status: DeliveryStatus) {
        synchronized(this) {
            repository?.updateMessageStatus(messageID, status)

            val privateMap = _privateMessages.value.toMutableMap()
            var privateChanged = false
            privateMap.keys.toList().forEach { peer ->
                val list = privateMap[peer]?.toMutableList() ?: mutableListOf()
                val index = list.indexOfFirst { it.id == messageID }
                if (index >= 0 && statusPriority(status) >= statusPriority(list[index].deliveryStatus)) {
                    list[index] = list[index].copy(deliveryStatus = status)
                    privateMap[peer] = list
                    privateChanged = true
                }
            }
            if (privateChanged) {
                _privateMessages.value = privateMap
            }

            val publicList = _publicMessages.value.toMutableList()
            val publicIndex = publicList.indexOfFirst { it.id == messageID }
            if (publicIndex >= 0 && statusPriority(status) >= statusPriority(publicList[publicIndex].deliveryStatus)) {
                publicList[publicIndex] = publicList[publicIndex].copy(deliveryStatus = status)
                _publicMessages.value = publicList
            }

            val channelMap = _channelMessages.value.toMutableMap()
            var channelChanged = false
            channelMap.keys.toList().forEach { channel ->
                val list = channelMap[channel]?.toMutableList() ?: mutableListOf()
                val index = list.indexOfFirst { it.id == messageID }
                if (index >= 0 && statusPriority(status) >= statusPriority(list[index].deliveryStatus)) {
                    list[index] = list[index].copy(deliveryStatus = status)
                    channelMap[channel] = list
                    channelChanged = true
                }
            }
            if (channelChanged) {
                _channelMessages.value = channelMap
            }
        }
    }

    fun addChannelMessage(channel: String, msg: BitchatMessage) {
        synchronized(this) {
            if (seenMessageIds.contains(msg.id)) return
            seenMessageIds.add(msg.id)
            repository?.persistChannelMessage(channel, msg, localPeerID, localNickname)
            val map = _channelMessages.value.toMutableMap()
            map[channel] = (map[channel] ?: emptyList()) + msg
            _channelMessages.value = map
        }
    }

    fun removeMessageById(messageID: String) {
        synchronized(this) {
            repository?.removeMessageById(messageID)
            seenMessageIds.remove(messageID)
            _publicMessages.value = _publicMessages.value.filterNot { it.id == messageID }
            _privateMessages.value = _privateMessages.value.mapValues { (_, messages) ->
                messages.filterNot { it.id == messageID }
            }
            _channelMessages.value = _channelMessages.value.mapValues { (_, messages) ->
                messages.filterNot { it.id == messageID }
            }
        }
    }

    fun clearPublicConversation() {
        synchronized(this) {
            repository?.clearConversationHistoryPublic()
            _publicMessages.value = emptyList()
        }
    }

    fun clearPrivateConversation(peerID: String) {
        synchronized(this) {
            repository?.clearConversationHistoryDirect(peerID)
            val map = _privateMessages.value.toMutableMap()
            map[peerID] = emptyList()
            _privateMessages.value = map
        }
    }

    fun clearChannelConversation(channel: String) {
        synchronized(this) {
            repository?.clearConversationHistoryChannel(channel)
            val map = _channelMessages.value.toMutableMap()
            map[channel] = emptyList()
            _channelMessages.value = map
        }
    }

    fun mergePrivateConversationKeys(targetPeerID: String, keysToMerge: List<String>) {
        synchronized(this) {
            repository?.mergePrivateConversationAliases(targetPeerID, keysToMerge)
            val currentChats = _privateMessages.value.toMutableMap()
            val targetMessages = currentChats[targetPeerID]?.toMutableList() ?: mutableListOf()
            var changed = false
            keysToMerge.distinct()
                .filter { it != targetPeerID }
                .forEach { key ->
                    val aliasMessages = currentChats.remove(key)
                    if (!aliasMessages.isNullOrEmpty()) {
                        targetMessages.addAll(aliasMessages)
                        changed = true
                    }
                }
            if (changed) {
                currentChats[targetPeerID] = targetMessages
                _privateMessages.value = currentChats
            }
        }
    }

    fun clear() {
        synchronized(this) {
            seenMessageIds.clear()
            _peers.value = emptyList()
            _publicMessages.value = emptyList()
            _privateMessages.value = emptyMap()
            _channelMessages.value = emptyMap()
        }
    }

    fun wipePersistentStore() {
        synchronized(this) {
            repository?.wipeAllMessages()
            seenMessageIds.clear()
            _publicMessages.value = emptyList()
            _privateMessages.value = emptyMap()
            _channelMessages.value = emptyMap()
        }
    }

    private fun applySnapshot(snapshot: PersistedChatSnapshot) {
        seenMessageIds.clear()
        snapshot.publicMessages.forEach { seenMessageIds.add(it.id) }
        snapshot.privateMessages.values.flatten().forEach { seenMessageIds.add(it.id) }
        snapshot.channelMessages.values.flatten().forEach { seenMessageIds.add(it.id) }
        _publicMessages.value = snapshot.publicMessages
        _privateMessages.value = snapshot.privateMessages
        _channelMessages.value = snapshot.channelMessages
    }
}
