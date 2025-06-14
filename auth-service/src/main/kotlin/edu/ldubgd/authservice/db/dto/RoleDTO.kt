package edu.ldubgd.authservice.db.dto

import io.swagger.v3.oas.annotations.media.Schema

data class RoleDTO(
    @Schema(description = "Назва ролі", example = "ADMIN")
    val roleName: String,
    @Schema(description = "Назва сервісу застосування ролі", example = "TESTSERVICE")
    val application: String,
    @Schema(description = "Опис ролі")
    val roleDescription: String?
)
