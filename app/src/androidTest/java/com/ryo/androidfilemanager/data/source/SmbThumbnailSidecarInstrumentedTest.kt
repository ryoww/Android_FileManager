package com.ryo.androidfilemanager.data.source

import androidx.test.platform.app.InstrumentationRegistry
import com.ryo.androidfilemanager.data.smb.SmbConnectionStore
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class SmbThumbnailSidecarInstrumentedTest {

    @Test
    fun downloadsFreshSidecarFromSavedSmbConnection() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val filePath = InstrumentationRegistry.getArguments().getString("sidecarPath")
            val requireConnection = InstrumentationRegistry.getArguments()
                .getString("requireConnection") == "true"
            assumeTrue(!filePath.isNullOrBlank())

            val context = instrumentation.targetContext
            val connection = SmbConnectionStore(context).savedConnection.first()
            if (requireConnection) {
                assertNotNull("A saved SMB connection is required for this run.", connection)
            }
            assumeNotNull(connection)

            val directory = filePath!!.substringBeforeLast('/', missingDelimiterValue = "")
            val fileName = filePath.substringAfterLast('/')
            val source = SmbFileSource(context, connection!!)
            val file = source.list(directory).first { item -> item.name == fileName }
            val destination = File(context.cacheDir, "sidecar-instrumented-test.jpg")
            destination.delete()

            assertTrue(source.copyFreshThumbnailSidecar(file, destination))
            assertTrue(destination.length() > 0L)
            destination.delete()
        }
    }
}
