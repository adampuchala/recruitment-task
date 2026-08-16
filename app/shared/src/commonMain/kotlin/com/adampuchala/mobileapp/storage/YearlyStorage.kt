package com.adampuchala.mobileapp.storage

import kotlinx.coroutines.flow.Flow
import com.adampuchala.mobileapp.Conference
import com.adampuchala.mobileapp.ConferenceInfo
import com.adampuchala.mobileapp.GoldenKodeeData
import com.adampuchala.mobileapp.NotificationSettings
import com.adampuchala.mobileapp.SessionId
import com.adampuchala.mobileapp.VoteInfo

interface YearlyStorage {
    fun isPolicySigned(): Flow<Boolean>
    suspend fun setPolicySigned(value: Boolean)

    fun getConferenceCache(): Flow<Conference?>
    suspend fun setConferenceCache(value: Conference)

    fun getConferenceInfoCache(): Flow<ConferenceInfo?>
    suspend fun setConferenceInfoCache(value: ConferenceInfo)

    fun getGoldenKodeeCache(): Flow<GoldenKodeeData?>
    suspend fun setGoldenKodeeCache(value: GoldenKodeeData)

    fun getFavorites(): Flow<Set<SessionId>>
    suspend fun setFavorites(value: Set<SessionId>)

    fun getNotificationSettings(): Flow<NotificationSettings?>
    suspend fun setNotificationSettings(value: NotificationSettings)

    fun getVotes(): Flow<List<VoteInfo>>
    suspend fun setVotes(value: List<VoteInfo>)

    suspend fun getAsset(name: String): String?
    suspend fun setAsset(name: String, content: String)
}
