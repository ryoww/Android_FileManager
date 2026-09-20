package com.ryo.androidfilemanager.core.application.port

import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo

interface SmbClient {
    suspend fun testConnection(info: SmbConnectionInfo): Result<Unit>
}

