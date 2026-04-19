package com.bitchat.android.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummaryEngineTest {
    private val engine = ConversationSummaryEngine()

    @Test
    fun `meetup planning surfaces topic update and question`() {
        val summary = engine.summarizeConversation(
            messages = listOf(
                msg("1", "alice", "Meetup tonight at 7pm near the river cafe"),
                msg("2", "bob", "Actually location changed to Gate 3"),
                msg("3", "alice", "@matt can you bring the speaker?"),
                msg("4", "cara", "ok")
            ),
            currentUserNickname = "matt"
        )

        assertTrue(summary.importantUpdates.any { it.text.contains("Gate 3") })
        assertTrue(summary.actionItems.any { it.text.contains("bring the speaker") })
        assertTrue(summary.notificationText?.contains("Main update") == true)
    }

    @Test
    fun `noisy meme burst is down ranked`() {
        val summary = engine.summarizeConversation(
            messages = listOf(
                msg("1", "alice", "lol"),
                msg("2", "bob", "😂😂😂"),
                msg("3", "cara", "ok"),
                msg("4", "dave", "Meeting moved to 8pm")
            )
        )

        assertEquals(1, summary.importantUpdates.size)
        assertTrue(summary.importantUpdates.first().text.contains("8pm"))
        assertTrue(summary.openQuestions.isEmpty())
    }

    @Test
    fun `attachment style file paths are ignored for updates`() {
        val summary = engine.summarizeConversation(
            messages = listOf(
                msg("1", "mathew", "/data/user/0/com.bitchat.droid/files/voicenotes/outgoing/voice_202604.m4a"),
                msg("2", "mathew", "scheduled practice for 8:00 pm today"),
                msg("3", "anon1592", "when are you available for coffee?")
            )
        )

        assertTrue(summary.importantUpdates.none { it.text.contains("/data/user/0") })
        assertTrue(summary.importantUpdates.any { it.text.contains("8:00 pm") })
        assertEquals(1, summary.openQuestions.size)
    }

    @Test
    fun `delayed sync burst keeps strongest logistics signal`() {
        val summary = engine.summarizeNotificationBatch(
            messages = listOf(
                msg("1", "alice", "syncing now"),
                msg("2", "bob", "Gate changed to 4"),
                msg("3", "cara", "see you soon"),
                msg("4", "alice", "final plan is Gate 4 at 9pm")
            )
        )

        assertNotNull(summary)
        assertTrue(summary!!.text.contains("Gate 4") || summary.text.contains("9pm"))
    }

    @Test
    fun `unanswered question is kept as open question`() {
        val summary = engine.summarizeConversation(
            messages = listOf(
                msg("1", "alice", "Who is picking up the batteries?"),
                msg("2", "bob", "lol"),
                msg("3", "cara", "still waiting on that answer")
            )
        )

        assertEquals(1, summary.openQuestions.size)
        assertTrue(summary.openQuestions.first().text.contains("batteries"))
    }

    @Test
    fun `time and location changes outrank chatter`() {
        val summary = engine.summarizeConversation(
            messages = listOf(
                msg("1", "alice", "The meetup is tomorrow"),
                msg("2", "bob", "Actually moved to Gate 3 at 6:30pm"),
                msg("3", "cara", "sounds good"),
                msg("4", "dave", "urgent please do not go to the old entrance")
            )
        )

        assertTrue(summary.importantUpdates.any { it.text.contains("Gate 3") })
        assertTrue(summary.importantUpdates.any { it.text.contains("old entrance") || it.text.contains("urgent") })
    }

    private fun msg(id: String, sender: String, text: String): SummaryInputMessage {
        return SummaryInputMessage(
            id = id,
            sender = sender,
            text = text,
            timestampMs = id.toLong() * 1000L
        )
    }
}
