// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import mu.KotlinLogging
import software.amazon.awssdk.services.macie2.model.Occurrences

/**
 * Locate sensitive data in files that generate line ranges.
 */
object LineRangeRecordLocator : Locator {
    private val logger = KotlinLogging.logger {}

    override fun locate(
        content: ByteArray,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        require(occurrences.values.stream().anyMatch { it.hasLineRanges() }) { "Internal: invalid locator picked" }
        val lines = String(content, Charsets.UTF_8).lines()
        return occurrences.mapValues { (_, occurrence) ->
            val values = mutableListOf<String>()
            for (l in occurrence.lineRanges().take(query.limit)) {
                try {
                    val text = lines.subList(l.start().toInt() - 1, l.end().toInt()).joinToString(" ")
                    val end = text.length.coerceAtMost(l.startColumn().toInt() - 1 + query.truncate)
                    values.add(text.substring(l.startColumn().toInt() - 1, end))
                } catch (e: Exception) {
                    logger.warn { "invalid line reference $l" }
                }
            }
            values
        }
    }
}
