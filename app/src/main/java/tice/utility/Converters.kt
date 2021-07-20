package tice.utility

import android.util.Base64
import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import tice.models.JoinMode
import tice.models.Location
import tice.models.PermissionMode
import tice.models.chat.MessageStatus
import tice.models.database.MessageEntity
import tice.utility.serializer.DateSerializer
import java.net.URL
import java.util.*

class Converters {

    @TypeConverter fun uuidFromString(value: String?): UUID? = value?.let { UUID.fromString(value) }
    @TypeConverter fun uuidToString(uuid: UUID?): String? = uuid?.uuidString()

    @TypeConverter fun byteArrayFromString(value: String?): ByteArray? = Base64.decode(value, Base64.DEFAULT)
    @TypeConverter fun byteArrayToString(byteArray: ByteArray?): String? = Base64.encodeToString(byteArray, Base64.DEFAULT)

    @TypeConverter fun joinModeFromString(value: String?): JoinMode? = JoinMode.valueOf(value!!)
    @TypeConverter fun joinModeToString(joinMode: JoinMode?): String? = joinMode.toString()

    @TypeConverter fun permissionModeFromString(value: String?): PermissionMode? = PermissionMode.valueOf(value!!)
    @TypeConverter fun permissionModeToString(permissionMode: PermissionMode?): String? = permissionMode.toString()

    @TypeConverter fun urlFromString(value: String?): URL? = URL(value!!)
    @TypeConverter fun urlToString(url: URL?): String? = url.toString()

    @TypeConverter fun locationFromString(value: String?): Location? = value?.let { Json.safeParse(Location.serializer(), it) }
    @TypeConverter fun locationToString(location: Location?): String? = location?.let { Json.encodeToString(Location.serializer(), it) }

    @TypeConverter fun dateFromString(value: String?): Date? = value?.let { Json.decodeFromString(DateSerializer, it) }
    @TypeConverter fun dateToString(date: Date?): String? = date?.let { Json.encodeToString(DateSerializer, it) }

    @TypeConverter fun rawStatusFromString(value: String?): MessageStatus? = MessageStatus.valueOf(value!!)
    @TypeConverter fun rawStatusToString(messageStatus: MessageStatus?): String? = messageStatus.toString()

    @TypeConverter fun messageTypeFromString(value: String?): MessageEntity.MessageType? = MessageEntity.MessageType.valueOf(value!!)
    @TypeConverter fun messageTypeToString(value: MessageEntity.MessageType?): String? = value.toString()
}
