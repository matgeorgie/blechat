package com.bitchat.android.ui

import com.bitchat.android.model.BitchatMessage
import com.google.gson.Gson
import java.util.Date
import java.util.UUID

data class GroupInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val memberPeerIDs: List<String>,
    val memberNicknames: Map<String, String>,
    val adminPeerIDs: List<String>,
    val createdByPeerID: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = createdAtMs
) {
    fun isMember(peerID: String): Boolean = memberPeerIDs.contains(peerID)
    fun isAdmin(peerID: String): Boolean = adminPeerIDs.contains(peerID)

    fun withResolvedAdmins(): GroupInfo {
        val resolvedAdmins = adminPeerIDs.filter { memberPeerIDs.contains(it) }.ifEmpty {
            listOfNotNull(memberPeerIDs.firstOrNull())
        }
        return if (resolvedAdmins == adminPeerIDs) this else copy(adminPeerIDs = resolvedAdmins)
    }
}

class GroupManager(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private val dataManager: DataManager
) {
    companion object {
        const val INVITE_PREFIX = "[GROUP_INVITE]"
        const val MESSAGE_PREFIX = "[GROUP_MSG]"
        const val CONTROL_PREFIX = "[GROUP_CTRL]"
        private const val FILE_PREFIX = "[GROUP_FILE]"
        private const val GROUP_PREFIX = "grp_"
        const val ACTION_METADATA_UPDATED = "metadata_updated"
        const val ACTION_MEMBERSHIP_UPDATED = "membership_updated"
        const val ACTION_MEMBER_REMOVED = "member_removed"
        const val ACTION_MEMBER_LEFT = "member_left"

        fun isGroupChannel(channel: String): Boolean = channel.startsWith(GROUP_PREFIX)

        fun encodeGroupFileName(groupID: String, messageID: String, originalFileName: String): String {
            return FILE_PREFIX + Gson().toJson(
                GroupFilePayload(
                    groupID = groupID,
                    messageID = messageID,
                    originalFileName = originalFileName
                )
            )
        }

        fun decodeGroupFileName(fileName: String): GroupFilePayload? {
            if (!fileName.startsWith(FILE_PREFIX)) return null
            return runCatching {
                Gson().fromJson(
                    fileName.removePrefix(FILE_PREFIX),
                    GroupFilePayload::class.java
                )
            }.getOrNull()
        }
    }

    data class GroupInvitePayload(
        val groupID: String,
        val name: String,
        val memberPeerIDs: List<String>,
        val memberNicknames: Map<String, String>,
        val createdByPeerID: String
    )

    data class GroupMessagePayload(
        val groupID: String,
        val messageID: String,
        val content: String
    )

    data class GroupControlPayload(
        val groupID: String,
        val action: String,
        val actorPeerID: String,
        val name: String,
        val description: String,
        val memberPeerIDs: List<String>,
        val memberNicknames: Map<String, String>,
        val adminPeerIDs: List<String>,
        val createdByPeerID: String,
        val createdAtMs: Long,
        val updatedAtMs: Long,
        val targetPeerID: String? = null,
        val targetNickname: String? = null
    )

    data class GroupFilePayload(
        val groupID: String,
        val messageID: String,
        val originalFileName: String
    )

    fun loadPersistedGroups(myPeerID: String) {
        val groups = dataManager.loadGroups()
            .filterValues { it.isMember(myPeerID) }
        state.setGroupInfoMap(groups)
        groups.keys.forEach { groupID ->
            channelManager.joinChannel(groupID, myPeerID = myPeerID)
        }
    }

    fun createGroup(
        name: String,
        selectedPeerIDs: List<String>,
        myPeerID: String,
        peerNicknames: Map<String, String>,
        myNickname: String
    ): GroupInfo {
        val memberPeerIDs = (selectedPeerIDs + myPeerID).distinct()
        val memberNicknames = memberPeerIDs.associateWith { peerID ->
            if (peerID == myPeerID) {
                myNickname
            } else {
                peerNicknames[peerID] ?: peerID.take(12)
            }
        }
        val groupInfo = GroupInfo(
            id = GROUP_PREFIX + UUID.randomUUID().toString().replace("-", "").take(12),
            name = name,
            description = "",
            memberPeerIDs = memberPeerIDs,
            memberNicknames = memberNicknames,
            adminPeerIDs = listOf(myPeerID),
            createdByPeerID = myPeerID
        )
        upsertGroup(groupInfo, myPeerID)
        return groupInfo
    }

    fun joinGroupFromInvite(
        payload: GroupInvitePayload,
        myPeerID: String,
        inviterName: String?
    ) {
        if (!payload.memberPeerIDs.contains(myPeerID)) return
        val info = GroupInfo(
            id = payload.groupID,
            name = payload.name,
            memberPeerIDs = payload.memberPeerIDs.distinct(),
            memberNicknames = payload.memberNicknames,
            adminPeerIDs = listOf(payload.createdByPeerID),
            createdByPeerID = payload.createdByPeerID
        ).withResolvedAdmins()
        val isNew = state.getGroupInfoMapValue()[info.id] == null
        upsertGroup(info, myPeerID)
        if (isNew) {
            addSystemEvent(
                groupID = info.id,
                text = "${inviterName ?: "someone"} added you to ${info.name}"
            )
        }
    }

    fun applyControlMessage(payload: GroupControlPayload, myPeerID: String, actorName: String?) {
        val existing = state.getGroupInfoMapValue()[payload.groupID]
        if (existing != null && existing.updatedAtMs > payload.updatedAtMs) {
            return
        }

        val normalized = GroupInfo(
            id = payload.groupID,
            name = payload.name,
            description = payload.description,
            memberPeerIDs = payload.memberPeerIDs.distinct(),
            memberNicknames = payload.memberNicknames,
            adminPeerIDs = payload.adminPeerIDs.distinct(),
            createdByPeerID = payload.createdByPeerID,
            createdAtMs = payload.createdAtMs,
            updatedAtMs = payload.updatedAtMs
        ).withResolvedAdmins()

        if (!normalized.isMember(myPeerID)) {
            removeGroupLocally(payload.groupID)
            if (state.getCurrentChannelValue() == payload.groupID) {
                channelManager.switchToChannel(null)
            }
            return
        }

        upsertGroup(normalized, myPeerID)
        maybeAddSystemEvent(payload, normalized, actorName)
    }

    fun updateGroupDetails(
        groupID: String,
        name: String,
        description: String,
        actorPeerID: String
    ): GroupInfo? {
        val groupInfo = getGroupInfo(groupID) ?: return null
        if (!groupInfo.isAdmin(actorPeerID)) return null
        val updated = groupInfo.copy(
            name = name,
            description = description,
            updatedAtMs = System.currentTimeMillis()
        )
        upsertGroup(updated, actorPeerID)
        addSystemEvent(groupID, "Group info updated")
        return updated
    }

    fun addMembers(
        groupID: String,
        peerIDs: List<String>,
        actorPeerID: String,
        peerNicknames: Map<String, String>
    ): GroupInfo? {
        val groupInfo = getGroupInfo(groupID) ?: return null
        if (!groupInfo.isAdmin(actorPeerID)) return null

        val newPeerIDs = peerIDs
            .filterNot { groupInfo.memberPeerIDs.contains(it) }
            .distinct()
        if (newPeerIDs.isEmpty()) return null

        val updatedMembers = (groupInfo.memberPeerIDs + newPeerIDs).distinct()
        val updatedNicknames = groupInfo.memberNicknames.toMutableMap().apply {
            newPeerIDs.forEach { peerID ->
                put(peerID, peerNicknames[peerID] ?: peerID.take(12))
            }
        }
        val updated = groupInfo.copy(
            memberPeerIDs = updatedMembers,
            memberNicknames = updatedNicknames,
            updatedAtMs = System.currentTimeMillis()
        ).withResolvedAdmins()
        upsertGroup(updated, actorPeerID)
        val targetNames = newPeerIDs.mapNotNull { updated.memberNicknames[it] }
        if (targetNames.isNotEmpty()) {
            addSystemEvent(groupID, "Added ${targetNames.joinToString()}")
        }
        return updated
    }

    fun removeMember(
        groupID: String,
        targetPeerID: String,
        actorPeerID: String
    ): GroupInfo? {
        val groupInfo = getGroupInfo(groupID) ?: return null
        if (!groupInfo.isAdmin(actorPeerID) || targetPeerID == actorPeerID) return null
        if (!groupInfo.memberPeerIDs.contains(targetPeerID)) return null

        val updated = updateMembershipAfterRemoval(groupInfo, targetPeerID, actorPeerID)
        val removedName = groupInfo.memberNicknames[targetPeerID] ?: targetPeerID.take(12)
        if (updated != null) {
            addSystemEvent(groupID, "Removed $removedName")
        }
        return updated
    }

    fun leaveGroup(groupID: String, peerID: String): GroupInfo? {
        val groupInfo = getGroupInfo(groupID) ?: return null
        if (!groupInfo.memberPeerIDs.contains(peerID)) return null

        val updated = updateMembershipAfterRemoval(groupInfo, peerID, peerID)
        removeGroupLocally(groupID)
        if (state.getCurrentChannelValue() == groupID) {
            channelManager.switchToChannel(null)
        }
        return updated
    }

    fun clearAllGroups() {
        state.setGroupInfoMap(emptyMap())
    }

    fun buildInvitePayload(groupInfo: GroupInfo): GroupInvitePayload {
        return GroupInvitePayload(
            groupID = groupInfo.id,
            name = groupInfo.name,
            memberPeerIDs = groupInfo.memberPeerIDs,
            memberNicknames = groupInfo.memberNicknames,
            createdByPeerID = groupInfo.createdByPeerID
        )
    }

    fun buildControlPayload(
        groupInfo: GroupInfo,
        action: String,
        actorPeerID: String,
        targetPeerID: String? = null,
        targetNickname: String? = null
    ): GroupControlPayload {
        return GroupControlPayload(
            groupID = groupInfo.id,
            action = action,
            actorPeerID = actorPeerID,
            name = groupInfo.name,
            description = groupInfo.description,
            memberPeerIDs = groupInfo.memberPeerIDs,
            memberNicknames = groupInfo.memberNicknames,
            adminPeerIDs = groupInfo.adminPeerIDs,
            createdByPeerID = groupInfo.createdByPeerID,
            createdAtMs = groupInfo.createdAtMs,
            updatedAtMs = groupInfo.updatedAtMs,
            targetPeerID = targetPeerID,
            targetNickname = targetNickname
        )
    }

    fun buildGroupMessagePayload(groupID: String, messageID: String, content: String): GroupMessagePayload {
        return GroupMessagePayload(groupID = groupID, messageID = messageID, content = content)
    }

    fun getGroupInfo(groupID: String): GroupInfo? = state.getGroupInfoMapValue()[groupID]

    fun getOtherMembers(groupID: String, myPeerID: String): List<String> {
        return getGroupInfo(groupID)
            ?.memberPeerIDs
            ?.filter { it != myPeerID }
            .orEmpty()
    }

    fun isAdmin(groupID: String, peerID: String): Boolean {
        return getGroupInfo(groupID)?.isAdmin(peerID) == true
    }

    fun isKnownGroup(groupID: String): Boolean = state.getGroupInfoMapValue().containsKey(groupID)

    private fun upsertGroup(groupInfo: GroupInfo, myPeerID: String) {
        val updated = state.getGroupInfoMapValue().toMutableMap()
        updated[groupInfo.id] = groupInfo.withResolvedAdmins()
        state.setGroupInfoMap(updated)
        dataManager.saveGroup(updated[groupInfo.id]!!)
        channelManager.joinChannel(groupInfo.id, myPeerID = myPeerID)
    }

    private fun updateMembershipAfterRemoval(
        groupInfo: GroupInfo,
        removedPeerID: String,
        myPeerID: String
    ): GroupInfo? {
        val updatedMembers = groupInfo.memberPeerIDs.filter { it != removedPeerID }
        if (updatedMembers.isEmpty()) {
            removeGroupLocally(groupInfo.id)
            return null
        }

        val updatedNicknames = groupInfo.memberNicknames
            .filterKeys { updatedMembers.contains(it) }
        var updatedAdmins = groupInfo.adminPeerIDs.filter { updatedMembers.contains(it) }
        if (updatedAdmins.isEmpty()) {
            updatedAdmins = listOf(updatedMembers.first())
        }

        val updated = groupInfo.copy(
            memberPeerIDs = updatedMembers,
            memberNicknames = updatedNicknames,
            adminPeerIDs = updatedAdmins,
            updatedAtMs = System.currentTimeMillis()
        )
        upsertGroup(updated, myPeerID)
        return updated
    }

    private fun removeGroupLocally(groupID: String) {
        val updated = state.getGroupInfoMapValue().toMutableMap()
        updated.remove(groupID)
        state.setGroupInfoMap(updated)
        dataManager.removeGroup(groupID)
        channelManager.leaveChannel(groupID)
    }

    private fun maybeAddSystemEvent(
        payload: GroupControlPayload,
        groupInfo: GroupInfo,
        actorName: String?
    ) {
        val actorDisplay = actorName
            ?: groupInfo.memberNicknames[payload.actorPeerID]
            ?: payload.actorPeerID.take(12)
        val targetDisplay = payload.targetNickname
            ?: payload.targetPeerID?.let { groupInfo.memberNicknames[it] ?: it.take(12) }

        val text = when (payload.action) {
            ACTION_METADATA_UPDATED -> "$actorDisplay updated the group info"
            ACTION_MEMBERSHIP_UPDATED -> {
                val target = targetDisplay ?: "new members"
                "$actorDisplay added $target"
            }
            ACTION_MEMBER_REMOVED -> {
                val target = targetDisplay ?: "a member"
                "$actorDisplay removed $target"
            }
            ACTION_MEMBER_LEFT -> {
                val target = targetDisplay ?: actorDisplay
                "$target left the group"
            }
            else -> null
        }
        text?.let { addSystemEvent(payload.groupID, it) }
    }

    private fun addSystemEvent(groupID: String, text: String) {
        messageManager.addChannelMessage(
            groupID,
            BitchatMessage(
                sender = "system",
                content = text,
                timestamp = Date(),
                channel = groupID
            )
        )
    }
}
