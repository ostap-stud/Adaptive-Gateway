package edu.ldubgd.adaptivemanager

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OpenApiAnalyzerTests {
    @Test fun `x-access permissions are declarations not service-defined roles and enter the semantic hash`() {
        val document=doc()
        operation(document).set<com.fasterxml.jackson.databind.JsonNode>("x-access",json.readTree("""{"type":"PERMISSION","permissions":["orders.read","orders.export"]}"""))
        val first=analyzer.analyze(target,document).candidate
        assertEquals(setOf("orders.read","orders.export"),first.operations.single().permissions)
        assertTrue(first.operations.single().roles.isEmpty())
        operation(document).set<com.fasterxml.jackson.databind.JsonNode>("x-access",json.readTree("""{"type":"PERMISSION","permissions":["orders.read"]}"""))
        assertNotEquals(first.specHash,analyzer.analyze(target,document).candidate.specHash)
    }
    @Test fun `x-access public does not weaken explicit JWT and internal never becomes public`() {
        val document=doc()
        operation(document).set<com.fasterxml.jackson.databind.JsonNode>("x-access",json.readTree("""{"type":"PUBLIC"}"""))
        assertEquals(AccessType.AUTHENTICATED,access(document))
        document.remove("security")
        assertEquals(AccessType.PUBLIC,access(document))
        for(value in listOf("""{"type":"INTERNAL"}""","""{"type":"PERMISSION","permissions":[]}""","""{"type":"ROLES","roles":["ADMIN"]}""")) {
            operation(document).set<com.fasterxml.jackson.databind.JsonNode>("x-access",json.readTree(value))
            assertEquals(AccessType.BLOCKED,access(document))
        }
    }
    private val json = jacksonObjectMapper()
    private val builder = DesiredConfigurationBuilder(json)
    private val analyzer = OpenApiAnalyzer(builder)
    private val target = ServiceTarget("demo", "one", "http://localhost:8996", "/demo/v3/api-docs")
    private fun doc(): ObjectNode = json.readTree("""{
      "openapi":"3.0.3","info":{"title":"Demo","version":"1"},
      "servers":[{"url":"https://localhost:8443"}],
      "components":{"securitySchemes":{
        "jwt":{"type":"http","scheme":"bearer","bearerFormat":"JWT"},
        "key":{"type":"apiKey","in":"header","name":"X-Key"},
        "oauth":{"type":"oauth2","flows":{}}
      }},
      "security":[{"jwt":[]}],
      "paths":{"/demo/item":{"get":{"operationId":"getItem","responses":{"200":{"description":"ok"}}}}}
    }""") as ObjectNode
    private fun operation(doc: ObjectNode) = doc.path("paths").path("/demo/item").path("get") as ObjectNode
    private fun access(doc: ObjectNode) = analyzer.analyze(target,doc).candidate.operations.single().access
    private fun security(doc: ObjectNode, value: String) { operation(doc).set<com.fasterxml.jackson.databind.JsonNode>("security",json.readTree(value)) }

    @Test fun `global JWT is inherited and absent declaration uses conservative default`() {
        val document = doc()
        assertEquals(AccessType.AUTHENTICATED,access(document))
        document.remove("security")
        val result = analyzer.analyze(target,document)
        assertEquals(AccessType.AUTHENTICATED,result.candidate.operations.single().access)
        assertTrue(result.warnings.single().contains("default"))
    }
    @Test fun `operation declaration overrides root and empty declarations allow public access`() {
        val document = doc()
        for (value in listOf("[]","[{}]","[{}, {\"key\":[]}]")) {
            security(document,value)
            assertEquals(AccessType.PUBLIC,access(document))
        }
        document.set<com.fasterxml.jackson.databind.JsonNode>("security", json.readTree("[]"))
        security(document,"[{\"jwt\":[]}]")
        assertEquals(AccessType.AUTHENTICATED,access(document))
    }
    @Test fun `OR alternatives and AND combinations are not confused`() {
        val document = doc()
        security(document,"[{\"key\":[]},{\"jwt\":[]}]")
        assertEquals(AccessType.AUTHENTICATED,access(document))
        security(document,"[{\"key\":[],\"jwt\":[]}]")
        assertEquals(AccessType.BLOCKED,access(document))
    }
    @Test fun `unsupported security and malformed requirements never become public`() {
        val document = doc()
        for (value in listOf("null","{}","[null]","[{\"jwt\":null}]","[{\"missing\":[]}]",
            "[{\"oauth\":[\"items:read\"]}]","[{\"jwt\":[\"ROLE_USER\"]}]","[{\"key\":[]}]")) {
            security(document,value)
            assertEquals(AccessType.BLOCKED,access(document),value)
        }
        security(document,"[{\"jwt\":[]}]")
        (document.path("components").path("securitySchemes").path("jwt") as ObjectNode)
            .put("\$ref","https://untrusted.invalid/scheme")
        assertEquals(AccessType.BLOCKED,access(document))
    }
    @Test fun `access extensions require explicit mapping even if security is empty`() {
        val document = doc()
        security(document,"[]")
        operation(document).put("x-permissions","item:read")
        assertEquals(AccessType.BLOCKED,access(document))
    }
    @Test fun `semantic hash ignores documentation formatting and endpoint address`() {
        val original = doc()
        val initial = analyzer.analyze(target,original).candidate
        operation(original).put("summary","Changed documentation")
        (original.path("info") as ObjectNode).put("version","2")
        val changedHost = target.copy(origin="http://other-host:1234", instanceId="two")
        assertEquals(initial.specHash,analyzer.analyze(changedHost,original).candidate.specHash)
        security(original,"[]")
        assertNotEquals(initial.specHash,analyzer.analyze(target,original).candidate.specHash)
        assertEquals(builder.preview(initial,3),builder.preview(initial,3))
        assertNotEquals(builder.preview(initial,3).requestId,builder.preview(initial,4).requestId)
    }
    @Test fun `path prefixes are preserved and effective operation order is canonical`() {
        val document = doc()
        val paths = document.path("paths") as ObjectNode
        paths.set<com.fasterxml.jackson.databind.JsonNode>("/demo/another",paths.path("/demo/item").deepCopy())
        val initial = analyzer.analyze(target,document).candidate
        assertEquals(listOf("/demo/another","/demo/item"),initial.operations.map { it.gatewayPath })
        assertTrue(initial.operations.all { it.backendPath == it.gatewayPath })
        assertEquals("/demo/**",initial.route.path)
        val reversed = json.createObjectNode()
        paths.fields().asSequence().toList().reversed().forEach { reversed.set<com.fasterxml.jackson.databind.JsonNode>(it.key,it.value) }
        document.set<com.fasterxml.jackson.databind.JsonNode>("paths",reversed)
        assertEquals(initial.specHash,analyzer.analyze(target,document).candidate.specHash)
    }
    @Test fun `valid empty paths produce a snapshot removing the last operation`() {
        val document = doc().apply { set<ObjectNode>("paths",json.createObjectNode()) }
        val analysis = analyzer.analyze(target,document)
        assertTrue(analysis.candidate.operations.isEmpty())
        assertTrue(analysis.warnings.isNotEmpty())
        assertEquals("/demo/**",analysis.candidate.route.path)
    }
    @Test fun `invalid missing and referenced paths never produce a removal snapshot`() {
        val variants = listOf(
            doc().apply { put("openapi","2.0") },
            doc().apply { remove("paths") },
            doc().apply { (path("paths") as ObjectNode).set<ObjectNode>("/demo/ref",json.createObjectNode().put("\$ref","https://other/spec")) },
            doc().apply { (path("paths") as ObjectNode).set<com.fasterxml.jackson.databind.JsonNode>("/outside",path("paths").path("/demo/item")) },
            doc().apply { (path("paths") as ObjectNode).set<com.fasterxml.jackson.databind.JsonNode>("/demo/**",path("paths").path("/demo/item")) }
        )
        variants.forEach { assertThrows(ObservationFailure::class.java) { analyzer.analyze(target,it) } }
    }
    @Test fun `server base path and equivalent parameter templates are rejected`() {
        val document = doc()
        (document.path("servers")[0] as ObjectNode).put("url","https://localhost/base")
        assertThrows(ObservationFailure::class.java) { analyzer.analyze(target,document) }
        val other = doc()
        val paths = other.path("paths") as ObjectNode
        val item = paths.path("/demo/item")
        paths.set<com.fasterxml.jackson.databind.JsonNode>("/demo/{id}",item)
        paths.set<com.fasterxml.jackson.databind.JsonNode>("/demo/{other}",item)
        assertEquals("AMBIGUOUS_OPERATIONS",assertThrows(ObservationFailure::class.java) { analyzer.analyze(target,other) }.code)
    }
    @Test fun `OpenAPI 31 nonempty role requirements stay blocked without a mapping`() {
        val document = doc().put("openapi","3.1.0")
        security(document,"[{\"jwt\":[\"user\"]}]")
        assertEquals(AccessType.BLOCKED,access(document))
    }
}
