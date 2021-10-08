package tice.ui.activitys.utility

import android.Manifest
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.*
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.uiautomator.UiDevice
import com.ticeapp.TICE.R
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import java.util.concurrent.TimeoutException

// Inspired by https://www.repeato.app/espresso-wait-for-view/
// © NetRabbit e.U. 2020.
fun waitForView(viewMatcher: Matcher<View>, timeout: Long = 5000L): ViewInteraction {
    val temp = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()

        override fun getDescription(): String = "Waiting for view. Matcher: $viewMatcher."

        override fun perform(uiController: UiController, rootView: View) {
            uiController.loopMainThreadUntilIdle()
            val startTime = System.currentTimeMillis()
            val endTime = startTime + timeout

            do {
                for (child in TreeIterables.breadthFirstViewTraversal(rootView)) {
                    if (viewMatcher.matches(child)) {
                        return
                    }
                }

                uiController.loopMainThreadForAtLeast(100)
            } while (System.currentTimeMillis() < endTime)
            throw PerformException.Builder()
                .withCause(TimeoutException())
                .withActionDescription(this.description)
                .withViewDescription(HumanReadables.describe(rootView))
                .build()
        }
    }

    onView(isRoot()).perform(temp)
    return onView(viewMatcher)
}

fun childAtPosition(parentMatcher: Matcher<View>, position: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        override fun describeTo(description: Description) {
            description.appendText("Child at position $position in parent ")
            parentMatcher.describeTo(description)
        }

        public override fun matchesSafely(view: View): Boolean {
            val parent = view.parent
            return parent is ViewGroup && parentMatcher.matches(parent)
                && view == parent.getChildAt(position)
        }
    }
}

fun finishOnboarding() {
    waitForView(allOf(withId(R.id.name), isDisplayed()), 10000L).perform(replaceText("User"), closeSoftKeyboard())
    waitForView(allOf(withId(R.id.register_button), isDisplayed())).perform(click())

    waitForView(allOf(withId(R.id.create_button), isDisplayed())).perform(click())
    waitForView(allOf(withId(R.id.createTeam_teamName_text), isDisplayed())).perform(replaceText("Test"), closeSoftKeyboard())

    Espresso.closeSoftKeyboard()

    waitForView(allOf(withId(R.id.createTeam_continue_button), isDisplayed())).perform(click())
    waitForView(allOf(withId(R.id.teamInvite_Continue_Button), isDisplayed())).perform(click())

    val context: Context = ApplicationProvider.getApplicationContext()
    val command = java.lang.String.format("pm grant %s %s", context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)

    val command2 = java.lang.String.format("pm grant %s %s", context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command2)
}

fun getText(matcher: Matcher<View?>?): String? {
    var holder = ""

    onView(matcher).perform(object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return isAssignableFrom(TextView::class.java)
        }

        override fun getDescription(): String {
            return "getting text from a TextView"
        }

        override fun perform(uiController: UiController?, view: View) {
            val tv = view as TextView
            holder = tv.text.toString()
        }
    })
    return holder
}

fun swipeToHideNotification() {
    val device = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
    val swipeStartHeight = device.displayHeight * 0.08
    UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).swipe(200, swipeStartHeight.toInt(), 200, 0, 5)
}
