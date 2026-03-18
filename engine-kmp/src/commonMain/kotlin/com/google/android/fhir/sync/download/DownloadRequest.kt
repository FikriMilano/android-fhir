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

import com.google.fhir.model.r4.Resource

/** Represents a request to download FHIR resources from a server. */
sealed class DownloadRequest(open val headers: Map<String, String>) {

  /** A download request specified by a URL path. */
  data class UrlDownloadRequest(
    val url: String,
    override val headers: Map<String, String> = emptyMap(),
  ) : DownloadRequest(headers)

  /** A download request specified by a FHIR resource (e.g. a Bundle for batch requests). */
  data class BundleDownloadRequest(
    val bundle: Resource,
    override val headers: Map<String, String> = emptyMap(),
  ) : DownloadRequest(headers)

  companion object {
    /** Creates a [UrlDownloadRequest] from a URL string. */
    fun of(url: String, headers: Map<String, String> = emptyMap()): DownloadRequest =
      UrlDownloadRequest(url, headers)
  }
}
