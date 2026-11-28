package com.nopethedev.riftsolo.core.sync

import com.nopethedev.riftsolo.core.data.LibraryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the library in step with Rift Server: on app start, after signing in, and when the user asks.
 * Read the server copy → merge it into the local one (item by item, see [LibraryMerge]) → write the result back.
 * If another device wrote in between, the server refuses and we go round again.
 */
class SyncManager(
    private val accounts: AccountStore,
    private val library: LibraryStore,
    private val server: RiftServer = RiftServer({ accounts.account.value.serverUrl }),
    private val device: String = "Rift Solo",
) {
    sealed interface State {
        data object Idle : State
        data object Syncing : State
        data class Done(val at: Long) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val lock = Mutex()
    val account get() = accounts.account

    suspend fun signUp(email: String, password: String, name: String) = enter { server.signUp(email.trim(), password, name.trim(), device) }
    suspend fun signIn(email: String, password: String) = enter { server.signIn(email.trim(), password, device) }

    private suspend fun enter(block: suspend () -> RiftServer.Session) {
        val s = block()
        accounts.update { it.copy(token = s.token, email = s.user.email, name = s.user.name, libraryVersion = 0, lastError = null) }
        sync()
    }

    suspend fun signOut() {
        val token = accounts.account.value.token
        if (token != null) runCatching { server.signOut(token) }
        accounts.update { it.copy(token = null, email = null, name = null, libraryVersion = 0, lastSyncAt = 0, lastError = null) }
        _state.value = State.Idle
    }

    /** Deletes the account and everything synced with it on the server. Local data stays. */
    suspend fun deleteAccount() {
        val token = accounts.account.value.token ?: return
        server.deleteAccount(token)
        accounts.update { it.copy(token = null, email = null, name = null, libraryVersion = 0, lastSyncAt = 0) }
        _state.value = State.Idle
    }

    fun setServer(url: String) = accounts.update { if (it.serverUrl == url.trim()) it else it.copy(serverUrl = url.trim(), token = null, email = null, name = null, libraryVersion = 0) }
    fun setShareCache(on: Boolean) = accounts.update { it.copy(shareCache = on) }

    /** One round of sync. Returns false (and records why) when it couldn't. Safe to call any time; calls don't overlap. */
    suspend fun sync(): Boolean = lock.withLock {
        val acc = accounts.account.value
        val token = acc.token ?: return@withLock false
        _state.value = State.Syncing
        try {
            repeat(4) {
                val (version, remote) = server.getLibrary(token)
                if (remote != null) library.mergeIn(remote)
                val merged = library.library.value
                if (remote != null && LibraryMerge.same(merged, remote)) return@withLock done(version)
                val written = server.putLibrary(token, version, merged)
                if (written != null) return@withLock done(written)
                // Another device synced in between: read again and merge its copy too.
            }
            throw ServerException("The library kept changing on another device; try again")
        } catch (e: CancellationException) { throw e } catch (e: ServerException) {
            if (e.status == 401 || e.status == 403) accounts.update { it.copy(token = null, lastError = e.message) }
            else accounts.update { it.copy(lastError = e.message) }
            _state.value = State.Failed(e.message ?: "Sync failed")
            false
        }
    }

    private fun done(version: Int): Boolean {
        val now = System.currentTimeMillis()
        accounts.update { it.copy(libraryVersion = version, lastSyncAt = now, lastError = null) }
        _state.value = State.Done(now)
        return true
    }
}
