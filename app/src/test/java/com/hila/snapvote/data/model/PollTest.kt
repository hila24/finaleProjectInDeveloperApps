package com.hila.snapvote.data.model

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * The poll maths – winner, percentages and averages – is the part of the app that a
 * wrong result would be hardest to notice by eye, so it is covered here.
 */
class PollTest {

    private fun poll(
        mode: String = Poll.MODE_SINGLE,
        voteCount: Int = 0,
        tally: Map<String, Int> = emptyMap(),
        hoursFromNow: Long = 24,
        images: List<PollImage> = listOf(
            PollImage(id = "img_0", label = "A"),
            PollImage(id = "img_1", label = "B"),
        ),
    ) = Poll(
        id = "poll_1",
        question = "איזו תמונה?",
        ownerId = "owner",
        images = images,
        mode = mode,
        deadline = Timestamp(Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hoursFromNow))),
        voteCount = voteCount,
        tally = tally,
    )

    // ------------------------------------------------------------------ winner

    @Test
    fun `winner is null while nobody voted`() {
        assertNull(poll(voteCount = 0, tally = mapOf("img_0" to 0, "img_1" to 0)).winner())
    }

    @Test
    fun `winner is the image with the highest score`() {
        val result = poll(voteCount = 5, tally = mapOf("img_0" to 2, "img_1" to 3)).winner()
        assertEquals("img_1", result?.id)
        assertEquals("B", result?.label)
    }

    @Test
    fun `winner still resolves when an image got no votes at all`() {
        val result = poll(voteCount = 4, tally = mapOf("img_0" to 4, "img_1" to 0)).winner()
        assertEquals("img_0", result?.id)
    }

    // -------------------------------------------------------------- percentage

    @Test
    fun `percent is zero for every image before the first vote`() {
        val subject = poll(voteCount = 0, tally = mapOf("img_0" to 0, "img_1" to 0))
        assertEquals(0, subject.percentOf("img_0"))
        assertEquals(0, subject.percentOf("img_1"))
    }

    @Test
    fun `percentages split the votes`() {
        val subject = poll(voteCount = 4, tally = mapOf("img_0" to 1, "img_1" to 3))
        assertEquals(25, subject.percentOf("img_0"))
        assertEquals(75, subject.percentOf("img_1"))
    }

    @Test
    fun `percentages are rounded, not truncated`() {
        // 1 of 3 is 33.33 -> 33, 2 of 3 is 66.67 -> 67
        val subject = poll(voteCount = 3, tally = mapOf("img_0" to 1, "img_1" to 2))
        assertEquals(33, subject.percentOf("img_0"))
        assertEquals(67, subject.percentOf("img_1"))
    }

    @Test
    fun `an unknown image id is worth zero percent`() {
        val subject = poll(voteCount = 2, tally = mapOf("img_0" to 2))
        assertEquals(0, subject.percentOf("img_does_not_exist"))
    }

    // ----------------------------------------------------------------- ratings

    @Test
    fun `average rating divides the stars by the number of voters`() {
        // three voters gave 5, 4 and 3 stars -> 12 / 3 = 4.0
        val subject = poll(mode = Poll.MODE_RATING, voteCount = 3, tally = mapOf("img_0" to 12))
        assertEquals(4.0f, subject.averageOf("img_0"), 0.001f)
    }

    @Test
    fun `average rating is zero before anyone voted`() {
        val subject = poll(mode = Poll.MODE_RATING, voteCount = 0, tally = mapOf("img_0" to 0))
        assertEquals(0f, subject.averageOf("img_0"), 0.001f)
    }

    // ---------------------------------------------------------------- deadline

    @Test
    fun `a poll with a future deadline is open`() {
        val subject = poll(hoursFromNow = 5)
        assertFalse(subject.isClosed)
        assertTrue(subject.millisLeft > 0)
    }

    @Test
    fun `a poll whose deadline passed is closed`() {
        val subject = poll(hoursFromNow = -1)
        assertTrue(subject.isClosed)
        assertTrue(subject.millisLeft < 0)
    }

    @Test
    fun `a poll without a deadline counts as closed rather than open forever`() {
        assertTrue(Poll(question = "no deadline").isClosed)
    }

    // ------------------------------------------------------------- visibility

    @Test
    fun `visibleTo carries the owner and their friends`() {
        val subject = Poll(ownerId = "owner", visibleTo = listOf("owner", "friend_1", "friend_2"))
        assertTrue(subject.visibleTo.contains("owner"))
        assertTrue(subject.visibleTo.contains("friend_2"))
        assertFalse(subject.visibleTo.contains("stranger"))
    }

    @Test
    fun `a brand new poll has no votes and no winner`() {
        val subject = Poll(question = "חדש")
        assertEquals(0, subject.voteCount)
        assertNull(subject.winner())
    }
}
