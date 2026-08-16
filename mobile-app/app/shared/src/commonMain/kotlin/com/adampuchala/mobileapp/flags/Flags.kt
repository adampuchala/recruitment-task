package com.adampuchala.mobileapp.flags

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import androidx.compose.runtime.staticCompositionLocalOf
import com.adampuchala.mobileapp.GoldenKodeeData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Serializable
data class Flags(
    val debugLogging: Boolean = false,
    val useFakeTime: Boolean = false,
    val useFakeGoldenKodeeData: Boolean = false,
    val enableBackOnTopLevelScreens: Boolean = true,
    val rippleEnabled: Boolean = true,
    val hideKeyboardOnDrag: Boolean = false,
)

@Inject
@SingleIn(AppScope::class)
class FlagsManager {
    val flags: StateFlow<Flags> = MutableStateFlow(Flags())
    suspend fun initAndGetFlags(): Flags = flags.value
}

val LocalFlags = staticCompositionLocalOf { Flags() }

val FakeGoldenKodeeData: GoldenKodeeData? = null
