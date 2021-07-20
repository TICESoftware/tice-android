package tice.dagger.modules.android

import androidx.lifecycle.ViewModel
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import tice.dagger.setup.ViewModelKey
import tice.ui.viewModels.MigrationViewModel

@Module
abstract class MigrationViewModelBuilderModule {

    @Binds
    @IntoMap
    @ViewModelKey(MigrationViewModel::class)
    abstract fun bindMigrationViewModel(myViewModel: MigrationViewModel): ViewModel
}
