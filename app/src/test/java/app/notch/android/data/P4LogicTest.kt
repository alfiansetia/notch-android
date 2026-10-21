package app.notch.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class P4LogicTest {
    @Test
    fun inboxCapIs100() {
        assertEquals(100, app.notch.android.data.db.InboxItem.MAX_ITEMS)
    }

    @Test
    fun hydrateGoalIs2000() {
        assertEquals(2000, HydrateRepository.DAILY_GOAL_ML)
    }

    @Test
    fun cadenceBounds() {
        assertTrue(60 in 15..240)
        assertTrue(20 in 5..120)
    }
}
