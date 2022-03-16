// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import mu.KotlinLogging
import org.apache.avro.generic.GenericRecord
import org.apache.parquet.avro.AvroParquetReader
import org.apache.parquet.io.DelegatingSeekableInputStream
import org.apache.parquet.io.InputFile
import org.apache.parquet.io.SeekableInputStream
import software.amazon.awssdk.services.macie2.model.Occurrences
import java.io.ByteArrayInputStream

object ParquetRecordLocator : Locator {
    private val logger = KotlinLogging.logger {}
    override fun locate(
        content: ByteArray,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        require(occurrences.values.stream().anyMatch { it.hasRecords() }) { "Internal: invalid locator picked" }

        val results = mutableMapOf<String, MutableList<String>>()

        // Prepare for reading by collecting all offsets with matches along with the column names.
        val recordOffsets = mutableMapOf<Long, MutableMap<String, String>>()
        occurrences.forEach { (key, value) ->
            results.computeIfAbsent(key) { mutableListOf() }
            value.records().forEach { r ->
                val offset = r.recordIndex()
                val column = r.jsonPath().substring(2)
                recordOffsets.computeIfAbsent(offset) { mutableMapOf() }[key] = column
            }
        }
        // Sort offsets to ensure we find what we need in one pass of the file.
        val work = recordOffsets.keys.toSortedSet()

        // Open the file and read
        AvroParquetReader.genericRecordReader(inputFile(content)).use { reader ->
            var offset = 0L
            var seenEnough = false
            var next = reader.read()
            while (next != null && work.isNotEmpty() && !seenEnough) {
                if (work.first() == offset) {
                    seenEnough = addToResults(next, recordOffsets[offset]!!, query, results)
                    work.remove(offset)
                }
                next = reader.read()
                offset++
            }
        }

        return results
    }

    /**
     * Extract the detection columns from the given record and add them to the results.
     */
    private fun addToResults(
        record: GenericRecord,
        detections: MutableMap<String, String>,
        query: RevealQuery,
        results: MutableMap<String, MutableList<String>>
    ): Boolean {
        detections.forEach { (name, col) ->
            try {
                val seen = results[name]!!
                if (seen.size < query.limit) {
                    seen.add(record.get(col).toString().take(query.truncate))
                }
            } catch (e: Exception) {
                logger.warn { "invalid column $col" }
            }
        }
        return results.values.count { it.size == query.limit } == results.values.size
    }

    /**
     * In-memory Parquet input file.
     */
    private fun inputFile(inMemory: ByteArray): InputFile {
        return object : InputFile {
            /**
             * Size of the in-memory buffer.
             */
            override fun getLength(): Long {
                return inMemory.size.toLong()
            }

            /**
             * Read a seekable stream to the byte buffer.
             */
            override fun newStream(): SeekableInputStream {
                val seekableBytes = object : ByteArrayInputStream(inMemory) {
                    fun seek(newPos: Long) {
                        pos = newPos.toInt()
                    }

                    fun getPosition(): Long {
                        return pos.toLong()
                    }
                }

                return object : DelegatingSeekableInputStream(seekableBytes) {
                    override fun getPos(): Long {
                        return seekableBytes.getPosition()
                    }

                    override fun seek(newPos: Long) {
                        return seekableBytes.seek(newPos)
                    }
                }
            }
        }
    }
}
