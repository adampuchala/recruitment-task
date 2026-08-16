package com.adampuchala.mobileapp.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import com.adampuchala.mobileapp.flags.Flags

@DependencyGraph(AppScope::class)
interface IosAppGraph : AppGraph {

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides platformFlags: Flags,
        ): IosAppGraph
    }
}
