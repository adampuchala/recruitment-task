package com.adampuchala.mobileapp.di

import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metrox.android.MetroAppComponentProviders

interface BaseAndroidAppGraph : AppGraph, MetroAppComponentProviders

@Qualifier
annotation class NotificationIcon
