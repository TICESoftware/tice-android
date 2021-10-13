package tice.dagger.components

import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjectionModule
import dagger.android.AndroidInjector
import tice.TICEApplication
import tice.dagger.modules.android.*

@Component(
    modules = [
        AndroidInjectionModule::class,

        SubComponentModule::class,

        ActivityBuilderModule::class,
        MigrationFragmentBuilderModule::class,
        MigrationViewModelBuilderModule::class,
        ForceUpdateFragmentBuilderModule::class,
        ForceUpdateViewModelBuilderModule::class,
        ViewModelFactoryModule::class
    ]
)
interface AndroidComponent : AndroidInjector<TICEApplication> {
    @Component.Factory
    interface Factory {
        fun create(@BindsInstance application: TICEApplication): AndroidComponent
    }

    fun appComponent(): AppComponent.Factory
}

@Component(modules = [LocationServiceBuilderModule::class])
interface FireBaseService
