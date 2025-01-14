package edu.ldubgd.authservice.security.requests

data class SignUpRequest(
    val login: String,
    val password: String,
    val contactId: Int,
    val roles: List<String>
)
