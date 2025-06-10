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
    val serviceId: Int?,
    val isInternal: Boolean,
    val order: Int?,
    @MappedCollection(idColumn = "route_id", keyColumn = "role_id")
    val roles: MutableSet<RouteRoleRef> = HashSet(),
    @MappedCollection(idColumn = "route_id", keyColumn = "filter_id")
    val filters: MutableSet<RouteFilterRef> = HashSet(),
    @MappedCollection(idColumn = "route_id", keyColumn = "predicate_id")
    val predicates: MutableSet<RoutePredicateRef> = HashSet(),
){
    fun addRole(role: Role){
        roles.add(createRouteRoleRef(role.id))
    }

    fun removeRole(role: Role){
        roles.remove(createRouteRoleRef(role.id))
    }

    private fun createRouteRoleRef(id: Int): RouteRoleRef {
        return RouteRoleRef(id)
    }

    fun getRolesId(): MutableList<Int> = roles.map { it.roleId }.toMutableList()

    fun addFilter(filter: Filter){
        filters.add(createRouteFilterRef(filter.id!!))
    }

    fun removeFilter(filter: Filter) {
        filters.remove(filter.id?.let { createRouteFilterRef(it) })
    }

    private fun createRouteFilterRef(id: Int): RouteFilterRef {
        return RouteFilterRef(id)
    }

    fun addPredicate(predicate: Predicate){
        predicates.add(createRoutePredicateRef(predicate.id!!))
    }

    fun removePredicate(predicate: Predicate){
        predicates.remove(predicate.id?.let { createRoutePredicateRef(it) })
    }

    private fun createRoutePredicateRef(id: Int): RoutePredicateRef {
        return RoutePredicateRef(id)
    }

}
