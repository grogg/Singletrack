package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class NominatimPlaceDto(
    @Json(name = "display_name") val displayName: String,
    @Json(name = "lat") val lat: String,
    @Json(name = "lon") val lon: String,
    @Json(name = "address") val address: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class NominatimReverseDto(
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "address") val address: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class PhotonResponse(
    @Json(name = "features") val features: List<PhotonFeature> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PhotonFeature(
    @Json(name = "geometry") val geometry: PhotonGeometry? = null,
    @Json(name = "properties") val properties: PhotonProperties? = null
)

@JsonClass(generateAdapter = true)
data class PhotonGeometry(
    @Json(name = "coordinates") val coordinates: List<Double> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PhotonProperties(
    @Json(name = "name") val name: String? = null,
    @Json(name = "city") val city: String? = null,
    @Json(name = "county") val county: String? = null,
    @Json(name = "state") val state: String? = null,
    @Json(name = "country") val country: String? = null
)

interface NominatimService {
    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 8
    ): List<NominatimPlaceDto>

    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("format") format: String = "json",
        @Query("zoom") zoom: Int = 10
    ): NominatimReverseDto
}

interface PhotonService {
    @GET("api")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("limit") limit: Int = 8,
        @Query("lang") lang: String = "en"
    ): PhotonResponse
}

private val geocodingOkHttpClient: OkHttpClient by lazy {
    OverpassClient.okHttpClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
}

object NominatimClient {
    val service: NominatimService by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(geocodingOkHttpClient)
            .addConverterFactory(OverpassClient.moshiConverterFactory)
            .build()
            .create(NominatimService::class.java)
    }
}

object PhotonClient {
    val service: PhotonService by lazy {
        Retrofit.Builder()
            .baseUrl("https://photon.komoot.io/")
            .client(geocodingOkHttpClient)
            .addConverterFactory(OverpassClient.moshiConverterFactory)
            .build()
            .create(PhotonService::class.java)
    }
}
