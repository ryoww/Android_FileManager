package com.ryo.androidfilemanager.core.domain

/** SMB 接続フォームの入力値バリデーション時に投げる例外 */
class InvalidSmbConnectionFormException(message: String) : IllegalArgumentException(message)

/** 接続フォームの生入力（すべて String）。バリデーション前は自由に空値・不正値を保持できる。 */
data class SmbConnectionForm(
    val host: String = "",
    val port: String = "445",
    val shareName: String = "",
    val username: String = "",
    val domain: String = "",
    val password: String = "",
) {
    // 数字以外の入力を弾く（テンキー入力の誤タップ対策）。空になったら既定値に戻す
    fun withPortInput(raw: String): SmbConnectionForm {
        val digitsOnly = raw.filter { it.isDigit() }
        return copy(port = digitsOnly.ifEmpty { "445" })
    }

    fun toConnectionInfo(): Result<SmbConnectionInfo> {
        val trimmedHost = host.trim()
        val trimmedShareName = shareName.trim()
        val portNumber = port.toIntOrNull()

        if (trimmedHost.isEmpty() || trimmedShareName.isEmpty() || portNumber == null || portNumber !in 1..65535) {
            return Result.failure(
                InvalidSmbConnectionFormException(
                    "Host and share name are required. Port must be a valid number.",
                ),
            )
        }

        return Result.success(
            SmbConnectionInfo(
                host = trimmedHost,
                shareName = trimmedShareName,
                // パスワードは前後の空白も値の一部なので trim しない。空白だけなら未指定扱い
                username = username.takeIf { it.isNotBlank() },
                password = password.takeIf { it.isNotBlank() },
                domain = domain.takeIf { it.isNotBlank() },
                port = portNumber,
            ),
        )
    }

    companion object {
        // null は空文字として表示する（フォームは常に String を扱う）
        fun from(info: SmbConnectionInfo): SmbConnectionForm = SmbConnectionForm(
            host = info.host,
            port = info.port.toString(),
            shareName = info.shareName,
            username = info.username ?: "",
            domain = info.domain ?: "",
            password = info.password ?: "",
        )
    }
}
