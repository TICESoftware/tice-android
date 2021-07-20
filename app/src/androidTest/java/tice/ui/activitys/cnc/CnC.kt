package tice.ui.activitys.cnc

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.UnsafeSerializationApi
import okhttp3.Headers
import tice.backend.HTTPRequesterType
import tice.backend.HTTPRequesterType.HTTPMethod
import tice.backend.request
import tice.models.GroupId
import tice.models.UserId
import tice.models.responses.CreateUserResponse
import tice.ui.activitys.cnc.requests.CnCChangePublicNameRequest
import tice.ui.activitys.cnc.requests.CnCJoinGroupRequest

@OptIn(UnsafeSerializationApi::class)
class CnC(private val httpRequester: HTTPRequesterType) : CnCAPI {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val cncBaseURL = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData.getString("cnc_base_url")!!

    override suspend fun createUser(): CreateUserResponse {
        return httpRequester.request(
            "$cncBaseURL/user",
            HTTPMethod.POST,
            Headers.Builder().build()
        )
    }

    override suspend fun joinGroup(userId: UserId, groupId: GroupId, groupKey: String) {
        val path = "$cncBaseURL/user/$userId/joinGroup"
        val body = CnCJoinGroupRequest(groupId, groupKey)

        return httpRequester.request(
            path,
            HTTPMethod.POST,
            Headers.Builder().build(),
            body
        )
    }

    override suspend fun changeUserName(userId: UserId, name: String) {
        val path = "$cncBaseURL/user/$userId/changeName"
        val body = CnCChangePublicNameRequest(name)

        return httpRequester.request(
            path,
            HTTPMethod.POST,
            Headers.Builder().build(),
            body
        )
    }
}