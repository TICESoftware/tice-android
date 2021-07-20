package tice.dagger.modules

import dagger.Binds
import dagger.Module
import tice.managers.messaging.*

@Module
abstract class MessagingModule {

    @Binds
    abstract fun bindPostOffice(postOffice: PostOffice): PostOfficeType

    @Binds
    abstract fun bindMailbox(mailbox: Mailbox): MailboxType

    @Binds
    abstract fun bindWebSocketReceiver(webSocketReceiver: WebSocketReceiver): WebSocketReceiverType
}
