package tice.dagger.modules

import dagger.Binds
import dagger.Module
import tice.utility.beekeeper.Beekeeper
import tice.utility.beekeeper.BeekeeperType
import tice.utility.provider.*

@Module
abstract class UtilityModule {

    @Binds
    abstract fun bindPseudonymGenerator(userDataGenerator: UserDataGenerator): UserDataGeneratorType

    @Binds
    abstract fun bindNameSupplier(nameProvider: NameProvider): NameProviderType

    @Binds
    abstract fun bindLocalizationProvider(localizationProvider: LocalizationProvider): LocalizationProviderType

    @Binds
    abstract fun bindBeekeeper(beekeeper: Beekeeper): BeekeeperType
}
