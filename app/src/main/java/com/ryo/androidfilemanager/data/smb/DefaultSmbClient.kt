package com.ryo.androidfilemanager.data.smb

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultSmbClient : SmbClient {
    override suspend fun testConnection(info: SmbConnectionInfo): Result<Unit> = runCatching {
        withDiskShare(info) { share ->
            share.getShareInformation()
        }
    }.map { Unit }
}

suspend fun <T> withDiskShare(
    info: SmbConnectionInfo,
    block: (DiskShare) -> T,
): T = withContext(Dispatchers.IO) {
    SMBClient().use { client ->
        client.connect(info.host, info.port).use { connection ->
            connection.authenticate(info.toAuthenticationContext()).use { session ->
                val share = session.connectShare(info.shareName)
                require(share is DiskShare) {
                    "SMB share '${info.shareName}' is not a disk share."
                }

                share.use { diskShare ->
                    block(diskShare)
                }
            }
        }
    }
}

fun SmbConnectionInfo.toAuthenticationContext(): AuthenticationContext {
    if (username.isNullOrBlank()) {
        return AuthenticationContext.anonymous()
    }

    return AuthenticationContext(
        username,
        password.orEmpty().toCharArray(),
        domain.orEmpty(),
    )
}
