package tice.ui.activitys

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ticeapp.TICE.R
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tice.ui.activitys.utility.finishOnboarding
import tice.ui.activitys.utility.swipeToHideNotification
import tice.ui.activitys.utility.waitForView

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class TeamInfoScreens {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun teamInfoScreenTest() {
        finishOnboarding()

        swipeToHideNotification()
        waitForView(allOf(withId(R.id.defaultMenu_GroupInfoMenu), isDisplayed())).perform(click())

        waitForView(withId(R.id.manage_meetup_button)).perform(scrollTo(), click())
        waitForView(withId(android.R.id.button2)).perform(scrollTo(), click())

        onView(isRoot()).perform(pressBack())
    }
}
