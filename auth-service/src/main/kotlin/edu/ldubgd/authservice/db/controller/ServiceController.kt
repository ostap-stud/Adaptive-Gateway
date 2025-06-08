package edu.ldubgd.authservice.db.controller

import edu.ldubgd.authservice.db.*
import edu.ldubgd.authservice.db.dto.ServiceDTO
import edu.ldubgd.authservice.db.dto.ServiceRouteDTO
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

    @GetMapping("/")
    fun getServices(): ResponseEntity<Any> {
        return ResponseEntity(serviceRepository.findAll(), HttpStatus.OK)
    }

    @GetMapping("/{name}")
    fun getService(@PathVariable("name") name: String): ResponseEntity<Any> {
        serviceRepository.findByServiceName(name)?.let {
            return ResponseEntity(it, HttpStatus.OK)
        }
        return ResponseEntity.notFound().build()
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
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found")
    }

    @DeleteMapping("/{id}")
    fun deleteService(@PathVariable("id") id: Int): ResponseEntity<Any> {
        val deleteService = serviceRepository.findById(id)
        if (deleteService.isPresent) {
            serviceRepository.deleteById(id)
            return ResponseEntity.ok("Successfully deleted Service (id: $id)")
        }
        return ResponseEntity.notFound().build()
    }

    @PostMapping("/route/add")
    fun addServiceRoutes(@RequestBody serviceRoutes: List<ServiceRouteDTO>): ResponseEntity<Any> {
        try {
            if (serviceRoutes.isNotEmpty()) {
                serviceRoutes.forEach { routeDTO ->
                    val roles = roleRepository.findRolesByRoleIn(routeDTO.roles.toList())
                    val route = ServiceRoute(
                        id = 0,
                        route = routeDTO.route,
                        routeDesc = routeDTO.routeDescription,
                        isInternal = routeDTO.isInternal,
                        serviceId =
                        if (routeDTO.isInternal) null
                        else serviceRepository.findByServiceName(routeDTO.serviceName)?.id!!
                    )
                    roles.forEach {
                        route.addRole(it)
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
                val newRoles = roleRepository.findRolesByRoleIn(serviceRoute.roles.toList())
                val updated = current.get().copy(
                    route = serviceRoute.route,
                    routeDesc = serviceRoute.routeDescription,
                    isInternal = serviceRoute.isInternal,
                    serviceId =
                    if (serviceRoute.isInternal) null
                    else serviceRepository.findByServiceName(serviceRoute.serviceName)?.id!!
                )
                updated.roles.clear()
                newRoles.forEach {
                    updated.addRole(it)
                }
                serviceRouteRepository.save(updated)
                return ResponseEntity.status(HttpStatus.OK).body("Successfully updated Service-route (id: $id)")
            } catch (ex: Exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found")
    }

    @DeleteMapping("/route/{id}")
    fun deleteServiceRoute(@PathVariable("id") id: Int): ResponseEntity<Any> {
        val deleteRoute = serviceRouteRepository.findById(id)
        if (deleteRoute.isPresent) {
            serviceRouteRepository.deleteById(id)
            return ResponseEntity.ok("Successfully deleted Service-route (id: $id)")
        }
        return ResponseEntity.notFound().build()
    }

}