package tice.models.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class APIError(val type: ErrorType, val description: String) : Exception() {

    @Serializable
    enum class ErrorType {

        @SerialName("unknown")
        UNKNOWN,

        @SerialName("invalidJson")
        INVALID_JSON,

        @SerialName("missingKey")
        MISSING_KEY,

        @SerialName("invalidValue")
        INVALID_VALUE,

        @SerialName("internalServerError")
        INTERNAL_SERVER_ERROR,

        @SerialName("invalidVerificationCode")
        INVALID_VERIFICATION_CODE,

        @SerialName("notFound")
        NOT_FOUND,

        @SerialName("duplicateGroupId")
        DUPLICATE_GROUP_ID,

        @SerialName("invalidGroupTag")
        INVALID_GROUP_TAG,

        @SerialName("authenticationFailed")
        AUTHENTICATION_FAILED,

        @SerialName("pushFailed")
        PUSH_FAILED,

        @SerialName("notModified")
        NOT_MODIFIED,

        @SerialName("conflicts")
        CONFLICTS,

        @SerialName("groupIsParent")
        GROUP_IS_PARENT,

        @SerialName("clientBuildDeprecated")
        CLIENT_BUILD_DEPRECATED,
    }
}
