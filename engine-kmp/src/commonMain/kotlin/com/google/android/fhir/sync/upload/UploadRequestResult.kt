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

import com.google.android.fhir.LocalChange
import com.google.fhir.model.r4.Resource

/** The result of an upload request to the FHIR server. */
sealed class UploadRequestResult {
  data class Success(
    val successfulUploadResponseMappings: List<SuccessfulUploadResponseMapping>,
  ) : UploadRequestResult()

  data class Failure(
    val localChanges: List<LocalChange>,
    val uploadError: ResourceSyncException,
  ) : UploadRequestResult()
}

/**
 * Maps local changes to the server's response after a successful upload.
 *
 * This is a sealed class with subtypes for different response formats:
 * - [ResourceUploadResponseMapping] for individual resource responses (URL requests)
 * - [BundleComponentUploadResponseMapping] for bundle entry responses
 */
sealed class SuccessfulUploadResponseMapping(
  open val localChanges: List<LocalChange>,
)

/** Maps local changes to a [Resource] response from the server. */
internal data class ResourceUploadResponseMapping(
  override val localChanges: List<LocalChange>,
  val output: Resource,
) : SuccessfulUploadResponseMapping(localChanges)

/**
 * Maps local changes to a bundle entry response containing metadata (etag, location, lastModified).
 *
 * Used for transaction bundle responses where entries may not contain full resources but instead
 * include response metadata for version tracking and ID resolution.
 */
internal data class BundleComponentUploadResponseMapping(
  override val localChanges: List<LocalChange>,
  val etag: String?,
  val location: String?,
  val lastModified: String?,
) : SuccessfulUploadResponseMapping(localChanges)
