package com.example.data.remote

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object SingletrackImageLoader {
    private const val DISK_CACHE_MAX_BYTES = 48L * 1024L * 1024L

    @Volatile
    private var instance: ImageLoader? = null

    private val imageHttpClient: OkHttpClient by lazy {
        OverpassClient.okHttpClient.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun get(context: Context): ImageLoader {
        return instance ?: synchronized(this) {
            instance ?: buildImageLoader(context.applicationContext).also { instance = it }
        }
    }

    private fun buildImageLoader(appContext: Context): ImageLoader {
        return ImageLoader.Builder(appContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "topo_tiles"))
                    .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .build()
    }
}
