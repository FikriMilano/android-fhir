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

package com.google.android.fhir.sync.upload

import co.touchlab.kermit.Logger
import com.google.android.fhir.LocalChange
import com.google.android.fhir.db.LocalChangeResourceReference
import com.google.android.fhir.sync.DataSource
import com.google.android.fhir.sync.upload.patch.PatchGenerator
import com.google.android.fhir.sync.upload.request.BundleUploadRequest
import com.google.android.fhir.sync.upload.request.HttpVerb
import com.google.android.fhir.sync.upload.request.UploadRequest
import com.google.android.fhir.sync.upload.request.UploadRequestGenerator
import com.google.android.fhir.sync.upload.request.UrlUploadRequest
import com.google.fhir.model.r4.Bundle
import com.google.fhir.model.r4.OperationOutcome
import com.google.fhir.model.r4.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.transformWhile

/**
 * Uploads changes made locally to FHIR resources to server in the following steps:
 * 1. fetching local changes from the on-device SQLite database,
 * 2. creating patches to be sent to the server using the local changes,
 * 3. generating HTTP requests to be sent to the server,
 * 4. processing the responses from the server and consolidate any changes (i.e. updates resource
 *    IDs).
 */
internal class Uploader(
  private val dataSource: DataSource,
  private val patchGenerator: PatchGenerator,
  private val requestGenerator: UploadRequestGenerator,
) {

  suspend fun upload(
    localChanges: List<LocalChange>,
    localChangesReferences: List<LocalChangeResourceReference>,
  ): Flow<UploadRequestResult> =
    localChanges
      .let { patchGenerator.generate(it, localChangesReferences) }
      .let { requestGenerator.generateUploadRequests(it) }
      .asFlow()
      .transformWhile {
        with(handleUploadRequest(it)) {
          emit(this)
          this !is UploadRequestResult.Failure
        }
      }

  private suspend fun handleUploadRequest(request: UploadRequest): UploadRequestResult {
    val affectedChanges =
      when (request) {
        is UrlUploadRequest -> request.mapping.localChanges
        is BundleUploadRequest -> request.mappings.flatMap { it.localChanges }
      }
    return try {
      val response = executeRequest(request)
      when {
        response is OperationOutcome && response.issue.isNotEmpty() ->
          UploadRequestResult.Failure(
            affectedChanges,
            ResourceSyncException(
              resourceType = affectedChanges.firstOrNull()?.resourceType ?: "Unknown",
              exception = Exception(response.issue.firstOrNull()?.diagnostics?.value ?: "Unknown error"),
            ),
          )
        response is OperationOutcome ->
          UploadRequestResult.Failure(
            affectedChanges,
            ResourceSyncException(
              resourceType = affectedChanges.firstOrNull()?.resourceType ?: "Unknown",
              exception = Exception("Unknown response for ${affectedChanges.firstOrNull()?.resourceType}"),
            ),
          )
        else -> handleSuccessfulUploadResponse(request, response)
      }
    } catch (e: Exception) {
      Logger.e("Uploader") { "Upload failed: ${e.message}" }
      UploadRequestResult.Failure(
        localChanges = affectedChanges,
        uploadError =
          ResourceSyncException(
            resourceType = affectedChanges.firstOrNull()?.resourceType ?: "Unknown",
            exception = e,
          ),
      )
    }
  }

  private suspend fun executeRequest(request: UploadRequest): Resource {
    return when (request) {
      is UrlUploadRequest ->
        dataSource.upload(request.url, request.httpVerb, request.headers, request.payload)
      is BundleUploadRequest ->
        dataSource.upload(request.url, HttpVerb.POST, request.headers, request.payload)
    }
  }

  private fun handleSuccessfulUploadResponse(
    request: UploadRequest,
    response: Resource,
  ): UploadRequestResult {
    return when (request) {
      is UrlUploadRequest -> {
        UploadRequestResult.Success(
          successfulUploadResponseMappings =
            listOf(
              ResourceUploadResponseMapping(
                localChanges = request.mapping.localChanges,
                output = response,
              ),
            ),
        )
      }
      is BundleUploadRequest -> {
        handleBundleUploadResponse(request, response)
      }
    }
  }

  private fun handleBundleUploadResponse(
    request: BundleUploadRequest,
    response: Resource,
  ): UploadRequestResult {
    val bundle = response as? Bundle
    if (bundle == null || bundle.type?.value != Bundle.BundleType.Transaction_Response) {
      return UploadRequestResult.Failure(
        localChanges = request.mappings.flatMap { it.localChanges },
        uploadError =
          ResourceSyncException(
            resourceType = request.mappings.firstOrNull()?.localChanges?.firstOrNull()?.resourceType ?: "Unknown",
            exception =
              IllegalStateException(
                "Unknown mapping for request and response. Response Type: ${response::class.simpleName}",
              ),
          ),
      )
    }

    require(request.mappings.size == bundle.entry.size) {
      "Bundle request mappings (${request.mappings.size}) and response entries (${bundle.entry.size}) must have the same size."
    }

    val responseMappings =
      request.mappings.mapIndexed { index, patchMapping ->
        val bundleEntry = bundle.entry[index]
        when {
          bundleEntry.resource != null ->
            ResourceUploadResponseMapping(
              localChanges = patchMapping.localChanges,
              output = bundleEntry.resource!!,
            )
          bundleEntry.response != null ->
            BundleComponentUploadResponseMapping(
              localChanges = patchMapping.localChanges,
              etag = bundleEntry.response?.etag?.value,
              location = bundleEntry.response?.location?.value,
              lastModified = bundleEntry.response?.lastModified?.value?.toString(),
            )
          else ->
            throw IllegalStateException(
              "Unknown response: $bundleEntry for Bundle Request at index $index",
            )
        }
      }

    return UploadRequestResult.Success(successfulUploadResponseMappings = responseMappings)
  }
}
