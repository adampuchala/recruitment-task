package com.adampuchala.mobileapp

import com.mmk.kmpnotifier.extensions.onApplicationDidReceiveRemoteNotification
import com.mmk.kmpnotifier.extensions.onNotificationClicked
import com.mmk.kmpnotifier.notification.NotifierManager
import platform.UserNotifications.UNNotificationContent
import platform.UserNotifications.UNNotificationResponse

@Suppress("unused") // Called from Swift
fun handleNotificationResponse(response: UNNotificationResponse) {
    val content: UNNotificationContent = response.notification.request.content
    val notificationId = content.userInfo[IOSLocalNotificationService.LOCAL_NOTIFICATION_ID_KEY] as? String
    if (notificationId != null) {
        // Local notification clicked
        return
    }

    // Process push notifications
    NotifierManager.onNotificationClicked(content)
}

@Suppress("unused") // Called from Swift
fun handleRemoteNotification(userInfo: Map<Any?, *>) {
    NotifierManager.onApplicationDidReceiveRemoteNotification(userInfo)
}
