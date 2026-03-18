/*
 * Copyright 2025-2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.fhir.sync.upload.request

import com.google.android.fhir.sync.upload.patch.Patch
import com.google.android.fhir.sync.upload.patch.PatchMapping
import com.google.android.fhir.sync.upload.patch.StronglyConnectedPatchMappings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Generates [BundleUploadRequest]s that pack multiple patches into FHIR Transaction Bundles.
 *
 * Strongly connected patch mappings are kept together in the same bundle to maintain referential
 * integrity during upload.
 */
internal class TransactionBundleGenerator(
  private val httpVerbForCreate: HttpVerb,
  private val httpVerbForUpdate: HttpVerb,
  private val bundleSize: Int,
) : UploadRequestGenerator {

  private val json = Json { ignoreUnknownKeys = true }

  override fun generateUploadRequests(
    mappings: List<StronglyConnectedPatchMappings>,
  ): List<UploadRequest> {
    val requests = mutableListOf<BundleUploadRequest>()
    val currentBatch = mutableListOf<PatchMapping>()

    for (scc in mappings) {
      // If SCC is larger than bundle size, it must go in its own bundle
      if (scc.patchMappings.size > bundleSize) {
        // Flush current batch first
        if (currentBatch.isNotEmpty()) {
          requests.add(createBundleRequest(currentBatch.toList()))
          currentBatch.clear()
        }
        requests.add(createBundleRequest(scc.patchMappings))
        continue
      }

      // If adding this SCC would exceed bundle size, flush current batch
      if (currentBatch.size + scc.patchMappings.size > bundleSize) {
        requests.add(createBundleRequest(currentBatch.toList()))
        currentBatch.clear()
      }

      currentBatch.addAll(scc.patchMappings)
    }

    // Flush remaining
    if (currentBatch.isNotEmpty()) {
      requests.add(createBundleRequest(currentBatch.toList()))
    }

    return requests
  }

  private fun createBundleRequest(patchMappings: List<PatchMapping>): BundleUploadRequest {
    val entries = patchMappings.map { createBundleEntry(it.generatedPatch) }

    val bundle =
      JsonObject(
        mapOf(
          "resourceType" to JsonPrimitive("Bundle"),
          "type" to JsonPrimitive("transaction"),
          "entry" to JsonArray(entries),
        ),
      )

    return BundleUploadRequest(
      headers = mapOf("Content-Type" to "application/fhir+json"),
      payload = Json.encodeToString(bundle),
      mappings = patchMappings,
    )
  }

  private fun createBundleEntry(patch: Patch): JsonObject {
    val request = createEntryRequest(patch)
    val entryMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

    entryMap["fullUrl"] = JsonPrimitive("${patch.resourceType}/${patch.resourceId}")
    entryMap["request"] = request

    when (patch.type) {
      Patch.Type.INSERT -> {
        val resource = json.parseToJsonElement(patch.payload).jsonObject
        entryMap["resource"] = resource
      }
      Patch.Type.UPDATE -> {
        if (httpVerbForUpdate == HttpVerb.PATCH) {
          // Wrap JSON patch in a Binary resource
          val binary =
            JsonObject(
              mapOf(
                "resourceType" to JsonPrimitive("Binary"),
                "contentType" to JsonPrimitive("application/json-patch+json"),
                "data" to
                  JsonPrimitive(
                    kotlin.io.encoding.Base64.encode(patch.payload.encodeToByteArray()),
                  ),
              ),
            )
          entryMap["resource"] = binary
        } else {
          // PUT — payload is the full resource
          val resource = json.parseToJsonElement(patch.payload).jsonObject
          entryMap["resource"] = resource
        }
      }
      Patch.Type.DELETE -> {
        // No resource for delete
      }
    }

    return JsonObject(entryMap)
  }

  private fun createEntryRequest(patch: Patch): JsonObject {
    val requestMap = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

    when (patch.type) {
      Patch.Type.INSERT -> {
        val verb = if (httpVerbForCreate == HttpVerb.PUT) "PUT" else "POST"
        requestMap["method"] = JsonPrimitive(verb)
        requestMap["url"] =
          if (httpVerbForCreate == HttpVerb.PUT) {
            JsonPrimitive("${patch.resourceType}/${patch.resourceId}")
          } else {
            JsonPrimitive(patch.resourceType)
          }
      }
      Patch.Type.UPDATE -> {
        requestMap["method"] =
          JsonPrimitive(if (httpVerbForUpdate == HttpVerb.PATCH) "PATCH" else "PUT")
        requestMap["url"] = JsonPrimitive("${patch.resourceType}/${patch.resourceId}")
        patch.versionId?.let {
          requestMap["ifMatch"] = JsonPrimitive("W/\"$it\"")
        }
      }
      Patch.Type.DELETE -> {
        requestMap["method"] = JsonPrimitive("DELETE")
        requestMap["url"] = JsonPrimitive("${patch.resourceType}/${patch.resourceId}")
        patch.versionId?.let {
          requestMap["ifMatch"] = JsonPrimitive("W/\"$it\"")
        }
      }
    }

    return JsonObject(requestMap)
  }
}
