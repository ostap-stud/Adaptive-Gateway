package edu.ldubgd.authservice.db.dto

import io.swagger.v3.oas.annotations.media.Schema

data class ServiceRouteDTO(
    @Schema(description = "Шлях маршруту (regexp), напр. /test-service/**", example = "/test-service/**")
    val route: String,
    @Schema(description = "Опис маршруту", example = "Тестовий маршрут для GET-запиту")
    val routeDescription: String?,
    @Schema(description = "Назва сервісу, до якого привʼязаний маршрут (server name!)", example = "test-service")
    val serviceName: String,
    @Schema(description = "Чи є маршрут внутрішнім", example = "false")
    val isInternal: Boolean = false,
    @Schema(description = "Пріоритетність маршруту (-5 > 0)", example = "0")
    val order: Int = 0,
    @Schema(description = "Список ролей, необхідних для доступу до маршруту", example = "[\"ROLE_TEST_USER\", \"ROLE_TEST_ADMIN\"]")
    val roles: Set<String> = emptySet(),
    @Schema(description = "Список фільтрів для маршруту")
    val filters: Set<FilterDTO> = emptySet(),
    @Schema(description = "Список предикатів для маршруту")
    val predicates: Set<PredicateDTO> = emptySet()
)
