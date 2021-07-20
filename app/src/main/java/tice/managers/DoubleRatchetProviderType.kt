package tice.managers

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import com.ticeapp.androiddoubleratchet.DoubleRatchet
import com.ticeapp.androiddoubleratchet.MessageKeyCache
import com.ticeapp.androiddoubleratchet.SessionState

// Workaround for mockk bug that prevents DoubleRatchet constructor from being mocked
interface DoubleRatchetProviderType {
    fun provideDoubleRatchet(
        keyPair: KeyPair?,
        remotePublicKey: Key?,
        sharedSecret: ByteArray,
        maxSkip: Int,
        info: String,
        messageKeyCache: MessageKeyCache?,
        sodium: LazySodiumAndroid
    ): DoubleRatchet

    fun provideDoubleRatchet(sessionState: SessionState, messageKeyCache: MessageKeyCache?, sodium: LazySodiumAndroid): DoubleRatchet
}
