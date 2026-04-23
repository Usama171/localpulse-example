package com.ghostapps.localpulse_example.sync

import org.json.JSONObject

object JsonCodec {
    fun toJson(dto: UserDto): String {
        return JSONObject()
            .put("id", dto.id)
            .put("process", dto.process)
            .put("firstName", dto.firstName)
            .put("lastName", dto.lastName)
            .put("email", dto.email)
            .put("username", dto.username)
            .put("password", dto.password)
            .put("role", dto.role)
            .toString()
    }

    fun fromJson(payload: String): UserDto {
        val json = JSONObject(payload)
        return UserDto(
            id = json.optString("id"),
            process = json.optString("process"),
            firstName = json.optString("firstName"),
            lastName = json.optString("lastName"),
            email = json.optString("email"),
            username = json.optString("username"),
            password = json.optString("password"),
            role = json.optString("role")
        )
    }
}
