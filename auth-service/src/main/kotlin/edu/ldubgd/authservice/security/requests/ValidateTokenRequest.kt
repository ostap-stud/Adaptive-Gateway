package edu.ldubgd.authservice.security.requests

data class ValidateTokenRequest(
    val routePath: String,
    val method: String
)
