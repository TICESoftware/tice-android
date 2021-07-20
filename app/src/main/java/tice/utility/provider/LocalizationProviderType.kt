package tice.utility.provider

import tice.models.LocalizationId

interface LocalizationProviderType {

    fun getString(localizationId: LocalizationId): String
}
