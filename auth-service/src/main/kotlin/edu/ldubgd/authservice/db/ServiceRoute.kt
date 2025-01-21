package edu.ldubgd.authservice.db

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.MappedCollection
import org.springframework.data.relational.core.mapping.Table

@Table("route")
data class ServiceRoute(
    @Id
    val id: Int,
    val route: String,
    val routeDesc: String?,
    val serviceId: Int,
    @MappedCollection(idColumn = "route_id", keyColumn = "role_id")
    val roles: MutableSet<RouteRoleRef> = HashSet()
){
    fun addRole(role: Role){
        roles.add(createRouteRoleRef(role.id))
    }

    private fun createRouteRoleRef(id: Int): RouteRoleRef {
        return RouteRoleRef(id)
    }

    fun getRolesId(): MutableList<Int> = roles.map { it.roleId }.toMutableList()
}
