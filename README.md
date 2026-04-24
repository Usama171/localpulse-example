# LocalPulse

`LocalPulse` is an Android offline sync library for `Room + WorkManager` apps.
It provides a persistent operation queue, retries with backoff, conflict handling, pull coordination, and observable sync state.

## What it does

- Queues writes locally (`CREATE`, `UPDATE`, `DELETE`)
- Persists queue state in Room
- Drains queue with WorkManager
- Retries retryable failures using exponential backoff + jitter
- Exposes queue and per-item state via `Flow`
- Supports pluggable conflict resolution strategy
- Supports bi-directional sync by running pull after push queue drain

## Quick start

If you installed LocalPulse from Maven Central, add:

```kotlin
implementation("io.github.usama171:localpulse:0.1.1")
```

Maven:

```xml
<dependency>
  <groupId>io.github.usama171</groupId>
  <artifactId>localpulse</artifactId>
  <version>0.1.1</version>
</dependency>
```

If you are using this repository directly as a multi-module project, add the local module dependency:

```kotlin
implementation(project(":localpulse"))
```

Create the persistent engine:

```kotlin
val engine = LocalPulse.createPersistent(
    context = applicationContext,
    config = LocalPulseConfig(
        serverSync = ServerSyncConfig(
            baseUrl = BuildConfig.LOCALPULSE_SERVER_BASE_URL
        ),
        pullCoordinator = {
            // Pull remote changes and store them in your Room tables.
            PullResult.Success
        },
    )
)
```

### Server sync is built in

For most apps, you only configure `ServerSyncConfig` and LocalPulse handles HTTP push for queued operations.
You can still customize request details when needed.

- base URL
- default headers (for auth, tenant, app version, etc.)
- per-operation request mapping (method/path/body/extra headers)

```kotlin
val serverConfig = ServerSyncConfig(
    baseUrl = BuildConfig.LOCALPULSE_SERVER_BASE_URL,
    defaultHeadersProvider = {
        mapOf("Authorization" to "Bearer ${tokenProvider()}")
    },
    requestBuilder = { operation ->
        ServerSyncRequest(
            method = "PATCH",
            path = "/api/v1/${operation.entity}/${operation.entityId}",
            body = operation.payload,
            headers = mapOf("X-Entity" to operation.entity)
        )
    }
)

val engine = LocalPulse.createPersistent(
    context = applicationContext,
    config = LocalPulseConfig(
        serverSync = serverConfig
    )
)
```

Change `LOCALPULSE_SERVER_BASE_URL` in `app/build.gradle.kts` (or inject via flavors/build types/env) to point to each developer/server environment.

Enqueue an operation:

```kotlin
engine.enqueue(
    SyncOperation(
        operationId = "op-123",
        entity = "task",
        entityId = "42",
        type = OperationType.UPDATE,
        payload = """{"title":"Buy milk"}"""
    )
)
```

Observe queue state:

```kotlin
engine.observeQueue().collect { states ->
    // pending / syncing / success / failed / conflict
}
```

## Conflict resolver strategy

Provide a custom resolver with `LocalPulseConfig(conflictResolver = ...)`.

```kotlin
val config = LocalPulseConfig(
    serverSync = ServerSyncConfig(baseUrl = BuildConfig.LOCALPULSE_SERVER_BASE_URL),
    conflictResolver = ConflictResolver { operation, reason ->
        // Example: convert conflict to retry
        ConflictResolution.Retry("Retry after refresh: $reason")
    }
)
```

Available resolutions:

- `ConflictResolution.MarkConflict`
- `ConflictResolution.MarkSuccess`
- `ConflictResolution.Retry(reason)`
- `ConflictResolution.MarkFailed(reason)`

## Connectivity and scheduling options

`LocalPulseConfig` supports scheduler customization:

- `scheduleOnEnqueue`: auto-schedule WorkManager after `enqueue`
- `scheduleAfterSyncNow`: re-schedule WorkManager after manual sync
- `workPolicy`: `PulseWorkPolicy` (`KEEP` or `REPLACE`)
- `workNetworkType`: `PulseNetworkRequirement` (`CONNECTED`, `NOT_REQUIRED`, `UNMETERED`)
- `uniqueWorkName`: unique background sync chain name

## Bi-directional pull sync

`LocalPulse` now runs **push then pull**:

1. Drain pending local write operations (push queue)
2. Run `pullCoordinator.pullAll()` to fetch server updates into local storage

For apps with many entities, route pulls in your coordinator:

```kotlin
val pullCoordinator = PullCoordinator {
    runCatching {
        userSync.pullUsers()
        ordersSync.pullOrders()
        inventorySync.pullInventory()
        // ... other entities
    }.fold(
        onSuccess = { PullResult.Success },
        onFailure = { PullResult.RetryableFailure(it.message ?: "pull failed") }
    )
}
```

