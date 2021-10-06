package tice.ui.activitys

import android.Manifest
import android.content.Context
import androidx.test.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import com.ticeapp.TICE.R
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tice.backend.HTTPRequester
import tice.ui.activitys.cnc.CnC
import tice.ui.activitys.utility.childAtPosition
import tice.ui.activitys.utility.getText
import tice.ui.activitys.utility.swipeToHideNotification
import tice.ui.activitys.utility.waitForView
import java.util.*

@LargeTest
@RunWith(AndroidJUnit4ClassRunner::class)
class CreateUserAndJoinGroup {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityScenarioRule(MainActivity::class.java)

    private val cncAPI = CnC(HTTPRequester(OkHttpClient()))

    @Test
    fun createUserAndJoinGroup() {
        val ourName = "User"
        val otherName = "Bot"

        waitForView(allOf(withId(R.id.name), isDisplayed())).perform(replaceText("User"), closeSoftKeyboard())
        waitForView(allOf(withId(R.id.register_button), isDisplayed())).perform(click())

        waitForView(allOf(withId(R.id.create_button), isDisplayed())).perform(click())
        waitForView(allOf(withId(R.id.createTeam_teamName_text), isDisplayed())).perform(replaceText("Test"), closeSoftKeyboard())

        Espresso.closeSoftKeyboard()

        waitForView(allOf(withId(R.id.createTeam_continue_button), isDisplayed())).perform(click())

        waitForView(allOf(withId(R.id.teamInvite_url), isDisplayed()))
        val invitationURL = getText(withId(R.id.teamInvite_url))

        val stringCache = invitationURL?.split("#")!!
        val groupKey = stringCache.last()
        val groupId = UUID.fromString(stringCache.first().split("/").last())

        val response = runBlocking { cncAPI.createUser() }
        runBlocking { cncAPI.changeUserName(response.userId, otherName) }
        runBlocking { cncAPI.joinGroup(response.userId, groupId, groupKey) }

        waitForView(allOf(withId(R.id.teamInvite_Continue_Button), isDisplayed())).perform(click())

        val context: Context = ApplicationProvider.getApplicationContext()
        val command = java.lang.String.format("pm grant %s %s", context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)

        val command2 = java.lang.String.format("pm grant %s %s", context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command2)

        swipeToHideNotification()

        waitForView(allOf(withId(R.id.defaultMenu_GroupInfoMenu), isDisplayed())).perform(click())

        waitForView(allOf(
                withId(R.id.team_recyclerView),
                childAtPosition(withClassName(`is`("androidx.constraintlayout.widget.ConstraintLayout")), 2)
            )
        )

        waitForView(allOf(
                withId(R.id.groupMemberListItem_Title_TextView), withText(ourName),
                childAtPosition(withClassName(`is`("android.widget.LinearLayout")), 0),
                isDisplayed()
            )
        )

        waitForView(allOf(
                withId(R.id.groupMemberListItem_Title_TextView), withText(otherName),
                childAtPosition(withClassName(`is`("android.widget.LinearLayout")), 0),
                isDisplayed()
            )
        )
    }
}
