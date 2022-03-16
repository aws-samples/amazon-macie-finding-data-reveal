// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import com.jayway.jsonpath.JsonPath
import mu.KotlinLogging
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.FileReader
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import software.amazon.awssdk.services.macie2.model.Occurrences

/**
 * Locate reported sensitive data in Avro files.
 */
object AvroRecordLocator : Locator {
    private val logger = KotlinLogging.logger {}

    /**
     * Macie reports Avro occurrences with a record index and a JSON path. We first translate the bytes to a record
     * array and then index into the offset and apply the provided JSON path.
     */
    override fun locate(
        content: ByteArray,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        require(occurrences.values.stream().anyMatch { it.hasRecords() }) { "Internal: invalid locator picked" }

        // Read the records into an array
        val reader: FileReader<GenericRecord> =
            DataFileReader.openReader(SeekableByteArrayInput(content), GenericDatumReader())
        val records = reader.mapTo(mutableListOf()) { it.toString() }

        // Resolve the record JSON paths and collect them into a map.
        return occurrences.mapValues { (_, occurrence) ->
            val values = mutableListOf<String>()
            for (record in occurrence.records().take(query.limit)) {
                try {
                    val ctx = JsonPath.parse(records[record.recordIndex().toInt()])
                    values.add(ctx.read<String?>(record.jsonPath()).take(query.truncate))
                } catch (e: Exception) {
                    logger.warn { "invalid record reference $record" }
                }
            }
            values
        }
    }
}
