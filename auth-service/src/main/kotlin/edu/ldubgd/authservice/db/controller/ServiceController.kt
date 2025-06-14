package edu.ldubgd.authservice.db.controller

import edu.ldubgd.authservice.db.*
import edu.ldubgd.authservice.db.dto.*
import edu.ldubgd.authservice.db.service.FilterService
import edu.ldubgd.authservice.db.service.PredicateService
import edu.ldubgd.authservice.db.service.RouteService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/\${spring.application.name}/service")
@Tag(name = "Service Management", description = "Керування мікросервісами та їх маршрутами")
class ServiceController {

    @Autowired
    private lateinit var serviceRepository: ServiceRepository

    @Autowired
    private lateinit var serviceRouteRepository: ServiceRouteRepository

    @Autowired
    private lateinit var routeService: RouteService

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var filterService: FilterService

    @Autowired
    private lateinit var predicateService: PredicateService

    @Operation(
        summary = "Отримання сервісів та їх маршрутів",
        responses = [
            ApiResponse(responseCode = "200")
        ]
    )
    @GetMapping("/")
    fun getServices(): ResponseEntity<Any> {
        return ResponseEntity(serviceRepository.findAll(), HttpStatus.OK)
    }

    @Operation(
        summary = "Отримання інформації про сервіс за назвою",
        responses = [
            ApiResponse(responseCode = "200"),
            ApiResponse(responseCode = "404", description = "Сервіс не знайдено")
        ]
    )
    @GetMapping("/{name}")
    fun getService(@PathVariable("name") name: String): ResponseEntity<Any> {
        serviceRepository.findByServiceName(name)?.let {
            return ResponseEntity(it, HttpStatus.OK)
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Service is not found")
    }

    @Operation(
        summary = "Створення та збереження сервісів",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список сервісів для збереження",
            content = [
                Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = ServiceDTO::class)),
                    examples = [
                        ExampleObject(
                            name = "Example of adding services",
                            value = """
                            [
                              {
                                "name": "test-service",
                                "description": "About service"
                              }
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "201", description = "Сервіси успішно додано"),
            ApiResponse(responseCode = "204", description = "Тіло запиту порожнє"),
            ApiResponse(responseCode = "400", description = "Помилка обробки запиту")
        ]
    )
    @Transactional
    @PostMapping("/add")
    fun addServices(@RequestBody services: List<ServiceDTO>): ResponseEntity<Any> {
        try {
            if (services.isNotEmpty()) {
                services.forEach {
                    serviceRepository.save(
                        Service(
                            id = 0,
                            serviceName = it.name,
                            serviceDesc = it.description
                        )
                    )
                }
            }else {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No services found in request body")
            }
        }catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.message)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Successfully added ${services.size} services")
    }

    @Operation(
        summary = "Оновлення сервісу",
        parameters = [Parameter(
            name = "Service ID",
            required = true,
            example = "0"
        )],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Сервіс з оновленими даними",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ServiceDTO::class),
                    examples = [
                        ExampleObject(
                            name = "Example of updating service",
                            value = """
                            [
                              {
                                "name": "test-service",
                                "description": "About service"
                              }
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Сервіс успішно оновлено"),
            ApiResponse(responseCode = "404", description = "Сервіс не знайдено")
        ]
    )
    @PutMapping("/{id}")
    fun updateService(@PathVariable("id") id: Int, @RequestBody service: ServiceDTO): ResponseEntity<Any> {
        val current = serviceRepository.findById(id)
        if (current.isPresent) {
            val updated = current.get().copy(serviceName = service.name, serviceDesc = service.description)
            serviceRepository.save(updated)
            return ResponseEntity.status(HttpStatus.OK).body("Successfully updated Service (id: $id)")
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Service is not found")
    }

    @Operation(
        summary = "Видалення сервісу",
        parameters = [Parameter(
            name = "Service ID",
            required = true,
            example = "0"
        )],
        responses = [
            ApiResponse(responseCode = "200", description = "Сервіс успішно видалено"),
            ApiResponse(responseCode = "404", description = "Сервіс не знайдено")
        ]
    )
    @DeleteMapping("/{id}")
    fun deleteService(@PathVariable("id") id: Int): ResponseEntity<Any> {
        val deleteService = serviceRepository.findById(id)
        if (deleteService.isPresent) {
            serviceRepository.deleteById(id)
            return ResponseEntity.ok("Successfully deleted Service (id: $id)")
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Service is not found")
    }

    @Operation(
        summary = "Створення та збереження сервісних маршрутів",
        description =
        """
        Цей ендпоінт дозволяє зареєструвати один або кілька маршрутів, що обов'язково прив'язуються до певного сервісу. 
        Підтримуються предикати, фільтри, ролі доступу, пріоритет маршруту та можливість створення внутрішнього маршруту (не реєструється шлюзом). 
        Предикати та фільтри створюються автоматично за умови відсутності в БД.
        Після збереження та очищення кешу шлюз автоматично зареєструє новий маршрут, або це можна спровокувати через Actuator API.
        """,
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список маршрутів для збереження",
            content = [
                Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = ServiceRouteDTO::class)),
                    examples = [
                        ExampleObject(
                            name = "Example of adding service routes",
                            value = """
                            [
                              {
                                "route": "/test-service/**",
                                "routeDescription": "Endpoints for test service",
                                "serviceName": "test-service",
                                "isInternal": false,
                                "order": 0,
                                "roles": ["ROLE_APPLICATION_NAME"],
                                "filters": [
                                  {
                                    "name": "Authentication",
                                    "args": {}
                                  }
                                ],
                                "predicates": [
                                  {
                                    "name": "Method",
                                    "args": {
                                      "methods": "GET,POST"
                                    }
                                  }
                                ]
                              }
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "201", description = "Маршрути успішно додано"),
            ApiResponse(responseCode = "204", description = "Тіло запиту порожнє"),
            ApiResponse(responseCode = "400", description = "Помилка обробки запиту")
        ]
    )
    @Transactional
    @PostMapping("/route/add")
    fun addServiceRoutes(@RequestBody serviceRoutes: List<ServiceRouteDTO>): ResponseEntity<Any> {
        try {
            if (serviceRoutes.isNotEmpty()) {
                serviceRoutes.forEach { routeDTO ->
                    routeService.insertRoute(routeDTO)
                }
            }else {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No service-routes found in request body")
            }
        }catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.message)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Successfully added ${serviceRoutes.size} service routes")
    }

    @Operation(
        summary = "Оновлення сервісного маршруту",
        description =
        """
        Операція ПОВНОГО оновлення маршруту за вказаним ідентифікатором в параметрах запиту. 
        Підтримується все те ж, що я для створення маршруту. 
        Предикати та фільтри створюються автоматично за умови відсутності в БД.
        Після збереження та очищення кешу шлюз автоматично зареєструє новий маршрут, або це можна спровокувати через Actuator API.
        """,
        parameters = [Parameter(
            name = "Route ID",
            required = true,
            example = "0"
        )],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Маршрут для оновлення",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ServiceRouteDTO::class),
                    examples = [
                        ExampleObject(
                            name = "Example of updating service routes",
                            value = """
                              {
                                "route": "/test-service/**",
                                "routeDescription": "Endpoints for test service",
                                "serviceName": "test-service",
                                "isInternal": false,
                                "order": 0,
                                "roles": ["ROLE_APPLICATION_NAME"],
                                "filters": [
                                  {
                                    "name": "Authentication",
                                    "args": {}
                                  }
                                ],
                                "predicates": [
                                  {
                                    "name": "Method",
                                    "args": {
                                      "methods": "GET,POST"
                                    }
                                  }
                                ]
                              }
                            """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Маршрути успішно оновлено"),
            ApiResponse(responseCode = "204", description = "Тіло запиту порожнє"),
            ApiResponse(responseCode = "400", description = "Помилка обробки запиту")
        ]
    )
    @PutMapping("/route/{id}")
    fun updateServiceRoute(@PathVariable("id") id: Int, @RequestBody serviceRoute: ServiceRouteDTO): ResponseEntity<Any> {
        val current = serviceRouteRepository.findById(id)
        if (current.isPresent) {
            try {
                routeService.updateRoute(current, serviceRoute)
                return ResponseEntity.status(HttpStatus.OK).body("Successfully updated Service-route (id: $id)")
            } catch (ex: Exception){
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

    @Operation(
        summary = "Видалення сервісного маршруту",
        parameters = [Parameter(
            name = "Route ID",
            required = true,
            example = "0"
        )],
        responses = [
            ApiResponse(responseCode = "200", description = "Маршрут успішно видалено"),
            ApiResponse(responseCode = "404", description = "Маршрут не знайдено")
        ]
    )
    @DeleteMapping("/route/{id}")
    fun deleteServiceRoute(@PathVariable("id") id: Int): ResponseEntity<Any> {
        val deleteRoute = serviceRouteRepository.findById(id)
        if (deleteRoute.isPresent) {
            serviceRouteRepository.deleteById(id)
            return ResponseEntity.ok("Successfully deleted Service-route (id: $id)")
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

    @Operation(
        summary = "Додавання до маршруту ролей доступу за назвою",
        parameters = [Parameter(
            name = "Route ID",
            required = true,
            example = "0"
        )],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список ролей для маршруту",
            content = [
                Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "Example of adding list of roles to given route",
                            value = """
                            [
                              "ROLE_APPLICATION_NAME"
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "201", description = "Доступ для ролей успішно надано"),
            ApiResponse(responseCode = "204", description = "Тіло запиту порожнє"),
            ApiResponse(responseCode = "400", description = "Помилка обробки запиту"),
            ApiResponse(responseCode = "404", description = "Маршрут не найдено")
        ]
    )
    @PostMapping("/route/{id}/role")
    fun addServiceRouteRoles(@PathVariable("id") id: Int, @RequestBody roleNames: List<String>): ResponseEntity<Any> {
        val current = serviceRouteRepository.findById(id)
        if (current.isPresent) {
            try {
                if (roleNames.isNotEmpty()){
                    val route = current.get()
                    val roles = roleRepository.findRolesByRoleIn(roleNames)
                    roles.forEach {
                        route.addRole(it)
                    }
                    serviceRouteRepository.save(route)
                    return ResponseEntity.status(HttpStatus.OK).body("Successfully granted access to route for given roles. Route path: ${route.route}")
                } else {
                    return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No role-names found in request body")
                }
            } catch(ex: Exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

    @Operation(
        summary = "Видалення ролей з дозволених для даного маршруту",
        parameters = [Parameter(
            name = "Route ID",
            required = true,
            example = "0"
        )],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список ролей для заборони",
            content = [
                Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "Example of removing list of roles from given route",
                            value = """
                            [
                              "ROLE_APPLICATION_NAME"
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Ролі заборонено успішно"),
            ApiResponse(responseCode = "204", description = "Тіло запиту пророжнє"),
            ApiResponse(responseCode = "404", description = "Маршрут не знайдено")
        ]
    )
    @DeleteMapping("/route/{id}/role")
    fun removeServiceRouteRoles(@PathVariable("id") id: Int, @RequestBody roleNames: List<String>): ResponseEntity<Any> {
        val current = serviceRouteRepository.findById(id)
        if (current.isPresent) {
            if (roleNames.isNotEmpty()) {
                val route = current.get()
                roleRepository.findRolesByRoleIn(roleNames)
                    .filter { role -> route.roles.any { it.roleId == role.id } }
                    .forEach { role -> route.removeRole(role) }
                serviceRouteRepository.save(route)
                return ResponseEntity.status(HttpStatus.OK).body("Successfully banned access to route for given roles. Route path: ${route.route}")
            } else {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No role-names found in request body")
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

    @Operation(
        summary = "Додавання Spring Gateway Route Filters або ж власних фільтрів до вказаного маршруту",
        parameters = [Parameter(
            name = "Route ID",
            required = true,
            example = "0"
        )],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список фільтрів для маршруту",
            content = [
                Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = FilterDTO::class)),
                    examples = [
                        ExampleObject(
                            name = "Example of adding list of filters to given route",
                            value = """
                            [
                              {
                                "name": "AddResponseHeader",
                                "args": {
                                  "name": "X-Response-Test",
                                  "value": "Test"
                                }
                              }
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "201", description = "Фільтри успішно додано до заданого маршруту"),
            ApiResponse(responseCode = "204", description = "Тіло запиту порожнє"),
            ApiResponse(responseCode = "400", description = "Помилка обробки запиту"),
            ApiResponse(responseCode = "404", description = "Маршрут не найдено")
        ]
    )
    @PostMapping("/route/{id}/filter")
    fun addServiceRouteFilters(@PathVariable("id") id: Int, @RequestBody filtersDTO: List<FilterDTO>): ResponseEntity<Any> {
        val current = serviceRouteRepository.findById(id)
        if (current.isPresent) {
            try {
                if (filtersDTO.isNotEmpty()){
                    val route = current.get()
                    val filters = filterService.getFiltersFromDTO(filtersDTO)
                    filters.forEach {
                        route.addFilter(it)
                    }
                    serviceRouteRepository.save(route)
                    return ResponseEntity.status(HttpStatus.OK).body("Successfully registered filters to route with path: ${route.route}")
                } else {
                    return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No filters found in request body")
                }
            } catch(ex: Exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

    @Operation(
        summary = "Видалення фільтрів для даного маршруту за їх назвою",
        parameters = [Parameter(
            name = "Route ID",
            required = true,
            example = "0"
        )],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список фільтрів для видалення",
            content = [
                Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "Example of removing list of filters from given route",
                            value = """
                            [
                              "Authentication"
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Фільтри успішно зняті з даного маршруту"),
            ApiResponse(responseCode = "204", description = "Тіло запиту пророжнє"),
            ApiResponse(responseCode = "404", description = "Маршрут не знайдено")
        ]
    )
    @DeleteMapping("/route/{id}/filter")
    fun removeServiceRouteFilters(@PathVariable("id") id: Int, @RequestBody filterNames: List<String>): ResponseEntity<Any> {
        val current = serviceRouteRepository.findById(id)
        if (current.isPresent) {
            if (filterNames.isNotEmpty()) {
                val route = current.get()
                filterService.getFiltersByNames(filterNames)
                    .filter { filter -> route.filters.any { it.filterId == filter.id } }
                    .forEach { filter -> route.removeFilter(filter) }
                serviceRouteRepository.save(route)
                return ResponseEntity.status(HttpStatus.OK).body("Successfully unregistered filters from route with path: ${route.route}")
            } else {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No filter-names found in request body")
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

    @Operation(
        summary = "Додавання Spring Gateway Route Predicates або ж власних предикатів (умов) до вказаного маршруту",
        parameters = [Parameter(
            name = "Route ID",
            required = true,
            example = "0"
        )],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список предикатів для маршруту",
            content = [
                Content(
                    mediaType = "application/json",
                    array = ArraySchema(schema = Schema(implementation = PredicateDTO::class)),
                    examples = [
                        ExampleObject(
                            name = "Example of adding list of predicates to given route",
                            value = """
                            [
                              {
                                "name": "Method",
                                "args": {
                                  "methods": "GET,POST"
                                }
                              }
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "201", description = "Предикати успішно додано до заданого маршруту"),
            ApiResponse(responseCode = "204", description = "Тіло запиту порожнє"),
            ApiResponse(responseCode = "400", description = "Помилка обробки запиту"),
            ApiResponse(responseCode = "404", description = "Маршрут не найдено")
        ]
    )
    @PostMapping("/route/{id}/predicate")
    fun addServiceRoutePredicates(@PathVariable("id") id: Int, @RequestBody predicatesDTO: List<PredicateDTO>): ResponseEntity<Any> {
        val current = serviceRouteRepository.findById(id)
        if (current.isPresent) {
            try {
                if (predicatesDTO.isNotEmpty()){
                    val route = current.get()
                    val predicates = predicateService.getPredicatesAndSaveNew(predicatesDTO)
                    predicates.forEach {
                        route.addPredicate(it)
                    }
                    serviceRouteRepository.save(route)
                    return ResponseEntity.status(HttpStatus.OK).body("Successfully registered predicates to route with path: ${route.route}")
                } else {
                    return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No predicates found in request body")
                }
            } catch(ex: Exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

    @Operation(
        summary = "Видалення предикатів для даного маршруту за їх назвою",
        parameters = [Parameter(
            name = "Route ID",
            required = true,
            example = "0"
        )],
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Список предикатів для видалення",
            content = [
                Content(
                    mediaType = "application/json",
                    examples = [
                        ExampleObject(
                            name = "Example of removing list of predicates from given route",
                            value = """
                            [
                              "Method"
                            ]
                        """
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(responseCode = "200", description = "Предикати успішно зняті з даного маршруту"),
            ApiResponse(responseCode = "204", description = "Тіло запиту пророжнє"),
            ApiResponse(responseCode = "404", description = "Маршрут не знайдено")
        ]
    )
    @DeleteMapping("/route/{id}/predicate")
    fun removeServiceRoutePredicates(@PathVariable("id") id: Int, @RequestBody predicateNames: List<String>): ResponseEntity<Any> {
        val current = serviceRouteRepository.findById(id)
        if (current.isPresent) {
            if (predicateNames.isNotEmpty()) {
                val route = current.get()
                predicateService.getPredicatesByNames(predicateNames)
                    .filter { predicate -> route.predicates.any { it.predicateId == predicate.id } }
                    .forEach { predicate -> route.removePredicate(predicate) }
                serviceRouteRepository.save(route)
                return ResponseEntity.status(HttpStatus.OK).body("Successfully unregistered predicates from route with path: ${route.route}")
            } else {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No predicate-names found in request body")
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

}