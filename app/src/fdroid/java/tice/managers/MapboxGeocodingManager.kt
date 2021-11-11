package tice.managers

import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import tice.dagger.scopes.AppScope
import tice.models.Coordinates
import tice.utility.getLogger
import java.lang.IndexOutOfBoundsException
import java.util.*
import javax.inject.Inject
import javax.inject.Named

@AppScope
class MapboxGeocodingManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @Named("MAPBOX_ACCESS_TOKEN") private val mapboxAccessToken: String
) : MapboxGeocodingManagerType {
    private val logger by getLogger()

    private val baseURL = "https://api.mapbox.com/"

    override suspend fun reverseGeocoding(coordinates: Coordinates): String? {
        val urlString = Uri.Builder()
            .scheme("https")
            .authority("api.mapbox.com")
            .appendPath("geocoding")
            .appendPath("v5")
            .appendPath("mapbox.places")
            .appendPath("${coordinates.longitude},${coordinates.latitude}.json")
            .appendQueryParameter("language", Locale.getDefault().language)
            .appendQueryParameter("access_token", mapboxAccessToken)
            .build()
            .toString()

        val url = urlString.toHttpUrl()

        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (response.code !in 200 until 300 || response.body == null) {
            logger.error("Reverse geocoding request failed: (${response.code}) ${response.message}")
            return null
        }

        val json = Json.parseToJsonElement(response.body!!.string()) as JsonObject
        return try {
            json
                .jsonObject["features"]!!
                .jsonArray[0]
                .jsonObject["place_name"]
                .toString()
                .removePrefix("\"")
                .removeSuffix("\"")
        } catch (e: Exception) {
            when (e) {
                is IllegalArgumentException, is NullPointerException, is IndexOutOfBoundsException -> {
                    logger.error("Received unexpected response body from reverse geocoding request.")
                    null
                }
                else -> throw e
            }
        }
    }
}