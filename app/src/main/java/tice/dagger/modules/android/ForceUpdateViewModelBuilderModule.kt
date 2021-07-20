package tice.dagger.modules.android

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import tice.dagger.setup.ViewModelKey
import tice.ui.viewModels.ForceUpdateViewModel

@Module
abstract class ForceUpdateViewModelBuilderModule {
    @Binds
    @IntoMap
    @ViewModelKey(ForceUpdateViewModel::class)
    abstract fun bindForceUpdateViewModel(myViewModel: ForceUpdateViewModel): ViewModel
}
