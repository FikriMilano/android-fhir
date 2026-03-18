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

package com.google.android.fhir.sync.download

import com.google.android.fhir.sync.upload.ResourceSyncException
import com.google.fhir.model.r4.Resource
import com.google.fhir.model.r4.terminologies.ResourceType
import kotlinx.coroutines.flow.Flow

/** Interface for downloading FHIR resources from a remote server. */
internal interface Downloader {
  /** Downloads resources from the server and emits [DownloadState] updates. */
  suspend fun download(): Flow<DownloadState>
}

/** Represents the state of a download operation. */
sealed class DownloadState {
  /** Download has started for the given [type] with a [total] count of resources. */
  data class Started(val type: ResourceType, val total: Int) : DownloadState()

  /** A batch of [resources] has been successfully downloaded. */
  data class Success(
    val resources: List<Resource>,
    val total: Int,
    val completed: Int,
  ) : DownloadState()

  /** A download failure has occurred. */
  data class Failure(val syncError: ResourceSyncException) : DownloadState()
}
