package com.example.data.remote

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class TopoTileLoader(context: Context) {
    private val appContext = context.applicationContext
    private val imageLoader = SingletrackImageLoader.get(appContext)

    fun getCachedTile(z: Int, x: Int, y: Int): ImageBitmap? {
        val key = tileKey(z, x, y)
        sharedBitmapCache.get(key)?.let { return it }
        val entry = imageLoader.memoryCache?.get(MemoryCache.Key(key)) ?: return null
        val bitmap = runCatching { entry.image.toBitmap().asImageBitmap() }.getOrNull() ?: return null
        sharedBitmapCache.put(key, bitmap)
        return bitmap
    }

    fun shouldFetch(z: Int, x: Int, y: Int): Boolean {
        val key = tileKey(z, x, y)
        if (sharedBitmapCache.get(key) != null ||
            imageLoader.memoryCache?.get(MemoryCache.Key(key)) != null ||
            sharedInFlight.contains(key)
        ) {
            return false
        }
        val failedAt = sharedFailedTiles[key]
        if (failedAt != null) {
            if (System.currentTimeMillis() - failedAt < FAILED_TILE_COOLDOWN_MS) {
                return false
            }
            sharedFailedTiles.remove(key)
        }
        return true
    }

    suspend fun loadTile(z: Int, x: Int, y: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = tileKey(z, x, y)
        getCachedTile(z, x, y)?.let { return@withContext it }
        if (!sharedInFlight.add(key)) return@withContext null

        try {
            val subdomain = SUBDOMAINS[(x + y).and(Int.MAX_VALUE) % SUBDOMAINS.size]
            val urls = arrayOf(
                "https://$subdomain.tile.opentopomap.org/$z/$x/$y.png",
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/$z/$y/$x"
            )

            for (url in urls) {
                val request = ImageRequest.Builder(appContext)
                    .data(url)
                    .memoryCacheKey(key)
                    .diskCacheKey(key)
                    .size(TILE_SIZE_PX, TILE_SIZE_PX)
                    .precision(Precision.INEXACT)
                    .allowHardware(false)
                    .build()

                val result = try {
                    imageLoader.execute(request)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    null
                }
                if (result is SuccessResult) {
                    val bitmap = runCatching { result.image.toBitmap().asImageBitmap() }.getOrNull()
                    if (bitmap != null) {
                        sharedFailedTiles.remove(key)
                        sharedBitmapCache.put(key, bitmap)
                        return@withContext bitmap
                    }
                }
            }

            sharedFailedTiles[key] = System.currentTimeMillis()
            null
        } finally {
            sharedInFlight.remove(key)
        }
    }

    private fun tileKey(z: Int, x: Int, y: Int): String = "tile_${z}_${x}_${y}"

    companion object {
        private const val TILE_SIZE_PX = 256
        private const val FAILED_TILE_COOLDOWN_MS = 60_000L
        private val SUBDOMAINS = arrayOf("a", "b", "c")
        private val sharedBitmapCache = object : LruCache<String, ImageBitmap>(48) {}
        private val sharedInFlight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        private val sharedFailedTiles = ConcurrentHashMap<String, Long>()
    }
}
