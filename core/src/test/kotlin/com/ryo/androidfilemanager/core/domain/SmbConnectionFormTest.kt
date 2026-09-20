package com.ryo.androidfilemanager.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbConnectionFormTest {
    @Test
    fun validInputTrimsRequiredFieldsAndConvertsPort() {
        val form = SmbConnectionForm(
            host = "  192.168.1.1  ",
            port = "445",
            shareName = "  share  ",
            username = "",
            domain = "  ",
            password = "secret",
        )

        val result = form.toConnectionInfo()

        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals("192.168.1.1", info.host)
        assertEquals("share", info.shareName)
        assertEquals(445, info.port)
        assertNull(info.username)
        assertNull(info.domain)
        assertEquals("secret", info.password)
    }

    @Test
    fun blankHostFails() {
        val result = SmbConnectionForm(host = "", shareName = "share").toConnectionInfo()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is InvalidSmbConnectionFormException)
        assertEquals(
            "Host and share name are required. Port must be a valid number.",
            exception?.message,
        )
    }

    @Test
    fun blankShareNameFails() {
        val result = SmbConnectionForm(host = "host", shareName = "").toConnectionInfo()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidSmbConnectionFormException)
    }

    @Test
    fun nonNumericPortFails() {
        val result = SmbConnectionForm(host = "host", shareName = "share", port = "abc").toConnectionInfo()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidSmbConnectionFormException)
    }

    @Test
    fun portZeroFails() {
        val result = SmbConnectionForm(host = "host", shareName = "share", port = "0").toConnectionInfo()

        assertTrue(result.isFailure)
    }

    @Test
    fun portAboveRangeFails() {
        val result = SmbConnectionForm(host = "host", shareName = "share", port = "70000").toConnectionInfo()

        assertTrue(result.isFailure)
    }

    @Test
    fun withPortInputStripsNonDigitsAndFallsBackTo445() {
        assertEquals("445", SmbConnectionForm().withPortInput("4a4b5").port)
        assertEquals("445", SmbConnectionForm().withPortInput("").port)
        assertEquals("139", SmbConnectionForm().withPortInput("139").port)
    }

    @Test
    fun fromInfoMapsFieldsToStringsWithNullAsEmpty() {
        val info = SmbConnectionInfo(
            host = "host",
            shareName = "share",
            username = null,
            password = null,
            domain = null,
            port = 139,
        )

        val form = SmbConnectionForm.from(info)

        assertEquals("host", form.host)
        assertEquals("share", form.shareName)
        assertEquals("139", form.port)
        assertEquals("", form.username)
        assertEquals("", form.domain)
        assertEquals("", form.password)
    }
}
