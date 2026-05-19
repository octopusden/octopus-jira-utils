package org.octopusden.octopus.jira.config

import com.atlassian.cache.Cache
import com.atlassian.cache.CacheManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory

/**
 * Detects a race between cache-loader calls and `clear()`:
 *  - a loader call starts BEFORE its cache's `clear()` runs,
 *  - the loader's remote call finishes AFTER that `clear()`,
 *  - so the loader silently re-inserts a value that was fetched against the
 *    pre-clean state of the remote service, overwriting the just-cleared entry
 *    with stale data. The cache then serves that stale value until the next clear.
 *
 * Clean timestamps are tracked **per cache**: a loader for cache A is only
 * flagged when cache A itself was cleared during the loader's window. This
 * avoids cross-cache false positives when multiple caches are cleared one by one.
 *
 * Usage:
 *  1. Hold a single [LoaderTracker] instance per service.
 *  2. Wrap every loader lambda with [wrap] (or use [CacheManager.trackedCache]).
 *  3. Immediately after each `cache.removeAll()` call, invoke [markCleaned] with
 *     the same cache name passed to [wrap]. Do this per cache, right after the
 *     clear succeeds — not once at the end of a multi-cache sweep — so loaders
 *     that finish mid-sweep are evaluated against an accurate timestamp.
 *  4. When the race occurs, a `POSSIBLE STALE REINSERT` WARN is emitted naming
 *     the cache and the exact key that is now stale.
 *
 * Overhead: ~100–200 ns per cache miss (two `System.nanoTime()`, one atomic inc/dec,
 * one map read). Zero overhead on cache hits. Constant memory (one map entry per cache).
 */
class LoaderTracker {

    val inFlight: AtomicInteger = AtomicInteger(0)

    private val lastCleanAtNanosByCache: ConcurrentHashMap<String, Long> = ConcurrentHashMap()

    fun markCleaned(cacheName: String) {
        lastCleanAtNanosByCache[cacheName] = System.nanoTime()
    }

    fun lastCleanAtNanos(cacheName: String): Long = lastCleanAtNanosByCache[cacheName] ?: 0L

    /** Wraps a cache-loader lambda with start/finish bookkeeping and race detection. */
    fun <K, V> wrap(cacheName: String, fn: (K) -> V): (K) -> V = { key ->
        val startedAt = System.nanoTime()
        inFlight.incrementAndGet()
        try {
            val value = fn(key)
            val finishedAt = System.nanoTime()
            val cleanAt = lastCleanAtNanos(cacheName)
            // If current cache was cleared strictly between our start and our finish,
            // our about-to-be-cached value may be stale and would overwrite the clean.
            if (cleanAt in (startedAt + 1)..finishedAt) {
                log.warn(
                    "POSSIBLE STALE REINSERT in cache '{}': loader for key='{}' started {}ms before clear() ran, " +
                        "finishing now will overwrite the cleared entry with data fetched from the pre-clean state of the remote service. " +
                        "elapsedMs={}, inFlightNow={}",
                    cacheName,
                    key,
                    (cleanAt - startedAt) / 1_000_000,
                    (finishedAt - startedAt) / 1_000_000,
                    inFlight.get()
                )
            }
            value
        } finally {
            inFlight.decrementAndGet()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LoaderTracker::class.java)
    }
}

/**
 * Convenience: register a tracked cache in a single call. Replaces the verbose
 * `cacheManager.getCache(id.id(), tracker.wrap<K, V>(id.id()) { ... })` pattern with
 * `cacheManager.trackedCache(id, tracker) { key: K -> V }`.
 */
inline fun <reified K : Any, reified V : Any> CacheManager.trackedCache(
    cacheId: CacheId,
    tracker: LoaderTracker,
    noinline loader: (K) -> V
): Cache<K, V> = getCache(cacheId.id(), tracker.wrap(cacheId.id(), loader))

