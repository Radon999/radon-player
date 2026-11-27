package org.omarchy.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Followed artists: followed by themselves (on), never again once unfollowed, nothing undone by switching it off. */
class FollowRulesTest {
    private val R = FollowRules

    @Test fun yourArtistsAreFollowedByThemselves() {
        val (f, s) = R.auto(emptyList(), emptySet(), listOf("Hippo Campus", "Radiohead", "hippo campus", "Unknown artist"), on = true)
        assertEquals(listOf("Hippo Campus", "Radiohead"), f)
        assertEquals(setOf("hippo campus", "radiohead"), s)
    }

    @Test fun anUnfollowStays() {
        var (f, s) = R.auto(emptyList(), emptySet(), listOf("Tyler", "Radiohead"), on = true)
        R.unfollow(f, s, listOf("Tyler")).let { f = it.first; s = it.second }
        assertEquals(listOf("Radiohead"), f)
        R.auto(f, s, listOf("Tyler", "Radiohead"), on = true).let { f = it.first; s = it.second }
        assertFalse("still unfollowed after the next look", R.isFollowed(f, "tyler"))
    }

    @Test fun offFollowsNoOneNewAndUnfollowsNoOne() {
        val (f, s) = R.auto(listOf("Radiohead"), setOf("radiohead"), listOf("Radiohead", "Beach House"), on = false)
        assertEquals(listOf("Radiohead"), f)
        assertEquals(setOf("radiohead"), s)
        // Turned on again: the one added meanwhile is followed at once
        val (f2, _) = R.auto(f, s, listOf("Radiohead", "Beach House"), on = true)
        assertEquals(listOf("Radiohead", "Beach House"), f2)
    }

    @Test fun anyoneCanBeFollowedByHand() {
        val (f, s) = R.follow(listOf("Radiohead"), setOf("radiohead"), listOf("Clairo", "radiohead"))
        assertEquals(listOf("Radiohead", "Clairo"), f)
        assertTrue("clairo" in s)
        assertEquals("Radiohead\nClairo", R.write(f))
        assertEquals(f, R.parse(R.write(f)))
    }
}
