package com.muze.cupsprinter.model

data class CupsServerConfig(
    val ip: String,
    val port: Int,
    val username: String?,
    val password: String?
)
