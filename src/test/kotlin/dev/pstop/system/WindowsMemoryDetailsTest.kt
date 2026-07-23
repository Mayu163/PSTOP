package dev.pstop.system

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsMemoryDetailsTest {
    @Test
    fun `reads requested memory details from Windows in one native query`() {
        val details = assertNotNull(queryWindowsMemoryDetails())

        assertTrue(details.pagedPoolBytes > 0L)
        assertTrue(details.nonPagedPoolBytes > 0L)
        assertTrue(details.committedBytes > 0L)
        assertTrue(details.cachedBytes >= 0L)
    }
}
