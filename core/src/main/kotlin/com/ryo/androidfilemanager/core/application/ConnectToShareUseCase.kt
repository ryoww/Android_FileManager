package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.application.port.SmbConnectionRepository

import com.ryo.androidfilemanager.core.application.port.SmbClient
import com.ryo.androidfilemanager.core.domain.SmbConnectionForm
import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo

/** SMB 共有への接続をテストし、成功したときのみ接続情報を保存するユースケース */
class ConnectToShareUseCase(
    private val smbClient: SmbClient,
    private val repository: SmbConnectionRepository,
) {
    suspend fun test(form: SmbConnectionForm): Result<SmbConnectionInfo> {
        val info = form.toConnectionInfo().getOrElse { return Result.failure(it) }

        return smbClient.testConnection(info)
            .onSuccess { repository.save(info) }
            .map { info }
    }
}
