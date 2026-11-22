package com.example.data.remote

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TopoTileLoader(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "topo_tiles").apply { mkdirs() }

    private val tileHttpClient: OkHttpClient by lazy {
        OverpassClient.okHttpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun getCachedTile(z: Int, x: Int, y: Int): ImageBitmap? {
        return sharedMemoryCache.get(tileKey(z, x, y))
    }

    fun shouldFetch(z: Int, x: Int, y: Int): Boolean {
        val key = tileKey(z, x, y)
        if (sharedMemoryCache.get(key) != null || sharedInFlight.contains(key)) {
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
        sharedMemoryCache.get(key)?.let { return@withContext it }
        if (!sharedInFlight.add(key)) return@withContext null

        try {
            val diskFile = File(cacheDir, "$key.png")
            if (diskFile.exists() && diskFile.length() > 100L) {
                val decoded = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
                if (decoded != null) {
                    val bmp = decoded.asImageBitmap()
                    sharedMemoryCache.put(key, bmp)
                    sharedFailedTiles.remove(key)
                    return@withContext bmp
                }
            }

            val subdomain = SUBDOMAINS[(x + y).and(Int.MAX_VALUE) % SUBDOMAINS.size]
            val urls = listOf(
                "https://$subdomain.tile.opentopomap.org/$z/$x/$y.png",
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/$z/$y/$x"
            )

            for (url in urls) {
                try {
                    val request = Request.Builder().url(url).build()
                    tileHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bytes = response.body?.bytes()
                            if (bytes != null && bytes.size > 100) {
                                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (decoded != null) {
                                    runCatching { diskFile.writeBytes(bytes) }
                                    val img = decoded.asImageBitmap()
                                    sharedMemoryCache.put(key, img)
                                    sharedFailedTiles.remove(key)
                                    return@withContext img
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Try fallback tile provider
                }
            }

            sharedFailedTiles[key] = System.currentTimeMillis()
            null
        } finally {
            sharedInFlight.remove(key)
        }
    }

    private fun tileKey(z: Int, x: Int, y: Int): String = "${z}_${x}_${y}"

    companion object {
        private const val FAILED_TILE_COOLDOWN_MS = 60_000L
        private val SUBDOMAINS = arrayOf("a", "b", "c")
        private val sharedMemoryCache = LruCache<String, ImageBitmap>(180)
        private val sharedInFlight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
        private val sharedFailedTiles = ConcurrentHashMap<String, Long>()
    }
}
