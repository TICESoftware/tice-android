package tice.utility.provider

import kotlinx.coroutines.Dispatchers
import tice.dagger.scopes.AppScope
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AppScope
class CoroutineContextProvider @Inject constructor() : CoroutineContextProviderType {
    override val Default: CoroutineContext by lazy { Dispatchers.Default }
    override val Main: CoroutineContext by lazy { Dispatchers.Main }
    override val IO: CoroutineContext by lazy { Dispatchers.IO }
}
