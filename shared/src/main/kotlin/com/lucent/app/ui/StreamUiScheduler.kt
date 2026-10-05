package com.lucent.app.ui

/**
 * 生产级流式响应聚合与调度器 (StreamUiScheduler)
 *
 * 核心目标：
 * 1. 增量缓冲（Buffer）：高频到达的 Token / SSE 增量快速入队，绝不阻塞网络协程；
 * 2. 多维度自适应 Flush 判定：
 *    - ① 时间窗到达（默认 35ms，保证慢速网络或长思考停顿时打字机视觉连贯）；
 *    - ② 字符突发阈值（累积字符 >= 48 字符立即触发，避免高速模型倾泻时造成大块跳字）；
 *    - ③ 结构性边界触发（遇到 \n\n 或代码块闭合等段落边界立即刷新，确保排版即时成型）；
 *    - ④ 流完成（forceFlush 保证最后一个字符 100% 完整交付）；
 * 3. 将高频 Token 触发与 Compose UI 重组解耦，从源头消灭高频 recomposition 压力。
 */
class StreamUiScheduler(
    private val timeWindowMs: Long = 35L,
    private val charBurstThreshold: Int = 48,
    private val onFlush: (content: String, reasoning: String?) -> Unit
) {
    private val pendingContent = StringBuilder()
    private val pendingReasoning = StringBuilder()

    private val fullContent = StringBuilder()
    private val fullReasoning = StringBuilder()

    private var lastFlushTime = System.currentTimeMillis()

    fun append(deltaText: String, deltaThought: String?) {
        var hasStructuralBoundary = false

        if (deltaText.isNotEmpty()) {
            pendingContent.append(deltaText)
            fullContent.append(deltaText)
            if (deltaText.contains("\n\n") || deltaText.contains("```")) {
                hasStructuralBoundary = true
            }
        }

        if (!deltaThought.isNullOrEmpty()) {
            pendingReasoning.append(deltaThought)
            fullReasoning.append(deltaThought)
            if (deltaThought.contains("\n\n")) {
                hasStructuralBoundary = true
            }
        }

        val now = System.currentTimeMillis()
        val timeElapsed = now - lastFlushTime
        val pendingLen = pendingContent.length + pendingReasoning.length

        val shouldFlush = hasStructuralBoundary ||
                (pendingLen >= charBurstThreshold) ||
                (timeElapsed >= timeWindowMs && pendingLen > 0)

        if (shouldFlush) {
            flush(now)
        }
    }

    fun forceFlush() {
        flush(System.currentTimeMillis())
    }

    private fun flush(timestamp: Long) {
        lastFlushTime = timestamp
        pendingContent.clear()
        pendingReasoning.clear()
        onFlush(fullContent.toString(), fullReasoning.toString().ifBlank { null })
    }

    fun getFinalContent(): String = fullContent.toString()
    fun getFinalReasoning(): String? = fullReasoning.toString().ifBlank { null }
}
