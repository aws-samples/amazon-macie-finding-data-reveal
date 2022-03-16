// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.macie2.Macie2AsyncClient
import software.amazon.awssdk.services.macie2.model.Finding
import software.amazon.awssdk.services.macie2.model.GetFindingsRequest
import software.amazon.awssdk.services.macie2.model.GetFindingsResponse
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

internal class RevealTest {
    private val s3: S3AsyncClient = mockk()
    private val macie: Macie2AsyncClient = mockk()
    private val json = jsonMapper {
        addModule(JavaTimeModule())
        enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    }

    /**
     * Create a test instance.
     */
    private val reveal = Reveal(
        object : AwsDependencies {
            override fun macie(): Macie2AsyncClient {
                return macie
            }

            override fun s3(): S3AsyncClient {
                return s3
            }
        },
    )

    /**
     * Train the mocks.
     */
    @Before
    fun trainMocks() {
        justRun { macie.close() }
        justRun { s3.close() }
        every {
            s3.getObject(
                any<GetObjectRequest>(),
                any<AsyncResponseTransformer<GetObjectResponse, ResponseBytes<GetObjectResponse>>>()
            )
        } answers {
            s3Response(firstArg<GetObjectRequest>().key())
        }
        every { macie.getFindings(any<GetFindingsRequest>()) } answers {
            macieResponse(firstArg<GetFindingsRequest>().findingIds().first())
        }
    }

    /**
     * Macie finding, with the given id.
     */
    private fun macieResponse(id: String): CompletableFuture<GetFindingsResponse> {
        val bytes = RevealTest::class.java.getResource("/data/findings/$id.json")!!.readBytes()
        val finding = json.readValue(bytes, Finding.serializableBuilderClass()).build()
        return CompletableFuture.completedFuture(GetFindingsResponse.builder().findings(finding).build())
    }

    /**
     * S3 response, read from data/s3.
     */
    private fun s3Response(obj: String): CompletableFuture<ResponseBytes<GetObjectResponse>> {
        val resource = RevealTest::class.java.getResource("/data/s3/${Path.of(obj).fileName}")!!
        return CompletableFuture.completedFuture(
            ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                resource.readBytes()
            )
        )
    }

    /**
     * Reveal findings in an Avro file.
     */
    @Test
    fun testAvro() {
        val query = RevealQuery("avro", setOf("NAME", "PHONE_NUMBER"), 3)
        val revelation = reveal.revealFinding(query).block()!!
        assertEquals(2, revelation.entities.size)
        assertEquals(3, revelation.entities["NAME"]?.size)
        assertEquals(3, revelation.entities["PHONE_NUMBER"]?.size)
        assertEquals("Akua", revelation.entities["NAME"]!![0])
        assertEquals("555-0100", revelation.entities["PHONE_NUMBER"]!![0])
    }

    /**
     * Reveal findings in an Avro file.
     */
    @Test
    fun testJson() {
        val revelation = reveal.revealFinding(RevealQuery("json")).block()!!
        assertEquals(3, revelation.entities.size)
        assertEquals("Carlos Salazar", revelation.entities["NAME"]!![1])
        assertEquals("4111111111111111", revelation.entities["CREDIT_CARD_NUMBER"]!![3])
    }

    @Test
    fun testCsv() {
        val query = RevealQuery("csv")
        val revelation = reveal.revealFinding(query).block()!!
        assertEquals(6, revelation.entities.size)
    }

    @Test
    fun testPathWithSpaces() {
        val query = RevealQuery("path-with-spaces")
        val revelation = reveal.revealFinding(query).block()!!
        assertEquals(6, revelation.entities.size)
    }

    @Test
    fun testParquet() {
        val query = RevealQuery("parquet", limit = 5)
        val revelation = reveal.revealFinding(query).block()!!
        assertEquals(6, revelation.entities.size)
        revelation.entities.forEach { (k, v) ->
            assertEquals(5, v.size, "$k has ${v.size} detections")
        }
    }

    @Test
    fun testCustomDataIdentifier() {
        val query = RevealQuery("cdi", limit = 5)
        val revelation = reveal.revealFinding(query).block()!!
        assertEquals(1, revelation.entities.size)
        revelation.entities.forEach { (k, v) ->
            assertEquals(1, v.size, "$k has ${v.size} detections")
        }
    }
}
