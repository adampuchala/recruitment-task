package com.adampuchala.mobileapp

import kotlinx.serialization.Serializable

@Serializable
enum class Theme {
    SYSTEM,
    LIGHT,
    DARK,
}

@Serializable
data class NotificationSettings(
    val sessionReminders: Boolean,
    val scheduleUpdates: Boolean,
) {
    fun hasAnyEnabled() = sessionReminders || scheduleUpdates
}
