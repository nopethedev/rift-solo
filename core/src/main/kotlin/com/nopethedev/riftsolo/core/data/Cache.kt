package com.nopethedev.riftsolo.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.security.MessageDigest

/**
 * Everything fetched from the network is kept here: search results, Lavalink node info, stream URLs, "similar songs",
 * Spotify imports, Spotify → YouTube matches. Free Lavalink nodes rate-limit hard, so the app asks the network only
 * when nothing fresh enough is cached, and falls back to an old copy when the network says no.
 *
 * One small JSON file per entry under files/cache/<namespace>/, plus an in-memory LRU in front of it.
 * Not cacheDir on purpose: Android empties that whenever it likes, and these are worth keeping.
 */
class Cache(root: File, private val clock: () -> Long = System::currentTimeMillis) {
    /**
     * Optional second level shared between users (Rift Server's anonymous cache): asked when nothing fresh is stored
     * here, and handed what this device fetched. Only for [SHARED] namespaces; null or opted out = off.
     */
    interface Remote {
        suspend fun get(ns: String, key: String): Pair<JsonElement, Long>?
        suspend fun put(ns: String, key: String, value: JsonElement, expiresAt: Long)
    }
    @Volatile var remote: Remote? = null
    private val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dir = File(root, "cache").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val memory = object : LinkedHashMap<String, Stored>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Stored>?) = size > MEMORY_ENTRIES
    }
    private val inflight = HashMap<String, CompletableDeferred<Any?>>()
    private val lock = Mutex()

    @Serializable
    private data class Stored(val savedAt: Long, val expiresAt: Long = 0, val value: JsonElement)

    data class Hit<T>(val value: T, val ageMs: Long, val fresh: Boolean)

    private fun file(ns: String, key: String) = File(File(dir, ns), sha1(key) + ".json")

    private fun read(ns: String, key: String): Stored? {
        val id = "$ns/$key"
        synchronized(memory) { memory[id] }?.let { return it }
        val f = file(ns, key)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(Stored.serializer(), f.readText()) }.getOrNull()?.also { synchronized(memory) { memory[id] = it } }
    }

    /** Cached value, fresh if younger than [maxAgeMs] (and not past its own expiry). Null if nothing is stored. */
    suspend fun <T> get(ns: String, key: String, s: KSerializer<T>, maxAgeMs: Long): Hit<T>? = withContext(Dispatchers.IO) {
        val st = read(ns, key) ?: return@withContext null
        val value = runCatching { json.decodeFromJsonElement(s, st.value) }.getOrNull() ?: return@withContext null
        val now = clock()
        val age = now - st.savedAt
        Hit(value, age, age < maxAgeMs && (st.expiresAt == 0L || now < st.expiresAt))
    }

    suspend fun <T> put(ns: String, key: String, s: KSerializer<T>, value: T, expiresAt: Long = 0, share: Boolean = true) = withContext(Dispatchers.IO) {
        val st = Stored(clock(), expiresAt, json.encodeToJsonElement(s, value))
        val r = remote
        if (share && r != null && ns in SHARED) bg.launch { runCatching { r.put(ns, key, st.value, expiresAt) } }
        synchronized(memory) { memory["$ns/$key"] = st }
        runCatching {
            val f = file(ns, key)
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(Stored.serializer(), st))
            tmp.renameTo(f)
        }
        Unit
    }

    /** Fresh local copy, else the shared cache's (stored locally for next time), else null. */
    suspend fun <T> getShared(ns: String, key: String, s: KSerializer<T>, maxAgeMs: Long): T? {
        get(ns, key, s, maxAgeMs)?.takeIf { it.fresh }?.let { return it.value }
        return fromRemote(ns, key, s)
    }

    private suspend fun <T> fromRemote(ns: String, key: String, s: KSerializer<T>): T? {
        val r = remote ?: return null
        if (ns !in SHARED) return null
        val (el, exp) = runCatching { r.get(ns, key) }.getOrNull() ?: return null
        if (exp in 1..clock()) return null
        val v = runCatching { json.decodeFromJsonElement(s, el) }.getOrNull() ?: return null
        put(ns, key, s, v, exp, share = false)
        return v
    }

    suspend fun remove(ns: String, key: String) = withContext(Dispatchers.IO) {
        synchronized(memory) { memory.remove("$ns/$key") }
        file(ns, key).delete()
        Unit
    }

    /**
     * Fresh cache → returned. Otherwise [load] runs (only once at a time per key: two screens asking for the same thing
     * share one request). If it fails and an old copy exists, the old copy is returned instead of the error
     * (up to [staleForMs]).
     */
    suspend fun <T> getOrLoad(
        ns: String, key: String, s: KSerializer<T>, maxAgeMs: Long,
        staleForMs: Long = STALE_MS, force: Boolean = false, expiresAt: (T) -> Long = { 0 }, load: suspend () -> T,
    ): T {
        val hit = get(ns, key, s, maxAgeMs)
        if (hit != null && hit.fresh && !force) return hit.value
        val id = "$ns/$key"
        val (deferred, owner) = lock.withLock {
            inflight[id]?.let { it to false } ?: CompletableDeferred<Any?>().also { inflight[id] = it }.let { it to true }
        }
        @Suppress("UNCHECKED_CAST")
        if (!owner) return deferred.await() as T
        try {
            if (!force) fromRemote(ns, key, s)?.let { deferred.complete(it); return it }
            val v = load()
            put(ns, key, s, v, expiresAt(v))
            deferred.complete(v)
            return v
        } catch (e: Throwable) {
            if (hit != null && hit.ageMs < staleForMs && e !is kotlinx.coroutines.CancellationException) { deferred.complete(hit.value); return hit.value }
            deferred.completeExceptionally(e)
            throw e
        } finally {
            lock.withLock { inflight.remove(id) }
        }
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) { dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(memory) { memory.clear() }
        dir.listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }

    /** Drops entries nobody has refreshed for a long time so the folder doesn't grow forever. */
    suspend fun prune(olderThanMs: Long = PRUNE_MS) = withContext(Dispatchers.IO) {
        val cutoff = clock() - olderThanMs
        dir.walkTopDown().filter { it.isFile && it.lastModified() < cutoff }.forEach { it.delete() }
    }

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val STALE_MS = 30 * DAY
        const val PRUNE_MS = 45 * DAY
        private const val MEMORY_ENTRIES = 300
        /** What may go to the shared cache: public catalogue data, never anything about the user. */
        val SHARED = setOf("search", "related", "spotify", "mirror", "stream", "ytvideos", "lyrics", "artist")

        /** Cache keys: lower-case, single spaces, so "Daft  Punk" and "daft punk" share an entry. */
        fun norm(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")

        private fun sha1(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
