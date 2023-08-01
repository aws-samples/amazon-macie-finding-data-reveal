// locators/GzipRecordLocator.kt
// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import mu.KotlinLogging
import software.amazon.awssdk.services.macie2.model.Occurrences
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

object GzipRecordLocator : Locator {
    private val logger = KotlinLogging.logger {}

    override fun locate(
        content: ByteArray,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        require(occurrences.values.stream().anyMatch { it.hasRecords() }) { "Internal: invalid locator picked" }

        val gzipInputStream = GZIPInputStream(ByteArrayInputStream(content))
        val uncompressedContent = gzipInputStream.readBytes()

        // Delegate the processing to another locator based on the content's structure
        // For example, you could delegate to the JsonRecordLocator if you know the GZIP content is JSON
        return JsonRecordLocator.locate(uncompressedContent, occurrences, query)
    }
}
