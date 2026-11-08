package com.roadmatekorea.android.voice

import com.roadmatekorea.android.rules.Clock
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.Priority
import java.util.Locale

/**
 * 말할 안내 대기열(순수 Kotlin).
 *
 * - 우선순위가 높은 것 먼저, 같으면 먼저 들어온 것 먼저.
 * - 너무 오래 기다린 안내는 버린다. 지나간 회전이나 구역을 늦게 말하면 오히려 헷갈린다.
 * - 같은 안내(ID와 인자가 같음)가 이미 기다리고 있으면 다시 넣지 않는다.
 */
class SpeechQueue(private val clock: Clock) {

    data class Item(
        val guidance: Guidance,
        val text: String,
        val locale: Locale,
        val enqueuedAtMs: Long,
        private val seq: Long,
    ) {
        internal val order get() = seq
    }

    private val items = mutableListOf<Item>()
    private var seq = 0L

    val size: Int get() = items.size

    fun enqueue(guidance: Guidance, text: String, locale: Locale): Boolean {
        if (items.any { it.guidance == guidance }) return false
        items += Item(guidance, text, locale, clock.nowMillis(), seq++)
        return true
    }

    /** 다음에 말할 안내를 꺼낸다. 유효 시간이 지난 것은 이때 버린다. */
    fun poll(): Item? {
        val now = clock.nowMillis()
        items.removeAll { item -> ttlMs(item.guidance.priority)?.let { now - item.enqueuedAtMs > it } ?: false }
        val next = items.minWithOrNull(compareByDescending<Item> { it.guidance.priority }.thenBy { it.order }) ?: return null
        items.remove(next)
        return next
    }

    fun clear() = items.clear()

    companion object {
        /** null이면 버리지 않는다. */
        fun ttlMs(priority: Priority): Long? = when (priority) {
            Priority.SAFETY -> 15_000
            Priority.RULE -> 10_000
            Priority.ADAPTATION -> 20_000
            Priority.INFO -> null
        }
    }
}
