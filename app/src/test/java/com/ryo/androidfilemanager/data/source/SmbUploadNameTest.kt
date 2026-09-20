package com.ryo.androidfilemanager.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbUploadNameTest {
    @Test
    fun reservesSuffixWhenTargetNameAlreadyExists() {
        val reservedNames = mutableSetOf("report.pdf")

        val name = reserveSmbUploadFileName("report.pdf", reservedNames)

        assertEquals("report (2).pdf", name)
        assertEquals(setOf("report.pdf", "report (2).pdf"), reservedNames)
    }

    @Test
    fun preservesUnicodeAndReplacesSmbReservedCharacters() {
        val name = reserveSmbUploadFileName("travel:旅行?.mp4", mutableSetOf())

        assertEquals("travel_旅行_.mp4", name)
    }

    @Test
    fun fallsBackToUploadForAnEmptyUsableName() {
        val name = reserveSmbUploadFileName("...", mutableSetOf())

        assertEquals("upload", name)
    }
}
