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
import java.util.concurrent.TimeUnit

class TopoTileLoader(context: Context) {
    private val cacheDir = File(context.cacheDir, "topo_tiles").apply { mkdirs() }
    private val memoryCache = LruCache<String, ImageBitmap>(140)
    private val inFlight = Collections.synchronizedSet(mutableSetOf<String>())
    private val failedTiles = Collections.synchronizedSet(mutableSetOf<String>())

    private val tileHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "SingletrackAndroidApp/2.0")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    fun getCachedTile(z: Int, x: Int, y: Int): ImageBitmap? {
        return memoryCache.get(tileKey(z, x, y))
    }

    fun shouldFetch(z: Int, x: Int, y: Int): Boolean {
        val key = tileKey(z, x, y)
        return memoryCache.get(key) == null &&
            !inFlight.contains(key) &&
            !failedTiles.contains(key)
    }

    suspend fun loadTile(z: Int, x: Int, y: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = tileKey(z, x, y)
        memoryCache.get(key)?.let { return@withContext it }
        if (!inFlight.add(key)) return@withContext null

        try {
            val diskFile = File(cacheDir, "$key.png")
            if (diskFile.exists() && diskFile.length() > 100L) {
                val bytes = runCatching { diskFile.readBytes() }.getOrNull()
                if (bytes != null) {
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    if (bmp != null) {
                        memoryCache.put(key, bmp)
                        return@withContext bmp
                    }
                }
            }

            val subdomain = listOf("a", "b", "c")[(x + y).and(Int.MAX_VALUE) % 3]
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
                                    memoryCache.put(key, img)
                                    failedTiles.remove(key)
                                    return@withContext img
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Try fallback tile provider
                }
            }

            failedTiles.add(key)
            null
        } finally {
            inFlight.remove(key)
        }
    }

    private fun tileKey(z: Int, x: Int, y: Int): String = "${z}_${x}_${y}"
}
