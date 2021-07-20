package tice.ui.activitys

import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.ticeapp.TICE.R
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tice.ui.activitys.utility.finishOnboarding
import tice.ui.activitys.utility.waitForView

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class StartSharingTest {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun startSharingAndCreateMeetingPoint() {
        finishOnboarding()

        waitForView(allOf(withId(R.id.meetupButton), isDisplayed())).perform(click())
        waitForView(withId(android.R.id.button1)).perform(scrollTo(), click())
        waitForView(allOf(withContentDescription("Google Map"), isDisplayed())).perform(longClick())
        waitForView(allOf(withId(R.id.bottom), isDisplayed()))
        waitForView(allOf(withId(R.id.bs_button), isDisplayed())).perform(click())
    }
}