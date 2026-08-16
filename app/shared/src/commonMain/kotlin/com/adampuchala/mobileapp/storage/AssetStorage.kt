package com.adampuchala.mobileapp.storage

interface AssetStorage {
    suspend fun read(key: String): String?
    suspend fun write(key: String, content: String)
}
