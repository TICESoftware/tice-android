package tice.managers

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ticeapp.TICE.R
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.models.LocalizationId
import tice.ui.delegates.AppStatusProvider
import tice.utility.provider.LocalizationProviderType

internal class PopupNotificationManagerTest {

    private lateinit var popupNotificationManager: PopupNotificationManager

    private val mockContext: Context = mockk(relaxUnitFun = true)
    private val mockNotificationManager: NotificationManagerCompat = mockk(relaxUnitFun = true)
    private val mockLocalizationProvider: LocalizationProviderType = mockk(relaxUnitFun = true)
    private val mockAppStatusProvider: AppStatusProvider = mockk(relaxUnitFun = true)

    private val TEST_CHANNEL_NAME = "TestChannelName"

    private val TEST_TEXT = "Text"
    private val TEST_TITLE = "Title"
    private val EXPECTED_CHANNEL_NAME = "TICE_CHANNEL_ID"

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockLocalizationProvider.getString(LocalizationId(R.string.notification_channel_name)) }
            .returns(TEST_CHANNEL_NAME)

        popupNotificationManager = PopupNotificationManager(
            mockContext,
            mockNotificationManager,
            mockLocalizationProvider,
            mockAppStatusProvider
        )
    }

    @Nested
    inner class ShowPopUpNotification {

        @Test
        fun `is in foreground`() {
            every { mockAppStatusProvider.status } returns AppStatusProvider.Status.FOREGROUND

            popupNotificationManager.showPopUpNotification(TEST_TITLE, TEST_TEXT)

            confirmVerified(mockNotificationManager)
        }

        @Test
        fun `is in background`() {
            mockkStatic(NotificationCompat.Builder::class)
            mockkConstructor(NotificationCompat.Builder::class)

            val mockNotification = mockk<Notification>(relaxed = true)

            every { mockAppStatusProvider.status } returns AppStatusProvider.Status.BACKGROUND
            every { anyConstructed<NotificationCompat.Builder>().build() } returns mockNotification

            popupNotificationManager.showPopUpNotification(TEST_TITLE, TEST_TEXT)

            verify(exactly = 1) { anyConstructed<NotificationCompat.Builder>().setContentTitle(TEST_TITLE) }
            verify(exactly = 1) { anyConstructed<NotificationCompat.Builder>().setContentText(TEST_TEXT) }
            verify(exactly = 1) { anyConstructed<NotificationCompat.Builder>().setSmallIcon(R.drawable.ic_logo_tice_small) }
            verify(exactly = 1) { anyConstructed<NotificationCompat.Builder>().setAutoCancel(true) }
            verify(exactly = 1) { mockNotificationManager.notify(EXPECTED_CHANNEL_NAME, any(), mockNotification) }
            confirmVerified(mockNotificationManager)
        }
    }
}
