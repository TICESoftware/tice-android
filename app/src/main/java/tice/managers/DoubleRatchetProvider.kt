package tice.managers

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair
import com.ticeapp.androiddoubleratchet.DoubleRatchet
import com.ticeapp.androiddoubleratchet.MessageKeyCache
import com.ticeapp.androiddoubleratchet.SessionState
import javax.inject.Inject

class DoubleRatchetProvider @Inject constructor() : DoubleRatchetProviderType {
    @ExperimentalStdlibApi
    override fun provideDoubleRatchet(
        keyPair: KeyPair?,
        remotePublicKey: Key?,
        sharedSecret: ByteArray,
        maxSkip: Int,
        info: String,
        messageKeyCache: MessageKeyCache?,
        sodium: LazySodiumAndroid
    ) = DoubleRatchet(
        keyPair,
        remotePublicKey,
        sharedSecret,
        maxSkip,
        info,
        messageKeyCache,
        sodium
    )

    @ExperimentalStdlibApi
    override fun provideDoubleRatchet(sessionState: SessionState, messageKeyCache: MessageKeyCache?, sodium: LazySodiumAndroid): DoubleRatchet = DoubleRatchet(sessionState, messageKeyCache, sodium)
}
