// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import mu.KotlinLogging
import software.amazon.awssdk.services.macie2.model.Occurrences
import java.nio.charset.StandardCharsets

/**
 * Locate sensitive data in JSON text.
 */
object JsonRecordLocator : Locator {
    private val logger = KotlinLogging.logger {}

    /**
     * Locate occurrences by resolving the JSON path.
     */
    override fun locate(
        content: ByteArray,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        require(occurrences.values.stream().anyMatch { it.hasRecords() }) { "Internal: invalid locator picked" }
        // JSON comes in two varieties, document or lines. If the file is read as a document, all record indices are 0
        val lines = !occurrences.values.stream().flatMap { it.records().stream() }.allMatch { it.recordIndex() == 0L }
        val text = String(content, StandardCharsets.UTF_8)
        return if (lines) jsonLines(text, occurrences, query) else jsonDocument(text, occurrences, query)
    }

    private fun jsonLines(
        text: String,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        val lines = text.lines()
        return occurrences.mapValues { (_, occurrence) ->
            val values = mutableListOf<String>()
            for (record in occurrence.records().take(query.limit)) {
                val ctx = JsonPath.parse(lines[record.recordIndex().toInt()])
                resolve(ctx, record.jsonPath())?.let { values.add(it.take(query.truncate)) }
            }
            values
        }
    }

    private fun jsonDocument(
        text: String,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        val ctx = JsonPath.parse(text)
        return occurrences.mapValues { (_, occurrence) ->
            val values = mutableListOf<String>()
            for (record in occurrence.records().take(query.limit)) {
                resolve(ctx, record.jsonPath())?.let { values.add(it.take(query.truncate)) }
            }
            values
        }
    }

    private fun resolve(ctx: DocumentContext, path: String): String? {
        try {
            return ctx.read<Any>(path).toString()
        } catch (e: Exception) {
            logger.warn { "invalid record reference $path" }
        }
        return null
    }
}
