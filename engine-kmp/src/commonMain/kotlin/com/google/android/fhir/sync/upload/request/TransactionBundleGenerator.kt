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

import com.google.android.fhir.ContentTypes
import com.google.android.fhir.sync.upload.patch.Patch
import com.google.android.fhir.sync.upload.patch.PatchMapping
import com.google.android.fhir.sync.upload.patch.StronglyConnectedPatchMappings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Generates list of [BundleUploadRequest] of type Transaction Bundle from the [Patch]es */
internal class TransactionBundleGenerator(
  private val generatedBundleSize: Int,
  private val useETagForUpload: Boolean,
  private val getBundleEntryForPatch: (patch: Patch, useETagForUpload: Boolean) -> JsonObject,
) : UploadRequestGenerator {

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * In order to accommodate cyclic dependencies between [PatchMapping]s and maintain referential
   * integrity on the server, the [PatchMapping]s in a [StronglyConnectedPatchMappings] are all put
   * in a single [BundleUploadRequest]. Based on the [generatedBundleSize], the remaining space of
   * the [BundleUploadRequest] maybe filled with other [StronglyConnectedPatchMappings] mappings.
   *
   * In case a single [StronglyConnectedPatchMappings] has more [PatchMapping]s than the
   * [generatedBundleSize], [generatedBundleSize] will be ignored so that all of the dependent
   * mappings in [StronglyConnectedPatchMappings] can be sent in a single Bundle.
   */
  override fun generateUploadRequests(
    mappedPatches: List<StronglyConnectedPatchMappings>,
  ): List<UploadRequest> {
    val mappingsPerBundle = mutableListOf<List<PatchMapping>>()

    var bundle = mutableListOf<PatchMapping>()
    mappedPatches.forEach {
      if ((bundle.size + it.patchMappings.size) <= generatedBundleSize) {
        bundle.addAll(it.patchMappings)
      } else {
        if (bundle.isNotEmpty()) {
          mappingsPerBundle.add(bundle)
          bundle = mutableListOf()
        }
        bundle.addAll(it.patchMappings)
      }
    }

    if (bundle.isNotEmpty()) mappingsPerBundle.add(bundle)

    return mappingsPerBundle.map { patchList -> generateBundleRequest(patchList) }
  }

  private fun generateBundleRequest(patches: List<PatchMapping>): BundleUploadRequest {
    val entries =
      patches.map { getBundleEntryForPatch(it.generatedPatch, useETagForUpload) }

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
      mappings = patches,
    )
  }

  companion object Factory {

    fun getDefault(useETagForUpload: Boolean = true, bundleSize: Int = 500) =
      getGenerator(HttpVerb.PUT, HttpVerb.PATCH, bundleSize, useETagForUpload)

    /**
     * Returns a [TransactionBundleGenerator] based on the provided [HttpVerb]s for creating and
     * updating resources. The function may throw an [IllegalArgumentException] if the provided
     * [HttpVerb]s are not supported.
     */
    fun getGenerator(
      httpVerbToUseForCreate: HttpVerb,
      httpVerbToUseForUpdate: HttpVerb,
      generatedBundleSize: Int = 500,
      useETagForUpload: Boolean = true,
    ): TransactionBundleGenerator {
      val createMapping =
        mapOf(
          HttpVerb.PUT to ::putForCreateEntry,
          HttpVerb.POST to ::postForCreateEntry,
        )

      val updateMapping =
        mapOf(
          HttpVerb.PATCH to ::patchForUpdateEntry,
        )

      val createFunction =
        createMapping[httpVerbToUseForCreate]
          ?: throw IllegalArgumentException(
            "Creation using $httpVerbToUseForCreate is not supported.",
          )

      val updateFunction =
        updateMapping[httpVerbToUseForUpdate]
          ?: throw IllegalArgumentException(
            "Update using $httpVerbToUseForUpdate is not supported.",
          )

      return TransactionBundleGenerator(generatedBundleSize, useETagForUpload) {
          patch,
          useETag,
        ->
        when (patch.type) {
          Patch.Type.INSERT -> createFunction(patch, useETag)
          Patch.Type.UPDATE -> updateFunction(patch, useETag)
          Patch.Type.DELETE -> deleteEntry(patch, useETag)
        }
      }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun putForCreateEntry(patch: Patch, useETagForUpload: Boolean): JsonObject {
      val resource = json.parseToJsonElement(patch.payload).jsonObject
      val request = buildEntryRequest("PUT", "${patch.resourceType}/${patch.resourceId}", patch, useETagForUpload)
      return buildEntry("${patch.resourceType}/${patch.resourceId}", request, resource)
    }

    private fun postForCreateEntry(patch: Patch, useETagForUpload: Boolean): JsonObject {
      val resource = json.parseToJsonElement(patch.payload).jsonObject
      val request = buildEntryRequest("POST", patch.resourceType, patch, useETagForUpload)
      return buildEntry("${patch.resourceType}/${patch.resourceId}", request, resource)
    }

    private fun patchForUpdateEntry(patch: Patch, useETagForUpload: Boolean): JsonObject {
      val binary =
        JsonObject(
          mapOf(
            "resourceType" to JsonPrimitive("Binary"),
            "contentType" to JsonPrimitive(ContentTypes.APPLICATION_JSON_PATCH),
            "data" to
              JsonPrimitive(
                kotlin.io.encoding.Base64.encode(patch.payload.encodeToByteArray()),
              ),
          ),
        )
      val request = buildEntryRequest("PATCH", "${patch.resourceType}/${patch.resourceId}", patch, useETagForUpload)
      return buildEntry("${patch.resourceType}/${patch.resourceId}", request, binary)
    }

    private fun deleteEntry(patch: Patch, useETagForUpload: Boolean): JsonObject {
      val request = buildEntryRequest("DELETE", "${patch.resourceType}/${patch.resourceId}", patch, useETagForUpload)
      return buildEntry("${patch.resourceType}/${patch.resourceId}", request, resource = null)
    }

    private fun buildEntryRequest(
      method: String,
      url: String,
      patch: Patch,
      useETagForUpload: Boolean,
    ): JsonObject {
      val requestMap = mutableMapOf<String, JsonElement>()
      requestMap["method"] = JsonPrimitive(method)
      requestMap["url"] = JsonPrimitive(url)

      if (useETagForUpload && !patch.versionId.isNullOrEmpty()) {
        when (patch.type) {
          Patch.Type.UPDATE,
          Patch.Type.DELETE, -> requestMap["ifMatch"] = JsonPrimitive("W/\"${patch.versionId}\"")
          Patch.Type.INSERT -> {}
        }
      }

      return JsonObject(requestMap)
    }

    private fun buildEntry(
      fullUrl: String,
      request: JsonObject,
      resource: JsonObject?,
    ): JsonObject {
      val entryMap = mutableMapOf<String, JsonElement>()
      entryMap["fullUrl"] = JsonPrimitive(fullUrl)
      entryMap["request"] = request
      if (resource != null) {
        entryMap["resource"] = resource
      }
      return JsonObject(entryMap)
    }
  }
}
