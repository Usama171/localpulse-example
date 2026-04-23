package com.ghostapps.localpulse_example.sync

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = UserDao.TABLE_NAME)
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = UserDao.ID)
    var id: String = "0",
    @ColumnInfo(name = UserDao.PROCESS)
    var process: String = "",
    @ColumnInfo(name = UserDao.FIRST_NAME)
    var firstName: String = "",
    @ColumnInfo(name = UserDao.LAST_NAME)
    var lastName: String = "",
    @ColumnInfo(name = UserDao.EMAIL)
    var email: String = "",
    @ColumnInfo(name = UserDao.USER_NAME)
    var username: String = "",
    @ColumnInfo(name = UserDao.PASSWORD)
    var password: String = "",
    @ColumnInfo(name = UserDao.ROLE)
    var role: String = ""
) : Serializable

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
    process = process,
    firstName = firstName,
    lastName = lastName,
    email = email,
    username = username,
    password = password,
    role = role
)

fun UserDto.toEntity(): UserEntity = UserEntity(
    id = id,
    process = process,
    firstName = firstName,
    lastName = lastName,
    email = email,
    username = username,
    password = password,
    role = role
)
