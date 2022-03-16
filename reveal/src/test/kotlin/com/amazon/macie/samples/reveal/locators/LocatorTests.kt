// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import org.junit.Test
import software.amazon.awssdk.services.macie2.model.Cell
import software.amazon.awssdk.services.macie2.model.Occurrences
import software.amazon.awssdk.services.macie2.model.Range
import software.amazon.awssdk.services.macie2.model.Record
import kotlin.test.assertEquals

internal class LocatorTests {
    private val query = RevealQuery("whatever", truncate = 40)

    @Test
    fun canLocateExcelSpreadsheets() {
        val bytes = readBytes("/test.xlsx")
        val cells = listOf<Cell>(
            Cell.builder().cellReference("test!B6").build(),
            Cell.builder().cellReference("test!F11").build(),
            Cell.builder().cellReference("test!F101").build(), // this is invalid and should be ignored
        )
        val revealed = XLSRecordLocator.locate(
            bytes,
            mapOf("whatever" to Occurrences.builder().cells(cells).build()),
            query
        )
        assertEquals(revealed["whatever"], listOf("haystack", "needle"))
    }

    @Test
    fun canLocateSingleSheetExcelSpreadsheetsWithNoSheetNameInLocation() {
        val bytes = readBytes("/test.xlsx")
        val cells = listOf<Cell>(
            Cell.builder().cellReference("B6").build(),
            Cell.builder().cellReference("F11").build(),
            Cell.builder().cellReference("F101").build(), // this is invalid and should be ignored
        )
        val revealed = XLSRecordLocator.locate(
            bytes,
            mapOf("whatever" to Occurrences.builder().cells(cells).build()),
            query
        )
        assertEquals(revealed["whatever"], listOf("haystack", "needle"))
    }

    @Test
    fun canLocateWithMultipleSheetExcelSpreadsheets() {
        val bytes = readBytes("/test-multiple-sheet.xlsx")
        val cells = listOf<Cell>(
            Cell.builder().cellReference("test!B6").build(),
            Cell.builder().cellReference("test!F11").build(),
            Cell.builder().cellReference("test!F101").build(), // this is invalid and should be ignored
            Cell.builder().cellReference("another!C15").build(),
            Cell.builder().cellReference("another!H28").build(),
        )
        val revealed = XLSRecordLocator.locate(
            bytes,
            mapOf("whatever" to Occurrences.builder().cells(cells).build()),
            query
        )
        assertEquals(revealed["whatever"], listOf("haystack", "needle", "ocean", "boat"))
    }

    @Test
    fun canLocateLineRangeOccurrences() {
        val bytes = """
            Once upon a time and a very good time it was
            there was a moocow coming down along the road.
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val ranges = listOf<Range>(
            Range.builder().start(1).end(2).startColumn(22).build(),
            Range.builder().start(10).end(20).startColumn(10).build(),
        )
        val occurrences = mapOf("whatever" to Occurrences.builder().lineRanges(ranges).build())
        val revealed = LineRangeRecordLocator.locate(bytes, occurrences, query)
        assertEquals(listOf("a very good time it was there was a mooc"), revealed["whatever"])
    }

    @Test
    fun canLocateJsonOccurrences() {
        val records = listOf<Record>(
            Record.builder().recordIndex(0).jsonPath("$[2].name").build(),
            Record.builder().recordIndex(0).jsonPath("$[3].gender").build(),
            Record.builder().recordIndex(0).jsonPath("$[100].gender").build(),
        )
        locateRecords(JsonRecordLocator, "/test.json", records)
    }

    @Test
    fun canLocateAvroOccurrences() {
        val records = listOf<Record>(
            Record.builder().recordIndex(2).jsonPath("$.name").build(),
            Record.builder().recordIndex(3).jsonPath("$.gender").build(),
            Record.builder().recordIndex(3).jsonPath("$.no-such-thing").build(),
        )
        locateRecords(AvroRecordLocator, "/test.avro", records)
    }

    @Test
    fun canLocateParquetOccurrences() {
        val records = listOf<Record>(
            Record.builder().recordIndex(0).jsonPath("$.SSN").build(),
            Record.builder().recordIndex(27).jsonPath("$.first_name").build(),
            Record.builder().recordIndex(1).jsonPath("$.city").build(),
            Record.builder().recordIndex(12).jsonPath("$.bad").build(),
        )
        val found = mapOf("whatever" to Occurrences.builder().records(records).build())
        val revealed = ParquetRecordLocator.locate(readBytes("/data/s3/sample-data.parquet"), found, query)
        assertEquals(revealed.size, 1)
        assertEquals(revealed["whatever"], listOf("000-11-1111", "Anytown2", "Wang"))
    }

    private fun readBytes(file: String): ByteArray {
        return LocatorTests::class.java.getResource(file)!!.readBytes()
    }

    private fun locateRecords(locator: Locator, file: String, records: List<Record>) {
        val found = mapOf("whatever" to Occurrences.builder().records(records).build())
        val revealed = locator.locate(readBytes(file), found, query)
        assertEquals(revealed["whatever"], listOf("xKMgdHyLw", "MALE"))
    }
}
