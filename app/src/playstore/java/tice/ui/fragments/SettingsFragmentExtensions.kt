package tice.ui.fragments

import android.content.Intent
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity

fun SettingsFragment.handleShowLicensesTap() {
    startActivity(Intent(context, OssLicensesMenuActivity::class.java))
}
