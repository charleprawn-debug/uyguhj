package com.proxyplatform.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbShellCommandTest {
    @Test
    fun wrapsCommandsWithRemoteExitStatusMarker() {
        val command = AdbShellCommand.wrap("settings put global http_proxy '127.0.0.1:10808'")
        assertTrue(command.startsWith("settings put global http_proxy '127.0.0.1:10808';"))
        assertTrue(command.contains("__proxy_platform_status=\$?"))
        assertTrue(command.contains(AdbShellCommand.EXIT_MARKER))
    }

    @Test
    fun parsesNormalOutputAndSuccessfulExit() {
        val parsed = AdbShellCommand.parse("Deleted 1 rows\n${AdbShellCommand.EXIT_MARKER}:0\n")
        assertEquals("Deleted 1 rows", parsed.output)
        assertEquals(0, parsed.exitCode)
    }

    @Test
    fun acceptsSuccessfulCommandWithNoOutput() {
        val parsed = AdbShellCommand.parse("\n${AdbShellCommand.EXIT_MARKER}:0\n")
        assertEquals("", parsed.output)
        assertEquals(0, parsed.exitCode)
    }

    @Test
    fun retainsNonzeroExitCodeForCallerToReject() {
        val parsed = AdbShellCommand.parse("permission denied\n${AdbShellCommand.EXIT_MARKER}:1\n")
        assertEquals("permission denied", parsed.output)
        assertEquals(1, parsed.exitCode)
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsAStreamWithoutTheRemoteCompletionMarker() {
        AdbShellCommand.parse("Stream closed.")
    }
}
