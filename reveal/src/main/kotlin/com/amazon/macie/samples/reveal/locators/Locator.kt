// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal.locators

import com.amazon.macie.samples.reveal.RevealQuery
import software.amazon.awssdk.services.macie2.model.Occurrences

/**
 * A locator takes the content of the object, and locates the occurences.
 */
sealed interface Locator {
    /**
     * Locate the given occurrences in the content, limit the response to the given count.
     */
    fun locate(
        content: ByteArray,
        occurrences: Map<String, Occurrences>,
        query: RevealQuery
    ): Map<String, List<String>>
}
