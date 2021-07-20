package tice.utility.provider

import tice.models.UserId

interface UserDataGeneratorType {

    fun generatePseudonym(userId: UserId): String
    fun generateColor(userId: UserId): Int
}
