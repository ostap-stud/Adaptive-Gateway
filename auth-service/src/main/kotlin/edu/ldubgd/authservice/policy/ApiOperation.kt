package edu.ldubgd.authservice.policy

data class ApiOperation(
    val id: Long,
    val serviceId: Int,
    val backendPath: String,
    val gatewayPath: String,
    val httpMethod: String,
    val active: Boolean
)
