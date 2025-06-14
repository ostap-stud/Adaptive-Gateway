package edu.ldubgd.authservice.security.requests

import io.swagger.v3.oas.annotations.media.Schema

data class LogInRequest (
    @Schema(description = "Логін користувача", example = "User123")
    val login: String,
    @Schema(description = "Пароль користувача", example = "Secret")
    val password: String
)