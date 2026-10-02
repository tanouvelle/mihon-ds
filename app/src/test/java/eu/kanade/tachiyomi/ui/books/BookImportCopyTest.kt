package eu.kanade.tachiyomi.ui.books

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BookImportCopyTest {
    @Test
    fun `copies full stream through repeated buffer reads at exact limit`() = runTest {
        val source = ByteArray(150000) { (it % 251).toByte() }
        val destination = ByteArrayOutputStream()
        copyBookBytes(ByteArrayInputStream(source), destination, source.size.toLong())
        assertArrayEquals(source, destination.toByteArray())
    }

    @Test
    fun `rejects oversized stream before writing past allowed size`() {
        val destination = ByteArrayOutputStream()
        assertThrows(BookImportTooLarge::class.java) {
            runTest { copyBookBytes(ByteArrayInputStream(ByteArray(100)), destination, 99) }
        }
        assertEquals(0, destination.size())
    }

    @Test
    fun `storage write failures propagate to staging cleanup`() {
        val output = object : OutputStream() {
            override fun write(value: Int) { throw IOException("Disk full") }
        }
        assertThrows(IOException::class.java) {
            runTest { copyBookBytes(ByteArrayInputStream(byteArrayOf(1, 2)), output) }
        }
    }
}
