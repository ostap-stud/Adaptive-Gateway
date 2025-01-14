package edu.ldubgd.authservice.security.requests

data class LogInRequest (
    val login: String,
    val password: String
)