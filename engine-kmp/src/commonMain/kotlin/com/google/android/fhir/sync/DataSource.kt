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
import com.google.android.fhir.sync.upload.request.HttpVerb
import com.google.fhir.model.r4.Resource

/**
 * Interface to make HTTP requests to the FHIR server. Implementations of this interface must
 * provide the mechanism for making these calls.
 */
internal interface DataSource {
  /**
   * Downloads a FHIR resource given a [DownloadRequest].
   *
   * @return The downloaded [Resource] (typically a Bundle).
   */
  suspend fun download(downloadRequest: DownloadRequest): Resource

  /**
   * Uploads a FHIR resource to the server.
   *
   * @param url The relative URL for the upload request.
   * @param headers Additional HTTP headers for the request.
   * @param payload The JSON string payload to upload.
   * @return The server's response [Resource].
   */
  suspend fun upload(
    url: String,
    httpVerb: HttpVerb,
    headers: Map<String, String>,
    payload: String,
  ): Resource
}
