package tice.exceptions

sealed class GroupManagerException : Exception() {
    object LastAdminException : GroupManagerException()
}
