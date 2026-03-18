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

import com.google.android.fhir.sync.upload.patch.PatchMapping

/** A request to upload local changes to the FHIR server. */
internal sealed class UploadRequest(
  open val url: String,
  open val headers: Map<String, String>,
  /** The JSON string payload to upload. */
  open val payload: String,
)

/**
 * A [UploadRequest] that bundles multiple patches into a FHIR Transaction Bundle.
 *
 * @param payload The JSON string of the Bundle resource.
 * @param mappings The patch mappings included in this bundle.
 */
internal data class BundleUploadRequest(
  override val headers: Map<String, String>,
  override val payload: String,
  val mappings: List<PatchMapping>,
) : UploadRequest(url = ".", headers = headers, payload = payload)

/**
 * A [UploadRequest] for uploading a single patch via a direct URL.
 *
 * @param httpVerb The HTTP method to use (PUT, POST, PATCH, DELETE).
 * @param payload The JSON string payload.
 * @param mapping The patch mapping for this request.
 */
internal data class UrlUploadRequest(
  override val url: String,
  val httpVerb: HttpVerb,
  override val headers: Map<String, String>,
  override val payload: String,
  val mapping: PatchMapping,
) : UploadRequest(url = url, headers = headers, payload = payload)

internal enum class HttpVerb {
  GET,
  POST,
  PUT,
  PATCH,
  DELETE,
}
