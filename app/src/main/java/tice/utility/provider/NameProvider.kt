package tice.utility.provider

import android.content.Context
import com.ticeapp.TICE.R
import tice.dagger.scopes.AppScope
import tice.managers.SignedInUserManagerType
import tice.managers.UserManagerType
import tice.models.Team
import tice.models.User
import tice.ui.models.GroupNameData
import java.util.*
import javax.inject.Inject

@AppScope
class NameProvider @Inject constructor(
    private val userDataGenerator: UserDataGeneratorType,
    private val userManager: UserManagerType,
    private val signedInUserManager: SignedInUserManagerType,
    private val context: Context
) : NameProviderType {

    override fun getUserName(user: User): String {
        return user.publicName ?: userDataGenerator.generatePseudonym(user.userId)
    }

    override fun getSignedInUserTeamName(): String {
        return createGroupName(getUserName(signedInUserManager.signedInUser))
    }

    override fun getPseudoTeamName(owner: User): GroupNameData.PseudoName {
        return owner.publicName?.let { GroupNameData.PseudoName(createGroupName(it)) }
            ?: run { GroupNameData.PseudoName(createGroupName(getUserName(owner))) }
    }

    override suspend fun getTeamName(team: Team): GroupNameData {
        return team.name?.let { GroupNameData.TeamName(it) }
            ?: run { getPseudoTeamName(userManager.getOrFetchUser(team.owner)) }
    }

    override fun getShortName(name: String): String {
        val trimmedName = name.trim(Char::isWhitespace)
        val words = trimmedName.split(" ")

        return when (words.count()) {
            1 -> words.first().take(2).takeIf { it.isNotEmpty() } ?: "NA"
            else -> "${words.first().first()}${words.last().first()}"
        }
    }

    private fun createGroupName(name: String): String {
        val localeLanguage = Locale.getDefault().language

        val useApostropheGerman = localeLanguage == "de" && (name.endsWith("s") || name.endsWith("x") || name.endsWith("z"))
        val useApostropheEnglish = localeLanguage == "en" && name.endsWith("s")

        return if (useApostropheEnglish || useApostropheGerman) {
            String.format(context.getString(R.string.teams_owner_name_endsWith_s), name)
        } else {
            String.format(context.getString(R.string.teams_owner_name), name)
        }
    }
}
