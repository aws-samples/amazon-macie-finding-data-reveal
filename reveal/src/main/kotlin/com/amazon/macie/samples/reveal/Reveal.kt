// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.amazon.macie.samples.reveal

import com.amazon.macie.samples.reveal.locators.LocatorRegistry
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.google.common.net.UrlEscapers
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.Criteria
import com.jayway.jsonpath.Filter
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.macie2.Macie2AsyncClient
import software.amazon.awssdk.services.macie2.model.ClassificationResult
import software.amazon.awssdk.services.macie2.model.FindingCategory
import software.amazon.awssdk.services.macie2.model.GetFindingsRequest
import software.amazon.awssdk.services.macie2.model.GetFindingsResponse
import software.amazon.awssdk.services.macie2.model.Occurrences
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.GZIPInputStream
import kotlin.streams.toList

/**
 * A query from the client.
 */
data class RevealQuery(
    /**
     * The finding identifier.
     */
    val findingId: String,
    /**
     * The set of entities we're interested in, if empty all entities are returned.
     */
    val entities: Set<String> = emptySet(),
    /**
     * The count of entities revealed, e.g. if set to 2, 2 values for each of the selected entities will be revealed.
     */
    val limit: Int = LIMIT,
    /**
     * The maximum length of the reveal entity. If the sensitive data is larger than
     */
    val truncate: Int = SIZE
) {
    companion object {
        /**
         * The default is to return upto 10 values for each entity.
         */
        private const val LIMIT = 10

        /**
         * The default maximum characters returned for the sensitive data.
         */
        private const val SIZE = 128
    }
}

/**
 * The Resolver interface.
 */
interface Revealer {

    /**
     * Reveal the sensitive entities reported by the finding in the given query. Use {@code canRevealFinding} to confirm
     * if the specific finding can be revealed by the system.
     * @param query The query
     * @return A revelation, or an error with a brief description of what went wrong.
     */
    fun revealFinding(query: RevealQuery): Mono<Revelation>
}

/**
 * The AWS backing services needed to do the revelation.
 */
interface AwsDependencies {
    /**
     * Macie to fetch the finding detail.
     */
    fun macie(): Macie2AsyncClient {
        return Macie2AsyncClient.create()
    }

    /**
     * S3 to fetch the object contents.
     */
    fun s3(): S3AsyncClient {
        return S3AsyncClient.create()
    }
}

/**
 * One revelation.
 */
data class Revelation(
    /**
     * The location of the object, as an S3 URL
     */
    val objectPath: String,
    /**
     * The mime-type used to interpret and scan the content.
     */
    val mime: String,
    /**
     * The specific sensitive data found.
     */
    val entities: Map<String, List<String>>
)

/**
 * Internal representation of the work we need to do.
 */
private data class Work(
    val objectPath: String,
    val escapedObjUri: URI,
    val resultsUri: URI,
    val mime: String,
    val query: RevealQuery,
) {
    fun request(uri: URI): GetObjectRequest {
        return GetObjectRequest.builder().bucket(uri.host).key(uri.path.drop(1)).build()
    }
}

/**
 * A concrete implementation of the Revealer interface defined above.
 */
class Reveal(private val aws: AwsDependencies = object : AwsDependencies {}) : Revealer {
    /***
     * Defaults
     */
    companion object {
        /**
         * The biggest object we'll fetch from S3.
         */
        private const val MAX_SIZE = 300 * 1024 * 1024

        /**
         * The JSONPath configuration used to extract locators.
         */
        private val JSON_PATH_CONFIG = Configuration.builder().jsonProvider(JacksonJsonProvider()).build()

        /**
         * Jackson Object Mapper to serialize/deserialize objects.
         */
        private val JSON = jsonMapper {
            addModule(JavaTimeModule())
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        }
    }

    /**
     * The workhorse.
     */
    override fun revealFinding(query: RevealQuery): Mono<Revelation> {
        val macie = aws.macie()
        val request = GetFindingsRequest.builder().findingIds(query.findingId).build()
        return Mono.fromFuture { macie.getFindings(request) }
            .map { response -> resolveWork(response, query) }
            .flatMap { work -> doWork(work) }
            .doFinally { macie.close() }
    }

