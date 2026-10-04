package com.hebo.codex.domain

import java.util.concurrent.ConcurrentHashMap

internal class ConversationLeaseRegistry<K> {
    private val leaseCounts = ConcurrentHashMap<K, Int>()

    fun acquire(key: K) {
        leaseCounts.compute(key) { _, count -> (count ?: 0) + 1 }
    }

    fun release(key: K) {
        leaseCounts.computeIfPresent(key) { _, count -> (count - 1).takeIf { it > 0 } }
    }

    fun isHeld(key: K): Boolean = leaseCounts.containsKey(key)
}

internal val ConversationState.requiresRuntimeRetention: Boolean
    get() = !isExternalWriter && (isRunning ||
        activeTurnId != null ||
        pendingRequests.isNotEmpty() ||
        backgroundTerminals.isNotEmpty())

internal fun ConversationState.withActiveTurnPlan(plan: ActiveTurnPlan): ConversationState =
    if (plan.turnId == activeTurnId) copy(plan = plan) else this
