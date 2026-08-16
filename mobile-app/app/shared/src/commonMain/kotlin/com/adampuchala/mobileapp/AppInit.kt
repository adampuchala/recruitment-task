package com.adampuchala.mobileapp

import com.adampuchala.mobileapp.di.AppGraph
import com.adampuchala.mobileapp.utils.Logger

fun initApp(
    appGraph: AppGraph,
    platformLogger: Logger,
) {
    platformLogger.log("AppInit") { "Minimal KotlinConf app initialized" }
}
