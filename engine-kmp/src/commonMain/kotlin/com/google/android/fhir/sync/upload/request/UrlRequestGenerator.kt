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

/** Generates list of [UrlUploadRequest]s for a list of [Patch]es. */
internal class UrlRequestGenerator(
  private val getUrlRequestForPatch: (patch: Patch, mapping: PatchMapping) -> UrlUploadRequest,
) : UploadRequestGenerator {

  /**
   * Since a [UrlUploadRequest] can only handle a single resource request, the
   * [StronglyConnectedPatchMappings.patchMappings] are flattened and handled as acyclic mapping to
   * generate [UrlUploadRequest] for each [PatchMapping].
   *
   * **NOTE**
   *
   * Since the referential integrity on the sever may get violated if the subsequent requests have
   * cyclic dependency on each other, We may introduce configuration for application to provide
   * server's referential integrity settings and make it illegal to generate [UrlUploadRequest] when
   * server has strict referential integrity and the requests have cyclic dependency amongst itself.
   */
  override fun generateUploadRequests(
    mappedPatches: List<StronglyConnectedPatchMappings>,
  ): List<UploadRequest> =
    mappedPatches
      .map { it.patchMappings }
      .flatten()
      .map { getUrlRequestForPatch(it.generatedPatch, it) }

  companion object Factory {

    private val createMapping =
      mapOf(
        HttpVerb.POST to this::postForCreateResource,
        HttpVerb.PUT to this::putForCreateResource,
      )

    private val updateMapping =
      mapOf(
        HttpVerb.PATCH to this::patchForUpdateResource,
      )

    fun getDefault() = getGenerator(HttpVerb.PUT, HttpVerb.PATCH)

    /**
     * Returns a [UrlRequestGenerator] based on the provided [HttpVerb]s for creating and updating
     * resources. The function may throw an [IllegalArgumentException] if the provided [HttpVerb]s
     * are not supported.
     */
    fun getGenerator(
      httpVerbToUseForCreate: HttpVerb,
      httpVerbToUseForUpdate: HttpVerb,
    ): UrlRequestGenerator {
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

      return UrlRequestGenerator { patch, mapping ->
        when (patch.type) {
          Patch.Type.INSERT -> createFunction(patch, mapping)
          Patch.Type.UPDATE -> updateFunction(patch, mapping)
          Patch.Type.DELETE -> deleteFunction(patch, mapping)
        }
      }
    }

    private fun deleteFunction(patch: Patch, mapping: PatchMapping) =
      UrlUploadRequest(
        httpVerb = HttpVerb.DELETE,
        url = "${patch.resourceType}/${patch.resourceId}",
        payload = "",
        headers = buildETagHeaders(patch),
        mapping = mapping,
      )

    private fun postForCreateResource(patch: Patch, mapping: PatchMapping) =
      UrlUploadRequest(
        httpVerb = HttpVerb.POST,
        url = patch.resourceType,
        payload = patch.payload,
        headers = emptyMap(),
        mapping = mapping,
      )

    private fun putForCreateResource(patch: Patch, mapping: PatchMapping) =
      UrlUploadRequest(
        httpVerb = HttpVerb.PUT,
        url = "${patch.resourceType}/${patch.resourceId}",
        payload = patch.payload,
        headers = emptyMap(),
        mapping = mapping,
      )

    private fun patchForUpdateResource(patch: Patch, mapping: PatchMapping) =
      UrlUploadRequest(
        httpVerb = HttpVerb.PATCH,
        url = "${patch.resourceType}/${patch.resourceId}",
        payload = patch.payload,
        headers =
          buildMap {
            put("Content-Type", ContentTypes.APPLICATION_JSON_PATCH)
            putAll(buildETagHeaders(patch))
          },
        mapping = mapping,
      )

    private fun buildETagHeaders(patch: Patch): Map<String, String> =
      buildMap {
        if (!patch.versionId.isNullOrEmpty()) {
          when (patch.type) {
            Patch.Type.UPDATE,
            Patch.Type.DELETE, -> put("If-Match", "W/\"${patch.versionId}\"")
            Patch.Type.INSERT -> {}
          }
        }
      }
  }
}
