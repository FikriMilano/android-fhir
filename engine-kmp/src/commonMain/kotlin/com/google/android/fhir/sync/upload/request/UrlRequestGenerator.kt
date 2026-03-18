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

/**
 * Generates [UrlUploadRequest]s for individual patches, each sent as a separate HTTP request to the
 * FHIR server.
 */
internal class UrlRequestGenerator(
  private val httpVerbForCreate: HttpVerb,
  private val httpVerbForUpdate: HttpVerb,
) : UploadRequestGenerator {

  override fun generateUploadRequests(
    mappings: List<StronglyConnectedPatchMappings>,
  ): List<UploadRequest> =
    mappings.flatMap { scc -> scc.patchMappings.map { generateRequest(it) } }

  private fun generateRequest(patchMapping: PatchMapping): UrlUploadRequest {
    val patch = patchMapping.generatedPatch
    return when (patch.type) {
      Patch.Type.INSERT ->
        UrlUploadRequest(
          url = "${patch.resourceType}",
          httpVerb =
            if (httpVerbForCreate == HttpVerb.PUT) HttpVerb.PUT else HttpVerb.POST,
          headers = buildHeaders(patch),
          payload = patch.payload,
          mapping = patchMapping,
        ).let {
          if (httpVerbForCreate == HttpVerb.PUT) {
            it.copy(url = "${patch.resourceType}/${patch.resourceId}")
          } else {
            it
          }
        }
      Patch.Type.UPDATE ->
        UrlUploadRequest(
          url = "${patch.resourceType}/${patch.resourceId}",
          httpVerb = httpVerbForUpdate,
          headers = buildHeaders(patch),
          payload = patch.payload,
          mapping = patchMapping,
        )
      Patch.Type.DELETE ->
        UrlUploadRequest(
          url = "${patch.resourceType}/${patch.resourceId}",
          httpVerb = HttpVerb.DELETE,
          headers = buildHeaders(patch),
          payload = "",
          mapping = patchMapping,
        )
    }
  }

  private fun buildHeaders(patch: Patch): Map<String, String> {
    val headers = mutableMapOf<String, String>()
    if (patch.type == Patch.Type.UPDATE && httpVerbForUpdate == HttpVerb.PATCH) {
      headers["Content-Type"] = "application/json-patch+json"
    }
    patch.versionId?.let {
      if (patch.type == Patch.Type.UPDATE || patch.type == Patch.Type.DELETE) {
        headers["If-Match"] = "W/\"$it\""
      }
    }
    return headers
  }
}
