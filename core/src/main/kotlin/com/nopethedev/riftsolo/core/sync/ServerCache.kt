package com.nopethedev.riftsolo.core.sync

import com.nopethedev.riftsolo.core.data.Cache
import kotlinx.serialization.json.JsonElement

/** [Cache.Remote] backed by Rift Server's shared cache, respecting the user's opt-out. */
class ServerCache(private val server: RiftServer, private val accounts: AccountStore) : Cache.Remote {
    private fun on() = accounts.account.value.let { it.shareCache && it.hasServer }

    override suspend fun get(ns: String, key: String): Pair<JsonElement, Long>? =
        if (!on()) null else server.cacheGet(ns, key)?.let { it.value to it.expiresAt }

    override suspend fun put(ns: String, key: String, value: JsonElement, expiresAt: Long) {
        if (on()) server.cachePut(ns, key, value, expiresAt)
    }
}
