package edu.ldubgd.authservice.security.requests

import io.swagger.v3.oas.annotations.media.Schema

data class SignUpRequest(
    @Schema(description = "Логін користувача", example = "User123")
    val login: String,
    @Schema(description = "Пароль користувача", example = "Secret")
    val password: String,
    @Schema(description = "ID поля в таблиці контактів користувачів", example = "333")
    val contactId: Int,
    @Schema(description = "Назви ролей для надання доступу користувачу", example = "[\"ROLE_TEST_ADMIN\"]")
    val roles: List<String>
)
