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

package com.google.android.fhir.sync

import com.google.android.fhir.sync.download.DownloadRequest
import com.google.fhir.model.r4.Resource
import com.google.fhir.model.r4.terminologies.ResourceType

/**
 * Manages the process of downloading FHIR resources from a remote server.
 *
 * Implementations of this interface define how download requests are generated and how responses are
 * processed to update the local database.
 */
interface DownloadWorkManager {
  /** Returns the next [DownloadRequest] to be executed, or `null` if there are no more requests. */
  suspend fun getNextRequest(): DownloadRequest?

  /**
   * Returns a map of [ResourceType] to URLs that can be used to retrieve the total count of
   * resources to be downloaded for each type.
   */
  suspend fun getSummaryRequestUrls(): Map<ResourceType, String>

  /**
   * Processes the [response] received from the FHIR server.
   *
   * @param response The FHIR resource received from the server (typically a Bundle).
   * @return A collection of [Resource]s extracted from the response.
   */
  suspend fun processResponse(response: Resource): Collection<Resource>
}
