package tice.utility.provider

import tice.models.Team
import tice.models.User
import tice.ui.models.GroupNameData

interface NameProviderType {

    fun getUserName(user: User): String
    fun getSignedInUserTeamName(): String
    fun getPseudoTeamName(owner: User): GroupNameData.PseudoName
    suspend fun getTeamName(team: Team): GroupNameData
    fun getShortName(name: String): String
}
