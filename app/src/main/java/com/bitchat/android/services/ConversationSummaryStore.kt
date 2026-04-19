package com.bitchat.android.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConversationSummaryStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    @Synchronized
    fun getLastOpenedAt(conversationKey: String): Long? {
        return loadMap()[conversationKey]
    }

    @Synchronized
    fun markOpened(conversationKey: String, openedAtMs: Long = System.currentTimeMillis()): Long? {
        val map = loadMap().toMutableMap()
        val previous = map[conversationKey]
        map[conversationKey] = openedAtMs
        persistMap(map)
        return previous
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(STORAGE_KEY).apply()
    }

    private fun loadMap(): Map<String, Long> {
        val json = prefs.getString(STORAGE_KEY, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Long>>() {}.type
        return runCatching { gson.fromJson<Map<String, Long>>(json, type) }.getOrNull().orEmpty()
    }

    private fun persistMap(map: Map<String, Long>) {
        prefs.edit().putString(STORAGE_KEY, gson.toJson(map)).apply()
    }

    companion object {
        private const val PREFS_NAME = "conversation_summary_store"
        private const val STORAGE_KEY = "last_opened_map"

        @Volatile
        private var INSTANCE: ConversationSummaryStore? = null

        fun getInstance(context: Context): ConversationSummaryStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConversationSummaryStore(context).also { INSTANCE = it }
            }
        }
    }
}
