package com.adampuchala.mobileapp.android

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import com.adampuchala.mobileapp.flags.Flags
import com.adampuchala.mobileapp.di.NotificationIcon
import com.adampuchala.mobileapp.di.BaseAndroidAppGraph

@DependencyGraph(AppScope::class)
interface MobileAppGraph : BaseAndroidAppGraph {
    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides application: Application,
            @Provides @NotificationIcon iconRes: Int,
            @Provides platformFlags: Flags = Flags(),
        ): MobileAppGraph
    }
}