## Sample integration snippet

The sample app wires `LocalPulse` in `app/src/main/java/com/ghostapps/localpulse/MainActivity.kt`.

## Full example with your `UserEntity`

This example shows one complete flow for your user table:

- save `UserEntity` in Room first
- enqueue sync operation for push
- LocalPulse worker pushes pending writes
- LocalPulse then pulls latest server users and upserts to Room

### 1) User model and DTO mapper

```kotlin
@Entity(tableName = UserDAO.TABLE_NAME)
class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = UserDAO.ID)
    var id: String = "0",
    @ColumnInfo(name = UserDAO.PROCESS)
    var Process: String = "",
    @ColumnInfo(name = UserDAO.FIRST_NAME)
    var FirstName: String = "",
    @ColumnInfo(name = UserDAO.LAST_NAME)
    var LastName: String = "",
    @ColumnInfo(name = UserDAO.EMAIL)
    var Email: String = "",
    @ColumnInfo(name = UserDAO.USER_NAME)
    var Username: String = "",
    @ColumnInfo(name = UserDAO.PASSWORD)
    var Password: String = "",
    @ColumnInfo(name = UserDAO.ROLE)
    var Role: String = ""
) : Serializable

@Serializable
data class UserDto(
    val id: String,
    val process: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val username: String,
    val password: String,
    val role: String
)

fun UserEntity.toDto(): UserDto = UserDto(
    id = id,
    process = Process,
    firstName = FirstName,
    lastName = LastName,
    email = Email,
    username = Username,
    password = Password,
    role = Role
)

fun UserDto.toEntity(): UserEntity = UserEntity(
    id = id,
    Process = process,
    FirstName = firstName,
    LastName = lastName,
    Email = email,
    Username = username,
    Password = password,
    Role = role
)
```

### 2) Repository (local-first + enqueue)

```kotlin
class UserRepository(
    private val userDao: UserDAO,
    private val syncEngine: LocalPulseSyncEngine,
    private val json: Json
) {
    suspend fun saveUser(entity: UserEntity) {
        // 1) Local first
        userDao.insertOrUpdate(entity)

        // 2) Queue push
        val payload = json.encodeToString(entity.toDto())
        syncEngine.enqueue(
            SyncOperation(
                operationId = "user-${entity.id}-${System.currentTimeMillis()}",
                entity = "user",
                entityId = entity.id,
                type = OperationType.UPDATE,
                payload = payload
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
```

### 3) Push processor for user operations

```kotlin
interface UserApi {
    suspend fun upsertUser(dto: UserDto)
    suspend fun deleteUser(id: String)
    suspend fun fetchUsersUpdatedAfter(lastSyncEpochMillis: Long): List<UserDto>
}

class AppSyncProcessor(
    private val userApi: UserApi,
    private val json: Json
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
                val dto = json.decodeFromString<UserDto>(operation.payload)
                userApi.upsertUser(dto)
                OperationResult.Success
            }
            OperationType.DELETE -> {
                userApi.deleteUser(operation.entityId)
                OperationResult.Success
            }
        }
    }
}
```

### 4) Pull coordinator for bi-directional sync

```kotlin
class AppPullCoordinator(
    private val userApi: UserApi,
    private val userDao: UserDAO,
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
```

### 5) Wire LocalPulse in `Application` or DI module

```kotlin
val localPulseEngine = LocalPulse.createPersistent(
    context = appContext,
    config = LocalPulseConfig(
        processor = AppSyncProcessor(userApi, json),
        pullCoordinator = AppPullCoordinator(userApi, userDao, syncMetaStore),
        conflictResolver = ConflictResolver { _, _ ->
            ConflictResolution.MarkConflict
        },
        uniqueWorkName = "invo-sync-work",
        scheduleOnEnqueue = true,
        scheduleAfterSyncNow = true,
        workPolicy = PulseWorkPolicy.KEEP,
        workNetworkType = PulseNetworkRequirement.CONNECTED
    )
)
```

### 6) Runtime behavior

1. User creates/updates a user row while offline.
2. Repository writes to Room immediately.
3. Repository enqueues `SyncOperation(entity = "user", ...)`.
4. WorkManager runs when network is available.
5. LocalPulse pushes queued writes via `OperationProcessor`.
6. LocalPulse runs `pullCoordinator.pullAll()` and refreshes local Room user data.
7. UI observes Room, so user screens update automatically.

> To scale this to 10 entities, keep this exact pattern for each entity and route both push and pull using entity keys (`"user"`, `"orders"`, `"inventory"`, etc.).
