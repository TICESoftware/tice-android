package tice.dagger.modules.android

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import tice.dagger.setup.ViewModelKey
import tice.ui.viewModels.OSMdroidContainerViewModel

@Module
abstract class OSMdroidMapContainerViewModelModule {
    @Binds
    @IntoMap
    @ViewModelKey(OSMdroidContainerViewModel::class)
    abstract fun bindOSMdroidMapContainerViewModel(myViewModel: OSMdroidContainerViewModel): ViewModel
}
