package com.adampuchala.mobileapp.di

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import com.adampuchala.mobileapp.flags.Flags
import com.adampuchala.mobileapp.storage.ApplicationStorage
import com.mmk.kmpnotifier.notification.configuration.NotificationPlatformConfiguration
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
object AndroidBindings {

    @Provides
    @SingleIn(AppScope::class)
    fun provideApplicationContext(application: Application): Context = application

    @Provides
    @BaseUrl
    @SingleIn(AppScope::class)
    fun provideBaseUrl(
        applicationStorage: ApplicationStorage,
        platformFlags: Flags,
    ): String {
        return "http://10.0.2.2:8080"
//        val flags = applicationStorage.getFlagsBlocking()
//        return when {
//            flags != null && (flags != platformFlags) -> URLs.STAGING_URL
//            else -> URLs.PRODUCTION_URL
//        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideSettings(application: Application): ObservableSettings =
        SharedPreferencesSettings(PreferenceManager.getDefaultSharedPreferences(application))

    @Provides
    @SingleIn(AppScope::class)
    @FileStorageDir
    fun provideFileStorageDir(application: Application): String =
        application.filesDir.resolve("files").absolutePath

    @Provides
    @SingleIn(AppScope::class)
    fun provideNotificationPlatformConfiguration(
        @NotificationIcon iconRes: Int,
    ): NotificationPlatformConfiguration =
        NotificationPlatformConfiguration.Android(
            notificationIconResId = iconRes,
            showPushNotification = true,
        )
}
