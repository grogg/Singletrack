package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenMeteoElevationResponse(
    @Json(name = "elevation") val elevation: List<Double?> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OpenTopoDataResponse(
    @Json(name = "results") val results: List<OpenTopoResult> = emptyList(),
    @Json(name = "status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenTopoResult(
    @Json(name = "elevation") val elevation: Double? = null
)

interface ElevationService {
    @GET
    suspend fun getOpenMeteoElevations(
        @Url url: String = "https://api.open-meteo.com/v1/elevation",
        @Query("latitude") latitudes: String,
        @Query("longitude") longitudes: String
    ): OpenMeteoElevationResponse

    @GET
    suspend fun getOpenTopoDataElevations(
        @Url url: String = "https://api.opentopodata.org/v1/srtm30m",
        @Query("locations") locations: String
    ): OpenTopoDataResponse
}

object ElevationClient {
    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "SingletrackAndroidApp/1.0")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    val service: ElevationService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ElevationService::class.java)
    }
}
