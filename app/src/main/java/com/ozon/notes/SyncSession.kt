package com.ozon.notes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** Non-suspending ownership and state transitions shared by every sync entry point. */
internal class SyncSession {
    val mutex = Mutex()
    private val syncing = MutableStateFlow(false)
    val isSyncing = syncing.asStateFlow()

    fun tryStart(): Boolean {
        if (!mutex.tryLock()) return false
        syncing.value = true
        return true
    }

    fun finish() {
        try {
            syncing.value = false
        } finally {
            mutex.unlock()
        }
    }
}
