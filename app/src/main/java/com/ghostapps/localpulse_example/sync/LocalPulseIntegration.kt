package com.ghostapps.localpulse_example.sync

import android.content.Context
import androidx.room.Room
import com.ghostapps.localpulse.LocalPulse
import com.ghostapps.localpulse.LocalPulseConfig
import com.ghostapps.localpulse.LocalPulseSyncEngine
import com.ghostapps.localpulse.conflict.ConflictResolution
import com.ghostapps.localpulse.conflict.ConflictResolver
import com.ghostapps.localpulse.core.OperationProcessor
import com.ghostapps.localpulse.core.OperationResult
import com.ghostapps.localpulse.model.OperationType
import com.ghostapps.localpulse.model.SyncOperation
import com.ghostapps.localpulse.pull.PullCoordinator
import com.ghostapps.localpulse.pull.PullResult
import com.ghostapps.localpulse.scheduling.PulseNetworkRequirement
import com.ghostapps.localpulse.scheduling.PulseWorkPolicy
import java.io.IOException

class UserRepository(
    private val userDao: UserDao,
    private val syncEngine: LocalPulseSyncEngine
) {
    suspend fun saveUser(entity: UserEntity) {
        userDao.insertOrUpdate(entity)
        syncEngine.enqueue(
            SyncOperation(
                operationId = "user-${entity.id}-${System.currentTimeMillis()}",
                entity = "user",
                entityId = entity.id,
                type = OperationType.UPDATE,
                payload = JsonCodec.toJson(entity.toDto())
            )
        )
    }

    suspend fun deleteUser(id: String) {
        userDao.deleteById(id)
        syncEngine.enqueue(
            SyncOperation(
                operationId = "user-delete-$id-${System.currentTimeMillis()}",
                entity = "user",
                entityId = id,
                type = OperationType.DELETE,
                payload = "{}"
            )
        )
    }
}

class AppSyncProcessor(
    private val userApi: UserApi
) : OperationProcessor {
    override suspend fun process(operation: SyncOperation): OperationResult {
        return try {
            when (operation.entity) {
                "user" -> handleUser(operation)
                else -> OperationResult.FatalFailure("Unknown entity ${operation.entity}")
            }
        } catch (io: IOException) {
            OperationResult.RetryableFailure(io.message ?: "network error")
        } catch (e: Exception) {
            OperationResult.FatalFailure(e.message ?: "unexpected error")
        }
    }

    private suspend fun handleUser(operation: SyncOperation): OperationResult {
        return when (operation.type) {
            OperationType.CREATE, OperationType.UPDATE -> {
                userApi.upsertUser(JsonCodec.fromJson(operation.payload))
                OperationResult.Success
            }

            OperationType.DELETE -> {
                userApi.deleteUser(operation.entityId)
                OperationResult.Success
            }
        }
    }
}

class AppPullCoordinator(
    private val userApi: UserApi,
    private val userDao: UserDao,
    private val syncMetaStore: SyncMetaStore
) : PullCoordinator {
    override suspend fun pullAll(): PullResult {
        return runCatching {
            val lastSync = syncMetaStore.getLastUserSyncEpochMillis()
            val remoteUsers = userApi.fetchUsersUpdatedAfter(lastSync)
            userDao.upsertAll(remoteUsers.map { it.toEntity() })
            syncMetaStore.setLastUserSyncEpochMillis(System.currentTimeMillis())
            PullResult.Success
        }.getOrElse {
            PullResult.RetryableFailure(it.message ?: "pull failed")
        }
    }
}

data class AppContainer(
    val db: AppDatabase,
    val userRepository: UserRepository,
    val syncEngine: LocalPulseSyncEngine
)

object LocalPulseContainer {
    @Volatile
    private var instance: AppContainer? = null

    fun get(context: Context): AppContainer {
        return instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }
    }

    private fun create(context: Context): AppContainer {
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "localpulse-example.db"
        ).build()
        val userDao = db.userDao()
        val userApi = InMemoryUserApi()
        val syncMetaStore = SyncMetaStore(context)
        val syncEngine = LocalPulse.createPersistent(
            context = context,
            config = LocalPulseConfig(
                processor = AppSyncProcessor(userApi),
                pullCoordinator = AppPullCoordinator(userApi, userDao, syncMetaStore),
                conflictResolver = ConflictResolver { _, _ -> ConflictResolution.MarkConflict },
                uniqueWorkName = "invo-sync-work",
                scheduleOnEnqueue = true,
                scheduleAfterSyncNow = true,
                workPolicy = PulseWorkPolicy.KEEP,
                workNetworkType = PulseNetworkRequirement.CONNECTED
            )
        )
        val repository = UserRepository(userDao, syncEngine)
        return AppContainer(
            db = db,
            userRepository = repository,
            syncEngine = syncEngine
        )
    }
}
