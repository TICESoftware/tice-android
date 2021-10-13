package tice.dagger.modules.android

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import tice.dagger.setup.ViewModelKey
import tice.ui.viewModels.GoogleMapsContainerViewModel

@Module
abstract class GoogleMapsContainerViewModelModule {
    @Binds
    @IntoMap
    @ViewModelKey(GoogleMapsContainerViewModel::class)
    abstract fun bindGoogleMapContainerViewModel(myViewModel: GoogleMapsContainerViewModel): ViewModel
}
