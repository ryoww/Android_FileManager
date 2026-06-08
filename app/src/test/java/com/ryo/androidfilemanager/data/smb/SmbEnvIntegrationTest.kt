package com.ryo.androidfilemanager.data.smb

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

class SmbEnvIntegrationTest {
    @Test
    fun envSmbShareCanBeListed() {
        runBlocking {
            assumeTrue(
                "Set -DandroidFileManager.smbIntegration=true or ANDROID_FILE_MANAGER_SMB_INTEGRATION=true to run the SMB integration test.",
                System.getProperty("androidFileManager.smbIntegration") == "true" ||
                    System.getenv("ANDROID_FILE_MANAGER_SMB_INTEGRATION") == "true",
            )

            val env = loadDotEnv()
            val address = env.requireValue("ADDRESS")
            val parsedAddress = parseSmbAddress(address)
            val info = SmbConnectionInfo(
                host = parsedAddress.host,
                shareName = parsedAddress.shareName,
                username = env["USERNAME"].blankToNull(),
                password = env["PASSWORD"].blankToNull(),
                domain = env["DOMAIN"].blankToNull(),
                port = env["PORT"]?.toIntOrNull() ?: 445,
            )

            runCatching {
                withDiskShare(info) { share ->
                    share.getShareInformation()
                    share.list(parsedAddress.path.toSmbPath()).size
                }
            }.onFailure { throwable ->
                throw AssertionError("SMB .env connection failed: ${throwable.safeMessage(info)}")
            }
        }
    }

    private fun loadDotEnv(): Map<String, String> {
        val envFile = generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .map { File(it, ".env") }
            .firstOrNull { it.isFile }
            ?: error(".env was not found from the Gradle working directory.")

        return envFile.readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    null
                } else {
                    val key = trimmed.substringBefore("=").trim()
                    val value = trimmed.substringAfter("=").trim().trim('"', '\'')
                    key to value
                }
            }
            .toMap()
    }

    private fun parseSmbAddress(address: String): ParsedSmbAddress {
        val normalized = address
            .trim()
            .removePrefix("smb://")
            .removePrefix("file://")
            .removePrefix("\\\\")
            .removePrefix("//")
            .replace('\\', '/')

        val segments = normalized.split('/').filter { it.isNotBlank() }
        require(segments.size >= 2) {
            "ADDRESS must include host and share, for example smb://host/share."
        }

        return ParsedSmbAddress(
            host = segments[0],
            shareName = segments[1],
            path = segments.drop(2).joinToString("/"),
        )
    }

    private fun Map<String, String>.requireValue(key: String): String = requireNotNull(this[key].blankToNull()) {
        "$key is required in .env."
    }

    private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }

    private fun String.toSmbPath(): String = replace('/', '\\')

    private fun Throwable.safeMessage(info: SmbConnectionInfo): String {
        val secrets = listOfNotNull(
            info.host,
            info.shareName,
            info.username,
            info.password,
            info.domain,
        ).filter { it.isNotBlank() }

        return (message ?: javaClass.simpleName ?: javaClass.name).let { rawMessage ->
            secrets.fold(rawMessage) { sanitized, secret ->
                sanitized.replace(secret, "<redacted>")
            }
        }
    }

    private data class ParsedSmbAddress(
        val host: String,
        val shareName: String,
        val path: String,
    )
}
