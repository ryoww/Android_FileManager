package com.ryo.androidfilemanager.core.domain

data class SmbConnectionInfo(
    val host: String,
    val shareName: String,
    val username: String?,
    val password: String?,
    val domain: String?,
    val port: Int = 445,
)

