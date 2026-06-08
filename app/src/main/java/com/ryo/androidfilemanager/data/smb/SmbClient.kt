package com.ryo.androidfilemanager.data.smb

interface SmbClient {
    suspend fun testConnection(info: SmbConnectionInfo): Result<Unit>
}