    /**
     * Extract the work we need to do from the GetFindingsResponse.
     */
    private fun resolveWork(response: GetFindingsResponse, query: RevealQuery): Work {
        require(response.findings().isNotEmpty()) { "Finding ${query.findingId} was not found" }

        // Don't handle policy findings.
        val finding = response.findings().first()
        require(
            finding.category().equals(FindingCategory.CLASSIFICATION)
        ) { "Finding ${query.findingId} is a policy finding." }

        // Can we handle this type of content
        val mime = finding.classificationDetails().result().mimeType()
        require(LocatorRegistry.canLocate(mime)) {
            "Finding ${query.findingId} is for an object of unsupported mime-type: $mime"
        }

        // We artificially restrict the amount of data we download
        val size = finding.resourcesAffected().s3Object().size()
        require(size <= MAX_SIZE) {
            "Finding ${query.findingId} is for an object that is bigger than $MAX_SIZE bytes ($size)}"
        }

        // Validate the ask, does the finding even have entities that we need.
        val entitiesToReveal = mutableSetOf<String>()
        val results = finding.classificationDetails().result()
        results.sensitiveData().stream()
            .flatMap { it.detections().stream() }.map { it.type() }
            .forEach(entitiesToReveal::add)
        results.customDataIdentifiers().detections().stream()
            .map { it.name() }
            .forEach(entitiesToReveal::add)

        // If we have specific needs, only keep those
        if (query.entities.isNotEmpty()) {
            entitiesToReveal.retainAll(query.entities.stream().map { it.uppercase() }.toList().toSet())
        }

        // If there are no entities left to reveal, can't proceed.
        require(entitiesToReveal.isNotEmpty()) {
            "Finding ${query.findingId} does not have any detections of type ${query.entities}."
        }

        // Pack up the work we need to do
        val objectPath = "s3://${finding.resourcesAffected().s3Object().path()}"
        val objectUri = URI(getEncodedS3Path(objectPath))
        val resultsPath = URI(finding.classificationDetails().detailedResultsLocation())
        val resolvedQuery = RevealQuery(query.findingId, entitiesToReveal, query.limit, query.truncate)
        return Work(objectPath, objectUri, resultsPath, mime, resolvedQuery)
    }

    /**
     * Fetch the object and do the reveal the sensitive data.
     */
    private fun doWork(work: Work): Mono<Revelation> {
        val s3 = aws.s3()
        val entities = resolveOccurrences(s3, work)
        val getObject = s3.getObject(work.request(work.escapedObjUri), AsyncResponseTransformer.toBytes())
        val scannedObject = Mono.fromFuture(getObject).map { it.asByteArray() }
        return entities.zipWith(scannedObject).map {
            val bytes = it.t2
            val occurrences = it.t1
            val locator = LocatorRegistry.getLocatorForMime(work.mime)!!
            val seen = locator.locate(bytes, occurrences, work.query)
            Revelation(work.objectPath, work.mime, seen)
        }.doFinally {
            s3.close()
        }
    }

    /**
     * Resolve the occurrences from the classification results.
     */
    private fun resolveOccurrences(s3: S3AsyncClient, work: Work): Mono<Map<String, Occurrences>> {
        val pick = "$.[?]['classificationDetails']['result']"
        val path = work.objectPath.removePrefix("s3://")
        val predicate = Filter.filter(Criteria.where("resourcesAffected.s3Object.path").eq(path))
        val crsDetails = s3.getObject(work.request(work.resultsUri), AsyncResponseTransformer.toBytes())
        return Mono.fromFuture(crsDetails)
            .map { GZIPInputStream(ByteArrayInputStream(it.asByteArray())) } // Unzip it
            .map { it.bufferedReader(Charsets.UTF_8).readText().lines().filter { l -> l.isNotEmpty() } } // Read Lines
            .flatMapMany { Flux.fromIterable(it) } // Consider each line individually
            .map { JsonPath.using(JSON_PATH_CONFIG).parse(it).read<List<Any>>(pick, predicate) } // Pick what we want
            .filter { it.isNotEmpty() } // Make sure we have something in hand
            .map { JSON.writeValueAsString(it.first()) } // Deserialize back to JSON (to serialize into results)
            .map { JSON.readValue(it, ClassificationResult.serializableBuilderClass()).build() }
            .next() // Not expecting more than one (take the first one)
            .switchIfEmpty(Mono.error { IllegalArgumentException("Locators not found or object path did not match.") })
            .map { unpackResults(it, work) } // Unpack what we need
    }

    /**
     * Unpack the classification results into a list of occurrences.
     */
    private fun unpackResults(results: ClassificationResult, work: Work): Map<String, Occurrences> {
        val entities = mutableMapOf<String, Occurrences>()
        results.sensitiveData().forEach { sensitiveDataItem ->
            sensitiveDataItem.detections().forEach {
                if (it.type() in work.query.entities) {
                    entities[it.type()] = it.occurrences()
                }
            }
        }
        results.customDataIdentifiers()?.detections()?.forEach {
            if (it.name() in work.query.entities) {
                entities[it.name()] = it.occurrences()
            }
        }
        return entities
    }

    private fun getEncodedS3Path(path: String): String {
        return UrlEscapers.urlFragmentEscaper().escape(path)
    }
}
