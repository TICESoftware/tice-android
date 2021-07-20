package tice.exceptions

sealed class MembershipRenewalException : Exception() {
    object GroupNotFoundException : MembershipRenewalException()
}
