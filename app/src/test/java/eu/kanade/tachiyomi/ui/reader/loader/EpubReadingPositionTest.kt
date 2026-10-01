package eu.kanade.tachiyomi.ui.reader.loader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpubReadingPositionTest {
    @Test
    fun `reading location follows text after page size changes`() {
        assertEquals(1, EpubReadingPosition.pageForOffset(listOf(0L, 100L, 200L), 150L))
        assertEquals(3, EpubReadingPosition.pageForOffset(listOf(0L, 50L, 100L, 150L, 200L), 150L))
    }

    @Test
    fun `exact page boundary belongs to the new page`() {
        assertEquals(2, EpubReadingPosition.pageForOffset(listOf(0L, 100L, 200L), 200L))
    }

    @Test
    fun `shorter replacement book clamps to final page`() {
        assertEquals(1, EpubReadingPosition.pageForOffset(listOf(0L, 100L), 300L))
    }

    @Test
    fun `invalid position and empty book have no resume page`() {
        assertNull(EpubReadingPosition.pageForOffset(emptyList(), 0L))
        assertNull(EpubReadingPosition.pageForOffset(listOf(0L), -1L))
    }
}
