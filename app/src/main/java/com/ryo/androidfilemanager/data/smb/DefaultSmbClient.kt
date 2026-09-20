package com.ryo.androidfilemanager.data.smb

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.ryo.androidfilemanager.core.application.port.SmbClient
import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * タイムアウト付きの SMBClient を生成する。smbj のデフォルトはソケット読み取りが
 * 無期限待ち(soTimeout=0)のため、NAS 側の応答停止でサムネイルワーカー等の
 * スレッドが永久にブロックされるのを防ぐ。
 */
fun newSmbClient(): SMBClient = SMBClient(
    SmbConfig.builder()
        .withTimeout(SMB_REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .withSoTimeout(SMB_SOCKET_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
)

private const val SMB_REQUEST_TIMEOUT_SECONDS = 20L
private const val SMB_SOCKET_TIMEOUT_SECONDS = 60L

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
    newSmbClient().use { client ->
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
