package tice

import android.util.Log
import androidx.work.Configuration
import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication
import tice.dagger.components.AppComponent
import tice.dagger.components.DaggerAndroidComponent

class TICEApplication : DaggerApplication(), Configuration.Provider {

    private val androidComponent = DaggerAndroidComponent.factory().create(this)

    lateinit var appComponent: AppComponent

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return androidComponent
    }

    fun initializeNewAppComponent(appFlow: AppFlow): Boolean {
        return if (!::appComponent.isInitialized) {
            appComponent = androidComponent.appComponent().create(appFlow)
            true
        } else false
    }

    override fun getWorkManagerConfiguration() = Configuration.Builder()
        .setMinimumLoggingLevel(Log.DEBUG)
        .build()
}
