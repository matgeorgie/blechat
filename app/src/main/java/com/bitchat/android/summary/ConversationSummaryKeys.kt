package com.bitchat.android.summary

object ConversationSummaryKeys {
    private const val GLOBAL_KEY = "mesh:global"
    private const val GROUP_PREFIX = "grp_"

    fun global(): String = GLOBAL_KEY

    fun direct(peerID: String): String = "dm:$peerID"

    fun channel(channel: String): String {
        return if (channel.startsWith(GROUP_PREFIX)) "group:$channel" else "channel:$channel"
    }
}
