// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

object LocatorRegistry {
    /**
     * Map mime types to the most appropriate record locator.
     */
    private val available = mapOf(
        "application/json" to JsonRecordLocator,
        "application/avro" to AvroRecordLocator,
        "text/plain" to LineRangeRecordLocator,
        "text/csv" to CsvRecordLocator,
        "application/parquet" to ParquetRecordLocator,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to XLSRecordLocator,
        "application/gzip" to GzipRecordLocator,
    )

    fun getLocatorForMime(mime: String): Locator? {
        return available[mime]
    }

    fun canLocate(mime: String): Boolean {
        return available.containsKey(mime)
    }
}
