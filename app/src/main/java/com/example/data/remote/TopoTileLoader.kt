package com.example.data.remote

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class TopoTileLoader(context: Context) {
    private val appContext = context.applicationContext
    private val imageLoader = SingletrackImageLoader.get(appContext)

    fun getCachedTile(z: Int, x: Int, y: Int): ImageBitmap? {
        val entry = imageLoader.memoryCache?.get(MemoryCache.Key(tileKey(z, x, y))) ?: return null
        return runCatching { entry.image.toBitmap().asImageBitmap() }.getOrNull()
    }

    fun shouldFetch(z: Int, x: Int, y: Int): Boolean {
        val key = tileKey(z, x, y)
        if (imageLoader.memoryCache?.get(MemoryCache.Key(key)) != null || sharedInFlight.contains(key)) {
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
            val urls = listOf(
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

                val result = runCatching { imageLoader.execute(request) }.getOrNull()
                if (result is SuccessResult) {
                    val bitmap = runCatching { result.image.toBitmap().asImageBitmap() }.getOrNull()
                    if (bitmap != null) {
                        sharedFailedTiles.remove(key)
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
        private val sharedInFlight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        private val sharedFailedTiles = ConcurrentHashMap<String, Long>()
    }
}
