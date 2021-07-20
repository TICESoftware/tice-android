package tice.ui.activitys

import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.ticeapp.TICE.R
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tice.ui.activitys.utility.childAtPosition
import tice.ui.activitys.utility.finishOnboarding
import tice.ui.activitys.utility.waitForView

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class ChatTest {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun chatTest() {
        finishOnboarding()

        waitForView(allOf(withId(R.id.chat_button), isDisplayed())).perform(click())

        waitForView(allOf(withId(R.id.chatTextField), isDisplayed())).perform(replaceText("hallo"), closeSoftKeyboard())

        waitForView(allOf(withId(R.id.text_input_end_icon), isDisplayed())).perform(click())

        waitForView(allOf(withId(R.id.chatTextField), isDisplayed())).perform(replaceText("message"), closeSoftKeyboard())

        waitForView(allOf(withId(R.id.text_input_end_icon), isDisplayed())).perform(click())
        
        Espresso.pressBack()

        waitForView(allOf(withId(R.id.chat_button), isDisplayed())).perform(click())
    }
}
