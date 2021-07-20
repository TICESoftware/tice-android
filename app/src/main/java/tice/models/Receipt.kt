package tice.models

import java.util.*

data class Receipt(
    val receiptId: ReceiptId,
    val owner: UserId,
    val content: ReceiptContent,

    var groupId: GroupId?,
    var expiresAt: Date?,
    var usableBy: UserId?,
    var usableIn: GroupId?
)

enum class ReceiptContent {
    MEETUPDAY5,
    MEETUPDAY10,
    MEETUPDAY20
}

enum class ReceiptType {
    IOS,
    ANDROID,
    COUPON
}
