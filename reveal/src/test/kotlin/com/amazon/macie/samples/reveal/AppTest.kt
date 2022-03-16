// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal

import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import picocli.CommandLine
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

class AppTest {
    private val reveal: Revealer = mockk()
    private val revelation = Revelation("s3://foo/bar", "plain/text", mapOf("whatever" to listOf("a")))

    @Test
    fun isHelpful() {
        val exitCode = CommandLine(App()).execute("-h")
        assertEquals(0, exitCode)
    }

    @Test
    fun backedErrorsReportedViaExitCode() {
        every { reveal.revealFinding(RevealQuery("no-such-finding", setOf(), 5)) } throws IllegalStateException("fail")
        val exit = CommandLine(App(reveal)).execute("no-such-finding")
        assertEquals(exit, 1)
    }

    @Test
    fun findingsAreRevealed() {
        every { reveal.revealFinding(RevealQuery("guid", setOf(), 5)) } returns Mono.just(revelation)
        val exit = CommandLine(App(reveal)).execute("guid")
        assertEquals(exit, 0)
    }

    @Test
    fun findingsCanBeRevealedAsJson() {
        every { reveal.revealFinding(RevealQuery("guid", setOf(), 5)) } returns Mono.just(revelation)
        val exit = CommandLine(App(reveal)).execute("guid", "-j")
        assertEquals(exit, 0)
    }

    @Test
    fun revealedEntitiesCanBeLimited() {
        every { reveal.revealFinding(RevealQuery("guid", setOf(), 2)) } returns Mono.just(revelation)
        val exit = CommandLine(App(reveal)).execute("guid", "-l", "2")
        assertEquals(exit, 0)
    }
}
