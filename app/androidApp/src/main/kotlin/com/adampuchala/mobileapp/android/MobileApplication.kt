package com.adampuchala.mobileapp.android

import android.app.Application
import com.adampuchala.mobileapp.R
import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.android.MetroAppComponentProviders
import dev.zacsweers.metrox.android.MetroApplication
import com.adampuchala.mobileapp.initApp
import com.adampuchala.mobileapp.utils.AndroidLogger

class MobileApplication : Application(), MetroApplication {

    private val appGraph: MobileAppGraph by lazy {
        createGraphFactory<MobileAppGraph.Factory>().create(
            application = this,
            iconRes = R.drawable.kotlinconf_notification_icon,
        )
    }

    override val appComponentProviders: MetroAppComponentProviders
        get() = appGraph

    override fun onCreate() {
        super.onCreate()

        initApp(
            appGraph = appGraph,
            platformLogger = AndroidLogger(),
        )
    }
}
