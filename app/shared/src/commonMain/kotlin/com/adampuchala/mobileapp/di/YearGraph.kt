package com.adampuchala.mobileapp.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import com.adampuchala.mobileapp.network.YearlyApi
import com.adampuchala.mobileapp.storage.YearlyStorage

@GraphExtension(YearScope::class)
interface YearGraph {
    val storage: YearlyStorage
    val api: YearlyApi

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides @Year year: Int): YearGraph
    }
}

object YearScope
