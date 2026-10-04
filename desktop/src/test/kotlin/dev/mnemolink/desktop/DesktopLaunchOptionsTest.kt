package dev.mnemolink.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLaunchOptionsTest {
    @Test
    fun normalLaunchDoesNotAutoClose() {
        assertFalse(DesktopLaunchOptions.parse(emptyArray()).smokeTest)
    }

    @Test
    fun smokeLaunchIsExplicit() {
        assertTrue(DesktopLaunchOptions.parse(arrayOf("--smoke-test")).smokeTest)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownArgumentsAreRejected() {
        DesktopLaunchOptions.parse(arrayOf("--save-to-anki"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun smokeDoesNotSilentlyAcceptOtherArguments() {
        DesktopLaunchOptions.parse(arrayOf("--smoke-test", "--unknown"))
    }
}
