package tice.models

import java.util.*

typealias Data = ByteArray
typealias IV = ByteArray

// ID aliases
typealias GroupId = UUID

typealias UserId = UUID
typealias MessageId = UUID
typealias ReceiptId = UUID
typealias DeviceId = String
typealias CollapseIdentifier = String
typealias VerificationCode = String

typealias GroupTag = String

typealias Ciphertext = ByteArray
typealias Certificate = String
typealias ConversationFingerprint = String
typealias ConversationId = UUID
typealias Signature = ByteArray

typealias SecretKey = ByteArray
typealias PrivateKey = ByteArray
typealias PublicKey = ByteArray
