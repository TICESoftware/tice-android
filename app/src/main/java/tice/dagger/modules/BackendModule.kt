package tice.dagger.modules

import dagger.Binds
import dagger.Module
import kotlinx.serialization.UnsafeSerializationApi
import tice.backend.Backend
import tice.backend.BackendType
import tice.backend.HTTPRequester
import tice.backend.HTTPRequesterType

@Module
abstract class BackendModule {

    @UnsafeSerializationApi
    @Binds
    abstract fun bindTICEBackend(backend: Backend): BackendType

    @Binds
    abstract fun bindHttpRequest(httpRequester: HTTPRequester): HTTPRequesterType
}
