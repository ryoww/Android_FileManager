package com.ryo.androidfilemanager.data.smb

data class SmbConnectionInfo(
    val host: String,
    val shareName: String,
    val username: String?,
    val password: String?,
    val domain: String?,
    val port: Int = 445,
)

