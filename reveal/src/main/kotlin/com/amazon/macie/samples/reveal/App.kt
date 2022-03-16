// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal

import com.fasterxml.jackson.databind.ObjectMapper
import picocli.CommandLine
import java.util.concurrent.Callable
import kotlin.system.exitProcess

@CommandLine.Command(
    name = "reveal", mixinStandardHelpOptions = true, version = ["reveal 1.0"],
    description = ["Reveals sensitive data in objects to help validate findings."],
)
class App(private val revealer: Revealer = Reveal()) : Callable<Int> {
    @CommandLine.Parameters(index = "0", paramLabel = "ID", description = ["ID of the finding."])
    lateinit var finding: String

    @CommandLine.Parameters(
        index = "1..*",
        paramLabel = "Entities",
        description = ["Reveal specified entities only."]
    )
    var entities: Set<String> = setOf()

    @CommandLine.Option(names = ["-j", "--json"], description = ["Reveal as JSON"])
    var json = false

    @CommandLine.Option(names = ["-l", "--limit"], description = ["Reveal no more than given values for each entity"])
    var limit = 5

    override fun call(): Int {
        try {
            val query = RevealQuery(finding, entities, limit)
            val revealed = revealer.revealFinding(query).block()!!
            if (json) {
                println(ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(revealed))
            } else {
                Display.render(revealed)
            }
        } catch (e: Exception) {
            println("ERROR: ${e.message}")
            return 1
        }
        return 0
    }
}

fun main(args: Array<String>): Unit = exitProcess(CommandLine(App()).execute(*args))
