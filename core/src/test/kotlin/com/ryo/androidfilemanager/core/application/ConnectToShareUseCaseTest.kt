package com.ryo.androidfilemanager.core.application

import com.ryo.androidfilemanager.core.application.port.SmbConnectionRepository

import app.cash.turbine.test
import com.ryo.androidfilemanager.core.application.port.SmbClient
import com.ryo.androidfilemanager.core.domain.InvalidSmbConnectionFormException
import com.ryo.androidfilemanager.core.domain.SmbConnectionForm
import com.ryo.androidfilemanager.core.domain.SmbConnectionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSmbClient(private val result: Result<Unit>) : SmbClient {
    var callCount = 0
        private set

    override suspend fun testConnection(info: SmbConnectionInfo): Result<Unit> {
        callCount++
        return result
    }
}

private class InMemorySmbConnectionRepository : SmbConnectionRepository {
    private val state = MutableStateFlow<SmbConnectionInfo?>(null)
    override val savedConnection: Flow<SmbConnectionInfo?> = state

    override suspend fun save(info: SmbConnectionInfo) {
        state.value = info
    }

    override suspend fun clear() {
        state.value = null
    }
}

class ConnectToShareUseCaseTest {
    private val validForm = SmbConnectionForm(host = "host", port = "445", shareName = "share")

    @Test
    fun invalidFormFailsWithoutCallingClientOrSaving() = runTest {
        val client = FakeSmbClient(Result.success(Unit))
        val repository = InMemorySmbConnectionRepository()
        val useCase = ConnectToShareUseCase(client, repository)

        val result = useCase.test(SmbConnectionForm(host = "", shareName = ""))

        assertTrue(result.exceptionOrNull() is InvalidSmbConnectionFormException)
        assertEquals(0, client.callCount)
        repository.savedConnection.test {
            assertNull(expectMostRecentItem())
        }
    }

    @Test
    fun successfulConnectionSavesConnection() = runTest {
        val client = FakeSmbClient(Result.success(Unit))
        val repository = InMemorySmbConnectionRepository()
        val useCase = ConnectToShareUseCase(client, repository)

        val result = useCase.test(validForm)

        assertTrue(result.isSuccess)
        repository.savedConnection.test {
            assertEquals("host", expectMostRecentItem()?.host)
        }
    }

    @Test
    fun failedConnectionPropagatesFailureWithoutSaving() = runTest {
        val client = FakeSmbClient(Result.failure(RuntimeException("unreachable")))
        val repository = InMemorySmbConnectionRepository()
        val useCase = ConnectToShareUseCase(client, repository)

        val result = useCase.test(validForm)

        assertTrue(result.isFailure)
        repository.savedConnection.test {
            assertNull(expectMostRecentItem())
        }
    }
}
