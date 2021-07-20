package tice.utility

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

inline fun <reified T> Json.Default.safeParse(serializer: KSerializer<T>, jsonString: String): T {
    val jsonObject = parseToJsonElement(jsonString)
    val jsonBuilder = Json { ignoreUnknownKeys = true }
    return jsonBuilder.decodeFromJsonElement(serializer, jsonObject)
}
