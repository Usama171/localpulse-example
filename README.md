# LocalPulse Example

`LocalPulse` is an Android offline sync library for `Room + WorkManager` apps.
It provides a persistent operation queue, retries with backoff, conflict handling, pull coordination, and observable sync state.

This repo is a runnable integration example.

## Dependency

```kotlin
implementation("io.github.usama171:localpulse:0.1.0")
```

## What it does

- Queues writes locally (`CREATE`, `UPDATE`, `DELETE`)
- Persists queue state
- Drains queue with WorkManager
- Retries retryable failures using exponential backoff + jitter
- Exposes queue and per-item state via `Flow`
- Supports pluggable conflict resolution strategy
- Supports bi-directional sync by running pull after push queue drain

## Quick start

Create the persistent engine:

```kotlin
val engine = LocalPulse.createPersistent(
    context = applicationContext,
    config = LocalPulseConfig(
        processor = { operation ->
            // Call your Retrofit/Ktor API here.
            OperationResult.Success
        },
        pullCoordinator = {
            // Pull remote changes and store them in your Room tables.
            PullResult.Success
        },
    )
)
```

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
    processor = { operation -> OperationResult.Conflict("version mismatch") },
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

`LocalPulse` runs **push then pull**:

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

## This repo's integration

Sample integration is wired in:

- `app/src/main/java/com/ghostapps/localpulse_example/MainActivity.kt`
- `app/src/main/java/com/ghostapps/localpulse_example/sync`

Main flow in this example:

1. Save `UserEntity` in Room first
2. Enqueue sync operation for push
3. LocalPulse worker pushes pending writes
4. LocalPulse then pulls latest users and upserts to Room

The demo uses an in-memory `UserApi` implementation so you can run and test the flow without a backend.
