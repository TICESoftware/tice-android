package tice.ui.activitys


import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tice.ui.activitys.utility.finishOnboarding


@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class CreateGroupTest {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun createGroupTest() {
        finishOnboarding()
    }
}
