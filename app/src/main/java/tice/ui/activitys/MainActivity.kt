package tice.ui.activitys

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController
import com.ticeapp.TICE.NavigationControllerDirections
import com.ticeapp.TICE.R
import dagger.Module
import dagger.android.AndroidInjection
import kotlinx.coroutines.*
import tice.dagger.setup.ViewModelFactory
import tice.ui.delegates.ActionBarAccess
import tice.ui.viewModels.MigrationViewModel
import javax.inject.Inject

@Module
class MainActivity : AppCompatActivity(), ActionBarAccess {
    override lateinit var actionBar: ActionBar
    private lateinit var navController: NavController

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private lateinit var migrationViewModel: MigrationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setTheme(R.style.AppTheme)
        setContentView(R.layout.activity_main)

        val navHost = supportFragmentManager.findFragmentById(R.id.navigationHostFragment) as NavHostFragment
        navController = navHost.navController
        navController.graph = navController.navInflater.inflate(R.navigation.navigation_controller)

        setupActionBarWithNavController(navController)
        this@MainActivity.actionBar = supportActionBar!!

        migrationViewModel = ViewModelProvider(this, viewModelFactory).get(MigrationViewModel::class.java)

        val uri = intent?.data
        intent?.data = null

        uri?.let {
            val listener = object : NavController.OnDestinationChangedListener {
                override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
                    if (destination.id == R.id.teamListFragment) {
                        handleDeepLinkUri(it)
                        navController.removeOnDestinationChangedListener(this)
                    }
                }
            }
            navController.addOnDestinationChangedListener(listener)
        }

        migrationViewModel.initializeMigration()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> navController.navigateUp()
            R.id.defaultMenu_SettingsMenu -> navController.navigate(R.id.action_global_settings)
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.default_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onNewIntent(newIntent: Intent?) {
        super.onNewIntent(newIntent)
        val uri = newIntent?.data
        newIntent?.data = null

        uri?.let { handleDeepLinkUri(it) }
    }

    private fun handleDeepLinkUri(uri: Uri) {
        val groupIdString = uri.pathSegments[1]
        val groupKeyString = uri.fragment ?: ""
        navController.navigate(NavigationControllerDirections.actionGlobalJoinTeamFragment(groupIdString, groupKeyString))
    }
}
