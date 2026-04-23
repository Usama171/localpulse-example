package com.ghostapps.localpulse_example.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<UserEntity>)

    @Query("DELETE FROM $TABLE_NAME WHERE $ID = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM $TABLE_NAME ORDER BY $FIRST_NAME ASC")
    fun observeAll(): Flow<List<UserEntity>>

    companion object {
        const val TABLE_NAME = "users"
        const val ID = "id"
        const val PROCESS = "process"
        const val FIRST_NAME = "first_name"
        const val LAST_NAME = "last_name"
        const val EMAIL = "email"
        const val USER_NAME = "username"
        const val PASSWORD = "password"
        const val ROLE = "role"
    }
}
