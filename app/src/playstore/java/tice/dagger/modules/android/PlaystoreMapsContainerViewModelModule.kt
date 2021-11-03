package tice.dagger.modules.android

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import tice.dagger.setup.ViewModelKey
import tice.ui.viewModels.GoogleMapsContainerViewModel
import tice.ui.viewModels.MapboxContainerViewModel

@Module
abstract class PlaystoreMapsContainerViewModelModule {
    @Binds
    @IntoMap
    @ViewModelKey(GoogleMapsContainerViewModel::class)
    abstract fun bindGoogleMapContainerViewModel(myViewModel: GoogleMapsContainerViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(MapboxContainerViewModel::class)
    abstract fun bindMapboxContainerViewModel(myViewModel: MapboxContainerViewModel): ViewModel
}
