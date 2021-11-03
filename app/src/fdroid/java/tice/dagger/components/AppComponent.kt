package tice.dagger.components

import dagger.BindsInstance
import dagger.Subcomponent
import tice.AppFlow
import tice.dagger.modules.*
import tice.dagger.modules.android.*
import tice.dagger.provides.ConfigModule
import tice.dagger.provides.DatabaseModule
import tice.dagger.scopes.AppScope
import tice.dagger.setup.ViewModelFactory
import tice.managers.services.LocationService
import tice.workers.BackendSyncWorker
import tice.workers.MembershipRenewalWorker
import tice.workers.MessageKeyCacheWorker

@AppScope
@Subcomponent(
    modules = [
        FragmentBuilderModule::class,
        OSMdroidMapContainerFragmentBuilderModule::class,
        TICEViewModelModule::class,
        ViewModelFactoryModule::class,
        OSMdroidMapContainerViewModelModule::class,

        BackendModule::class,
        ManagerModule::class,
        DatabaseModule::class,
        StorageManagerModule::class,
        MessagingModule::class,
        ConfigModule::class,
        LocationServiceBuilderModule::class,
        TrackerModule::class,
        UtilityModule::class
    ]
)
interface AppComponent {
    @Subcomponent.Factory
    interface Factory {
        fun create(@BindsInstance appFlow: AppFlow): AppComponent
    }

    fun bind(appFlow: AppFlow)

    // bind services
    fun bind(service: LocationService)

    // bind workers
    fun bind(worker: MessageKeyCacheWorker)
    fun bind(worker: BackendSyncWorker)
    fun bind(worker: MembershipRenewalWorker)

    fun getViewModelFactory(): ViewModelFactory
}
