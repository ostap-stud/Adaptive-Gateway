package edu.ldubgd.authservice.db.controller

import edu.ldubgd.authservice.db.*
import edu.ldubgd.authservice.db.dto.*
import edu.ldubgd.authservice.db.service.FilterService
import edu.ldubgd.authservice.db.service.PredicateService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/\${spring.application.name}/service")
class ServiceController {

    @Autowired
    private lateinit var serviceRepository: ServiceRepository

    @Autowired
    private lateinit var serviceRouteRepository: ServiceRouteRepository

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var filterService: FilterService

    @Autowired
    private lateinit var predicateService: PredicateService

    @GetMapping("/")
    fun getServices(): ResponseEntity<Any> {
        return ResponseEntity(serviceRepository.findAll(), HttpStatus.OK)
    }

    @GetMapping("/{name}")
    fun getService(@PathVariable("name") name: String): ResponseEntity<Any> {
        serviceRepository.findByServiceName(name)?.let {
            return ResponseEntity(it, HttpStatus.OK)
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Service is not found")
    }

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
//            e.printStackTrace()
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Successfully added ${services.size} services")
    }

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

    @DeleteMapping("/{id}")
    fun deleteService(@PathVariable("id") id: Int): ResponseEntity<Any> {
        val deleteService = serviceRepository.findById(id)
        if (deleteService.isPresent) {
            serviceRepository.deleteById(id)
            return ResponseEntity.ok("Successfully deleted Service (id: $id)")
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Service is not found")
    }

    @PostMapping("/route/add")
    fun addServiceRoutes(@RequestBody serviceRoutes: List<ServiceRouteDTO>): ResponseEntity<Any> {
        try {
            if (serviceRoutes.isNotEmpty()) {
                serviceRoutes.forEach { routeDTO ->
                    val route = ServiceRoute(
                        id = 0,
                        route = routeDTO.route,
                        routeDesc = routeDTO.routeDescription,
                        isInternal = routeDTO.isInternal,
                        serviceId =
                        if (routeDTO.isInternal) null
                        else serviceRepository.findByServiceName(routeDTO.serviceName)?.id!!
                    )
                    val roles = roleRepository.findRolesByRoleIn(routeDTO.roles.toList())
                    val filters = filterService.getFiltersFromDTO(routeDTO.filters)
                    val predicates = predicateService.getPredicatesAndSaveNew(routeDTO.predicates)
                    roles.forEach {
                        route.addRole(it)
                    }
                    filters.forEach {
                        route.addFilter(it)
                    }
                    predicates.forEach {
                        route.addPredicate(it)
                    }
                    serviceRouteRepository.save(route)
                }
            }else {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("No service-routes found in request body")
            }
        }catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.message)
        }
        return ResponseEntity.status(HttpStatus.CREATED).body("Successfully added ${serviceRoutes.size} service routes")
    }

    @PutMapping("/route/{id}")
    fun updateServiceRoute(@PathVariable("id") id: Int, @RequestBody serviceRoute: ServiceRouteDTO): ResponseEntity<Any> {
        val current = serviceRouteRepository.findById(id)
        if (current.isPresent) {
            try {
                val updated = current.get().copy(
                    route = serviceRoute.route,
                    routeDesc = serviceRoute.routeDescription,
                    isInternal = serviceRoute.isInternal,
                    serviceId =
                    if (serviceRoute.isInternal) null
                    else serviceRepository.findByServiceName(serviceRoute.serviceName)?.id!!
                )
                updated.apply {
                    roles.clear()
                    filters.clear()
                    predicates.clear()
                }
                val newRoles = roleRepository.findRolesByRoleIn(serviceRoute.roles.toList())
                val newFilters = filterService.getFiltersFromDTO(serviceRoute.filters)
                val newPredicates = predicateService.getPredicatesAndSaveNew(serviceRoute.predicates)
                newRoles.forEach {
                    updated.addRole(it)
                }
                newFilters.forEach {
                    updated.addFilter(it)
                }
                newPredicates.forEach {
                    updated.addPredicate(it)
                }
                serviceRouteRepository.save(updated)
                return ResponseEntity.status(HttpStatus.OK).body("Successfully updated Service-route (id: $id)")
            } catch (ex: Exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

    @DeleteMapping("/route/{id}")
    fun deleteServiceRoute(@PathVariable("id") id: Int): ResponseEntity<Any> {
        val deleteRoute = serviceRouteRepository.findById(id)
        if (deleteRoute.isPresent) {
            serviceRouteRepository.deleteById(id)
            return ResponseEntity.ok("Successfully deleted Service-route (id: $id)")
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Route is not found")
    }

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