package tice.managers

import tice.ui.delegates.AppStatusProvider
import java.lang.ref.WeakReference

interface PopupNotificationManagerType {
    var delegate: WeakReference<AppStatusProvider>?

    fun showPopUpNotification(title: String, text: String)
}
