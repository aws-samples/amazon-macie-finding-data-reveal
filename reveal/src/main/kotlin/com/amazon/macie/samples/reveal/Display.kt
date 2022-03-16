// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.table.Borders
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal

/**
 * Render the revelation as a table.
 */
object Display {
    fun render(revealed: Revelation) {
        with(Terminal()) {
            println(
                table {
                    body {
                        borders = Borders.ALL
                        row("Object", revealed.objectPath)
                        row("Mime", revealed.mime)
                    }
                }
            )
            revealed.entities.forEach {
                println(
                    table {
                        borders = Borders.ALL
                        header {
                            style(TextColors.cyan, bold = true)
                            row(it.key)
                        }
                        body {
                            it.value.forEach { seen -> row(seen) }
                        }
                    }
                )
            }
        }
    }
}
