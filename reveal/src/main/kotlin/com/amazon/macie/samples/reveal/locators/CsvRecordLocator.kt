// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import mu.KotlinLogging
import software.amazon.awssdk.services.macie2.model.Occurrences

/**
 * Read from CSV/TSV files.
 */
object CsvRecordLocator : Locator {
    private val logger = KotlinLogging.logger {}

    override fun locate(
        content: ByteArray,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        require(occurrences.values.stream().anyMatch { it.hasCells() }) { "Internal: invalid locator picked" }
        val data = String(content, Charsets.UTF_8)
        return occurrences.mapValues { (_, occurrence) ->
            val values = mutableListOf<String>()
            for (it in occurrence.cells().take(query.limit)) {
                try {
                    data.lines().stream().skip(it.row() - 1).limit(1)
                        .flatMap { it.split(",", "\t").stream() }
                        .skip(it.column() - 1).limit(1)
                        .forEach { values.add(it) }
                } catch (e: Exception) {
                    logger.warn { "invalid line reference $it" }
                }
            }
            values
        }
    }
}
