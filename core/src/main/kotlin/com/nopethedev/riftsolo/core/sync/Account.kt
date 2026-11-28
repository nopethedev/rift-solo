package com.nopethedev.riftsolo.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Account(
    val serverUrl: String = RiftServer.DEFAULT_URL,
    val token: String? = null,
    val email: String? = null,
    val name: String? = null,
    /** Server version of the library this device last saw (for the server's conflict check). */
    val libraryVersion: Int = 0,
    val lastSyncAt: Long = 0,
    val lastError: String? = null,
    /**
     * Share search results / stream links with the server's anonymous cache, and use what others shared. On by default;
     * the user can turn it off any time (Profile → Account). Nothing in it is tied to an account.
     */
    val shareCache: Boolean = true,
) {
    val signedIn get() = token != null
    val hasServer get() = serverUrl.isNotBlank()
}

/** The account in a small JSON file next to the library. */
class AccountStore(dir: File) {
    private val file = File(dir, "account.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _account = MutableStateFlow(runCatching { json.decodeFromString(Account.serializer(), file.readText()) }.getOrDefault(Account()))
    val account: StateFlow<Account> = _account.asStateFlow()

    fun update(block: (Account) -> Account) {
        _account.update(block)
        runCatching {
            val tmp = File(file.parentFile, "account.json.tmp")
            tmp.writeText(json.encodeToString(Account.serializer(), _account.value))
            tmp.renameTo(file)
        }
    }
}
