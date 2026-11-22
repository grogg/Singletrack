package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface OverpassService {
    @FormUrlEncoded
    @POST
    suspend fun query(
        @Url url: String,
        @Field("data") query: String
    ): OverpassResponse
}

object OverpassClient {
    val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    internal val moshiConverterFactory: MoshiConverterFactory by lazy {
        MoshiConverterFactory.create(moshi)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "SingletrackMTB/${BuildConfig.VERSION_NAME}")
                    .build()
                chain.proceed(request)
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    }
                    addInterceptor(logging)
                }
            }
            .build()
    }

    val service: OverpassService by lazy {
        Retrofit.Builder()
            .baseUrl("https://overpass-api.de/")
            .client(okHttpClient)
            .addConverterFactory(moshiConverterFactory)
            .build()
            .create(OverpassService::class.java)
    }
}
