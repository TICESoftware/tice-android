package tice.models

interface UserType {
    val userId: UserId
    var publicSigningKey: PublicKey
    var publicName: String?
}
