package tice.dagger.modules.android

import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import tice.dagger.setup.ViewModelFactory

@Module
abstract class ViewModelFactoryModule {

    @Binds
    abstract fun bindsRegisterViewModelFactory(viewModelFactory: ViewModelFactory): ViewModelProvider.Factory
}
