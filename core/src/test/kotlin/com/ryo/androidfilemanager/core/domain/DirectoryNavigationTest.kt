package com.ryo.androidfilemanager.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryNavigationTest {
    @Test
    fun rootHasCurrentPathAndCannotNavigateUp() {
        val navigation = DirectoryNavigation.root("/storage")

        assertEquals("/storage", navigation.currentPath)
        assertFalse(navigation.canNavigateUp)
    }

    @Test
    fun enterAppendsToStackAndAllowsNavigatingUp() {
        val navigation = DirectoryNavigation.root("/storage").enter("/storage/photos")

        assertEquals("/storage/photos", navigation.currentPath)
        assertTrue(navigation.canNavigateUp)
    }

    @Test
    fun upRemovesLastSegment() {
        val navigation = DirectoryNavigation.root("/storage").enter("/storage/photos")

        val result = navigation.up()

        assertEquals("/storage", result.currentPath)
    }

    @Test
    fun upAtRootReturnsSameInstance() {
        val navigation = DirectoryNavigation.root("/storage")

        val result = navigation.up()

        assertSame(navigation, result)
    }

    @Test
    fun emptyNavigationHasNoCurrentPath() {
        assertNull(DirectoryNavigation.Empty.currentPath)
        assertFalse(DirectoryNavigation.Empty.canNavigateUp)
    }
}
