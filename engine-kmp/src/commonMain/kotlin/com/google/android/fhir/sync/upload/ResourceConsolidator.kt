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
import com.google.android.fhir.db.Database
import com.google.android.fhir.sync.upload.request.HttpVerb
import com.google.fhir.model.r4.terminologies.ResourceType
import kotlin.time.Instant

/** Consolidates resources in the local database after a successful upload. */
internal fun interface ResourceConsolidator {
  suspend fun consolidate(uploadRequestResult: UploadRequestResult)
}

/**
 * Default consolidator that updates version IDs and last updated timestamps after a successful
 * upload, and deletes the local change tokens.
 */
internal class DefaultResourceConsolidator(
  private val database: Database,
) : ResourceConsolidator {

  override suspend fun consolidate(uploadRequestResult: UploadRequestResult) {
    when (uploadRequestResult) {
      is UploadRequestResult.Success -> {
        uploadRequestResult.successfulUploadResponseMappings.forEach { mapping ->
          val versionId = mapping.output.meta?.versionId?.value
          val lastUpdated =
            mapping.output.meta?.lastUpdated?.value?.toString()?.let {
              Instant.parse(it)
            }
          if (versionId != null && lastUpdated != null) {
            database.updateVersionIdAndLastUpdated(
              mapping.localChange.resourceId,
              ResourceType.valueOf(mapping.localChange.resourceType),
              versionId,
              lastUpdated,
            )
          }
          database.deleteUpdates(mapping.localChange.token)
        }
      }
      is UploadRequestResult.Failure -> {
        Logger.w("DefaultResourceConsolidator") {
          "Upload failed for ${uploadRequestResult.localChanges.size} changes: " +
            "${uploadRequestResult.uploadError.exception.message}"
        }
      }
    }
  }
}

/**
 * Consolidator for HTTP POST creates that also handles resource ID updates when the server assigns
 * a different ID.
 */
internal class HttpPostResourceConsolidator(
  private val database: Database,
) : ResourceConsolidator {

  override suspend fun consolidate(uploadRequestResult: UploadRequestResult) {
    when (uploadRequestResult) {
      is UploadRequestResult.Success -> {
        uploadRequestResult.successfulUploadResponseMappings.forEach { mapping ->
          val outputId = mapping.output.id
          val localId = mapping.localChange.resourceId

          // If server assigned a different ID, update references
          if (outputId != null && outputId != localId) {
            database.updateResourceAndReferences(
              currentResourceId = localId,
              updatedResource = mapping.output,
            )
          }

          val versionId = mapping.output.meta?.versionId?.value
          val lastUpdated =
            mapping.output.meta?.lastUpdated?.value?.toString()?.let {
              Instant.parse(it)
            }
          if (versionId != null && lastUpdated != null) {
            database.updateVersionIdAndLastUpdated(
              mapping.localChange.resourceId,
              ResourceType.valueOf(mapping.localChange.resourceType),
              versionId,
              lastUpdated,
            )
          }
          database.deleteUpdates(mapping.localChange.token)
        }
      }
      is UploadRequestResult.Failure -> {
        Logger.w("HttpPostResourceConsolidator") {
          "Upload failed for ${uploadRequestResult.localChanges.size} changes: " +
            "${uploadRequestResult.uploadError.exception.message}"
        }
      }
    }
  }
}

internal object ResourceConsolidatorFactory {
  fun byHttpVerb(httpVerb: HttpVerb, database: Database): ResourceConsolidator =
    when (httpVerb) {
      HttpVerb.POST -> HttpPostResourceConsolidator(database)
      else -> DefaultResourceConsolidator(database)
    }
}
