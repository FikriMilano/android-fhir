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
import com.google.fhir.model.r4.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Uploads local changes to the FHIR server.
 *
 * The upload flow: local changes → patches → upload requests → HTTP calls → response mapping.
 */
internal class Uploader(
  private val dataSource: DataSource,
  private val patchGenerator: PatchGenerator,
  private val requestGenerator: UploadRequestGenerator,
) {

  fun upload(
    localChanges: List<LocalChange>,
    localChangesReferences: List<LocalChangeResourceReference>,
  ): Flow<UploadRequestResult> = flow {
    val patchMappings = patchGenerator.generate(localChanges, localChangesReferences)
    val requests = requestGenerator.generateUploadRequests(patchMappings)

    requests.forEach { request ->
      try {
        val response = executeRequest(request)
        emit(handleSuccessfulUploadResponse(request, response))
      } catch (e: Exception) {
        Logger.e("Uploader") { "Upload failed: ${e.message}" }
        val affectedChanges =
          when (request) {
            is UrlUploadRequest -> request.mapping.localChanges
            is BundleUploadRequest -> request.mappings.flatMap { it.localChanges }
          }
        emit(
          UploadRequestResult.Failure(
            localChanges = affectedChanges,
            uploadError =
              ResourceSyncException(
                resourceType = affectedChanges.firstOrNull()?.resourceType ?: "Unknown",
                exception = e,
              ),
          ),
        )
      }
    }
  }

  private suspend fun executeRequest(request: UploadRequest): Resource {
    return when (request) {
      is UrlUploadRequest -> {
        when (request.httpVerb) {
          HttpVerb.POST -> dataSource.upload(request.url, request.headers, request.payload)
          HttpVerb.PUT -> dataSource.upload(request.url, request.headers, request.payload)
          HttpVerb.PATCH -> dataSource.upload(request.url, request.headers, request.payload)
          HttpVerb.DELETE -> dataSource.upload(request.url, request.headers, request.payload)
          HttpVerb.GET ->
            error("GET is not supported for upload requests")
        }
      }
      is BundleUploadRequest -> {
        dataSource.upload(request.url, request.headers, request.payload)
      }
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
            request.mapping.localChanges.map { localChange ->
              SuccessfulUploadResponseMapping(
                localChange = localChange,
                output = response,
              )
            },
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
    if (bundle == null) {
      return UploadRequestResult.Success(
        successfulUploadResponseMappings =
          request.mappings.flatMap { patchMapping ->
            patchMapping.localChanges.map { localChange ->
              SuccessfulUploadResponseMapping(
                localChange = localChange,
                output = response,
              )
            }
          },
      )
    }

    val entries = bundle.entry
    val mappings = request.mappings

    // Map each patch mapping to the corresponding bundle response entry
    val responseMappings = mutableListOf<SuccessfulUploadResponseMapping>()
    var entryIndex = 0
    for (patchMapping in mappings) {
      val entryResource =
        if (entryIndex < entries.size) {
          entries[entryIndex].resource ?: response
        } else {
          response
        }
      patchMapping.localChanges.forEach { localChange ->
        responseMappings.add(
          SuccessfulUploadResponseMapping(
            localChange = localChange,
            output = entryResource,
          ),
        )
      }
      entryIndex++
    }

    return UploadRequestResult.Success(successfulUploadResponseMappings = responseMappings)
  }
}
