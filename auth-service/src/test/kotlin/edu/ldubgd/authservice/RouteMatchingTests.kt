package edu.ldubgd.authservice

import edu.ldubgd.authservice.db.*
import edu.ldubgd.authservice.db.service.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class RouteMatchingTests {
    private val predicates = mock(PredicateService::class.java)
    private val service = RouteServiceImpl(
        mock(ServiceRouteRepository::class.java), mock(ServiceRepository::class.java),
        mock(RoleRepository::class.java), mock(FilterService::class.java), predicates,
        mock(edu.ldubgd.authservice.adaptive.ConfigurationWriteLock::class.java)
    )

    private fun route(id: Int, order: Int? = 0) = ServiceRoute(id, "/test/**", null, 1, false, order)

    @Test
    fun `method matching uses complete tokens and ignores whitespace`() {
        val route = route(1).apply { addPredicate(Predicate(1, "Method")) }
        `when`(predicates.getPredicatesByName("Method"))
            .thenReturn(listOf(Predicate(1, "Method", mapOf("methods" to "GET, POST"))))
        assertEquals(route, service.findRouteByRequestMethod(listOf(route), "post"))
        assertNull(service.findRouteByRequestMethod(listOf(route), "ET"))
        assertNull(service.findRouteByRequestMethod(listOf(route), "DELETE"))
    }

    @Test
    fun `separate Method predicates must all match`() {
        val route = route(1).apply {
            addPredicate(Predicate(1, "Method")); addPredicate(Predicate(2, "Method"))
        }
        `when`(predicates.getPredicatesByName("Method")).thenReturn(listOf(
            Predicate(1, "Method", mapOf("methods" to "GET,POST")),
            Predicate(2, "Method", mapOf("methods" to "POST"))
        ))
        assertNull(service.findRouteByRequestMethod(listOf(route), "GET"))
        assertEquals(route, service.findRouteByRequestMethod(listOf(route), "POST"))
    }

    @Test
    fun `legacy null priority behaves as zero`() {
        `when`(predicates.getPredicatesByName("Method")).thenReturn(emptyList())
        val legacy = route(1, null)
        assertEquals(legacy, service.findRouteByRequestMethod(listOf(route(2, 5), legacy), "GET"))
        val higherPriority = route(3, -1)
        assertEquals(higherPriority, service.findRouteByRequestMethod(listOf(legacy, higherPriority), "GET"))
    }
}
