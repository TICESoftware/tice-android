package tice.utility.provider

import android.content.Context
import tice.dagger.scopes.AppScope
import tice.models.LocalizationId
import javax.inject.Inject

@AppScope
class LocalizationProvider @Inject constructor(private val appContext: Context) : LocalizationProviderType {

    override fun getString(localizationId: LocalizationId): String {
        return appContext.getString(localizationId.value)
    }
}
