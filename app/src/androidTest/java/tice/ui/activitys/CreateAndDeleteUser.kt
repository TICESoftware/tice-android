package tice.ui.activitys

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.ticeapp.TICE.R
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tice.ui.activitys.utility.childAtPosition
import tice.ui.activitys.utility.waitForView

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class CreateAndDeleteUser {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun createAndDeleteUserTest() {
        waitForView(allOf(withId(R.id.name), isDisplayed())).perform(replaceText("User"), closeSoftKeyboard())
        waitForView(allOf(withId(R.id.register_button), isDisplayed())).perform(click())

        waitForView(allOf(withContentDescription("More options"), isDisplayed())).perform(click())
        waitForView(allOf(withId(R.id.title), isDisplayed())).perform(click())
        waitForView(allOf(withId(R.id.recycler_view), childAtPosition(withId(android.R.id.list_container), 0))).perform(
            actionOnItemAtPosition<RecyclerView.ViewHolder>(6, click())
        )
    }
}
