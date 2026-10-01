package com.khaleel.newleaf.nutrition

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** Process bounds only; quota state contains no credentials or food descriptions. */
internal class UsdaDirectPolicy(private val clockMillis: () -> Long = { System.nanoTime() / 1000000L }) {
    private val slots = Semaphore(4)
    private val lock = Mutex()
    private var hourStart: Long? = null
    private var hourCalls = 0
    private var minuteStart: Long? = null
    private var minuteActions = 0
    private var blockedUntil: Long? = null

    suspend fun action() = lock.withLock {
        val time = clockMillis()
        if (minuteStart == null || time - minuteStart!! >= 60000L) { minuteStart = time; minuteActions = 0 }
        if (minuteActions >= 30) throw NutritionException(NutritionError.RATE_LIMITED, 60)
        minuteActions++
    }

    suspend fun <T> call(block: suspend () -> T): T = slots.withPermit {
        currentCoroutineContext().ensureActive()
        lock.withLock {
            val time = clockMillis()
            blockedUntil?.let { until ->
                if (time < until) throw NutritionException(NutritionError.RATE_LIMITED, ((until - time) / 1000L + 1).coerceAtMost(86400L))
            }
            if (hourStart == null || time - hourStart!! >= 3600000L) { hourStart = time; hourCalls = 0 }
            if (hourCalls >= 100) throw NutritionException(NutritionError.BUDGET_EXCEEDED,
                ((3600000L - (time - hourStart!!)) / 1000L + 1).coerceAtLeast(1))
            hourCalls++  // never refund unknown outcomes; no retries
        }
        try { block() } catch (error: NutritionException) {
            if (error.category == NutritionError.RATE_LIMITED) lock.withLock {
                val until = clockMillis() + (error.retryAfterSeconds ?: 60).coerceIn(1, 86400) * 1000L
                blockedUntil = maxOf(blockedUntil ?: until, until)
            }
            throw error
        }
    }
}

/** All clients on this device share one process policy, regardless of key/client recreation. */
internal val UsdaDirectLimits = UsdaDirectPolicy()
