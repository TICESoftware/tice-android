package tice.ui.models

sealed class GroupNameData {
    abstract val name: String

    data class TeamName(override val name: String) : GroupNameData()
    data class PseudoName(override val name: String) : GroupNameData()
}
