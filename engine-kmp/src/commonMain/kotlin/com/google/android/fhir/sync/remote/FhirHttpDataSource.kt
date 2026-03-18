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

package com.google.android.fhir.sync.remote

import com.google.android.fhir.sync.DataSource
import com.google.android.fhir.sync.download.DownloadRequest
import com.google.android.fhir.sync.upload.request.HttpVerb
import com.google.fhir.model.r4.Resource

/** [DataSource] implementation backed by [FhirHttpService]. */
internal class FhirHttpDataSource(private val fhirHttpService: FhirHttpService) : DataSource {

  override suspend fun download(downloadRequest: DownloadRequest): Resource {
    return when (downloadRequest) {
      is DownloadRequest.UrlDownloadRequest ->
        fhirHttpService.get(downloadRequest.url, downloadRequest.headers)
      is DownloadRequest.BundleDownloadRequest ->
        fhirHttpService.post("", downloadRequest.bundle, downloadRequest.headers)
    }
  }

  override suspend fun upload(
    url: String,
    httpVerb: HttpVerb,
    headers: Map<String, String>,
    payload: String,
  ): Resource {
    return when (httpVerb) {
      HttpVerb.POST -> fhirHttpService.postJson(url, payload, headers)
      HttpVerb.PUT -> fhirHttpService.put(url, payload, headers)
      HttpVerb.PATCH -> fhirHttpService.patch(url, payload, headers)
      HttpVerb.DELETE -> fhirHttpService.delete(url, headers)
      HttpVerb.GET -> error("GET is not supported for upload requests")
    }
  }
}
