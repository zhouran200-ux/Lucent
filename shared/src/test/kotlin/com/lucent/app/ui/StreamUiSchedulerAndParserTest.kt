package com.lucent.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUiSchedulerAndParserTest {

    @Test
    fun testStreamUiSchedulerBurstFlush() {
        var flushedCount = 0
        var lastFlushedContent = ""

        val scheduler = StreamUiScheduler(
            timeWindowMs = 5000L, // long time window to isolate burst condition
            charBurstThreshold = 20
        ) { content, _ ->
            flushedCount++
            lastFlushedContent = content
        }

        // 10 chars -> should not flush yet
        scheduler.append("1234567890", null)
        assertEquals(0, flushedCount)

        // 15 more chars -> total pending = 25 >= 20 -> should trigger burst flush!
        scheduler.append("ABCDEFGHIJKLMNO", null)
        assertEquals(1, flushedCount)
        assertEquals("1234567890ABCDEFGHIJKLMNO", lastFlushedContent)

        // Force flush at end
        scheduler.append("tail", null)
        scheduler.forceFlush()
        assertEquals(2, flushedCount)
        assertEquals("1234567890ABCDEFGHIJKLMNOtail", scheduler.getFinalContent())
    }

    @Test
    fun testStreamUiSchedulerStructuralBoundaryFlush() {
        var flushedCount = 0
        var lastFlushedContent = ""

        val scheduler = StreamUiScheduler(
            timeWindowMs = 5000L,
            charBurstThreshold = 100
        ) { content, _ ->
            flushedCount++
            lastFlushedContent = content
        }

        // Short text with \n\n (structural boundary) -> triggers flush immediately
        scheduler.append("第一段文字\n\n", null)
        assertEquals(1, flushedCount)
        assertEquals("第一段文字\n\n", lastFlushedContent)
    }

    @Test
    fun testIncrementalMarkdownParserClosedSplits() {
        val parser = IncrementalMarkdownParser()

        // Stream phase 1: First paragraph streaming
        val blocks1 = parser.parse("这是第一段正在生成")
        assertTrue(blocks1.isNotEmpty())
        assertTrue(blocks1[0] is MdBlock.Paragraph)

        // Stream phase 2: First paragraph completed with double newline, second paragraph starts
        val text2 = "这是第一段已经完成。\n\n这是第二段正在进行..."
        val blocks2 = parser.parse(text2)
        assertTrue(blocks2.size >= 2)

        // Stream phase 3: Second paragraph grows
        val text3 = "这是第一段已经完成。\n\n这是第二段正在进行...增加更多文字。"
        val blocks3 = parser.parse(text3)
        assertTrue(blocks3.size >= 2)
        assertEquals("这是第一段已经完成。", (blocks3[0] as MdBlock.Paragraph).text)
    }
}
