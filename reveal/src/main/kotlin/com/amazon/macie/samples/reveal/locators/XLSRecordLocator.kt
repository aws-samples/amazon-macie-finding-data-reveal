// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import mu.KotlinLogging
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.util.CellReference
import software.amazon.awssdk.services.macie2.model.Occurrences
import java.io.ByteArrayInputStream
import kotlin.math.truncate

/**
 * Read from Excel spreadsheets.
 */
object XLSRecordLocator : Locator {
    private val logger = KotlinLogging.logger {}

    override fun locate(
        content: ByteArray,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>> {
        require(occurrences.values.stream().anyMatch { it.hasCells() }) { "Internal: invalid locator picked" }
        val dataFormatter = DataFormatter()
        val workbook = WorkbookFactory.create(ByteArrayInputStream(content))
        return occurrences.mapValues { (_, occurrence) ->
            val values = mutableListOf<String>()
            for (it in occurrence.cells().take(query.limit)) {
                try {
                    val ref = CellReference(it.cellReference())
                    var sheetName = ref.sheetName

                    // When there is only 1 sheet, the sheet name is not provided in the classification result.
                    // In this case, we can replace it with the first sheet's name.
                    if (sheetName == null || sheetName.isEmpty()) {
                        sheetName = workbook.getSheetName(0)
                    }
                    val cell = workbook.getSheet(sheetName).getRow(ref.row).getCell(ref.col.toInt())
                    values.add(dataFormatter.formatCellValue(cell).take(query.truncate))
                } catch (e: Exception) {
                    logger.warn { "invalid line reference $it" }
                }
            }
            values
        }
    }
}
