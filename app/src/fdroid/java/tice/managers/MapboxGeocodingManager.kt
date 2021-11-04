package tice.managers

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

    override suspend fun reverseGeocoding(coordinates: Coordinates): String {
        val urlString = baseURL +
                "geocoding/v5/mapbox.places/" +
                coordinates.longitude + "," +
                coordinates.latitude +
                ".json" +
                "?language=" + Locale.getDefault().language +
                "&access_token=" + mapboxAccessToken
        val url = urlString.toHttpUrl()

        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (response.code !in 200..300 || response.body == null) {
            logger.error("Reverse geocoding request failed: (${response.code}) ${response.message}")
            return "${coordinates.latitude}, ${coordinates.longitude}"
        }

        val json = Json.parseToJsonElement(response.body!!.string()) as JsonObject
        return json
            .jsonObject["features"]!!
            .jsonArray[0]
            .jsonObject["place_name"]
            .toString()
            .removePrefix("\"")
            .removeSuffix("\"")
    }
}