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
        "https://overpass.private.coffee/api/interpreter",
        "https://overpass-api.de/api/interpreter",
        "https://overpass.nchc.org.tw/api/interpreter"
    )

    private const val DRESEL_NA_ENDPOINT = "https://ovp.dresel.systems/api-na/interpreter"
    private const val DRESEL_EU_ENDPOINT = "https://ovp.dresel.systems/api/interpreter"

    fun endpointsForLocation(lat: Double, lon: Double): List<String> = buildList {
        addAll(ENDPOINTS)
        if (lat in 15.0..70.0 && lon in -165.0..-55.0) {
            add(DRESEL_NA_ENDPOINT)
        } else if (lat in 35.0..70.0 && lon in -11.0..40.0) {
            add(DRESEL_EU_ENDPOINT)
        }
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    internal val moshiConverterFactory: MoshiConverterFactory by lazy {
        MoshiConverterFactory.create(moshi)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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
