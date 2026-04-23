package com.ghostapps.localpulse_example.sync

import java.util.concurrent.ConcurrentHashMap

interface UserApi {
    suspend fun upsertUser(dto: UserDto)
    suspend fun deleteUser(id: String)
    suspend fun fetchUsersUpdatedAfter(lastSyncEpochMillis: Long): List<UserDto>
}

/**
 * Demo-only fake API so the sample compiles and runs
 * without a backend while still exercising push + pull flow.
 */
class InMemoryUserApi : UserApi {
    private val remote = ConcurrentHashMap<String, Pair<UserDto, Long>>()

    override suspend fun upsertUser(dto: UserDto) {
        remote[dto.id] = dto to System.currentTimeMillis()
    }

    override suspend fun deleteUser(id: String) {
        remote.remove(id)
    }

    override suspend fun fetchUsersUpdatedAfter(lastSyncEpochMillis: Long): List<UserDto> {
        return remote.values
            .filter { (_, updatedAt) -> updatedAt > lastSyncEpochMillis }
            .map { (dto, _) -> dto }
    }
}
