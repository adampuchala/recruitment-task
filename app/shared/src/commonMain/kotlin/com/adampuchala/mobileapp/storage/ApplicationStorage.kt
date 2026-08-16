package com.adampuchala.mobileapp.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import com.adampuchala.mobileapp.AppConfig
import com.adampuchala.mobileapp.flags.Flags
import com.adampuchala.mobileapp.Theme

interface ApplicationStorage {
    val userId: StateFlow<String>

    fun isOnboardingComplete(): Flow<Boolean>
    suspend fun setOnboardingComplete(value: Boolean)

    fun getTheme(): Flow<Theme>
    suspend fun setTheme(value: Theme)

    fun getFlagsBlocking(): Flags?
    fun getFlags(): Flow<Flags?>
    suspend fun setFlags(value: Flags)

    fun getConfig(): Flow<AppConfig?>
    suspend fun setConfig(config: AppConfig)

    fun initialize()
}
