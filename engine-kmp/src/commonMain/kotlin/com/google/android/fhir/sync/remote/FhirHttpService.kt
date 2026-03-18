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

import com.google.fhir.model.r4.Resource

/** Interface for performing HTTP operations against a FHIR server. */
internal interface FhirHttpService {
  /**
   * Makes a HTTP GET request to the FHIR server.
   *
   * @param path The URL path relative to the FHIR server base URL.
   * @param headers Additional headers to include in the request.
   * @return The FHIR [Resource] returned by the server (typically a Bundle).
   */
  suspend fun get(path: String, headers: Map<String, String>): Resource

  /**
   * Makes a HTTP POST request to the FHIR server.
   *
   * @param path The URL path relative to the FHIR server base URL.
   * @param resource The FHIR [Resource] to send as the request body.
   * @param headers Additional headers to include in the request.
   * @return The FHIR [Resource] returned by the server.
   */
  suspend fun post(path: String, resource: Resource, headers: Map<String, String>): Resource

  /**
   * Makes a HTTP POST request to the FHIR server with a raw JSON string payload.
   *
   * @param path The URL path relative to the FHIR server base URL.
   * @param jsonPayload The JSON string to send as the request body.
   * @param headers Additional headers to include in the request.
   * @return The FHIR [Resource] returned by the server.
   */
  suspend fun postJson(path: String, jsonPayload: String, headers: Map<String, String>): Resource

  /**
   * Makes a HTTP PUT request to the FHIR server.
   *
   * @param path The URL path relative to the FHIR server base URL.
   * @param jsonPayload The JSON string to send as the request body.
   * @param headers Additional headers to include in the request.
   * @return The FHIR [Resource] returned by the server.
   */
  suspend fun put(path: String, jsonPayload: String, headers: Map<String, String>): Resource

  /**
   * Makes a HTTP PATCH request to the FHIR server.
   *
   * @param path The URL path relative to the FHIR server base URL.
   * @param jsonPayload The JSON patch payload to send as the request body.
   * @param headers Additional headers to include in the request.
   * @return The FHIR [Resource] returned by the server.
   */
  suspend fun patch(path: String, jsonPayload: String, headers: Map<String, String>): Resource

  /**
   * Makes a HTTP DELETE request to the FHIR server.
   *
   * @param path The URL path relative to the FHIR server base URL.
   * @param headers Additional headers to include in the request.
   * @return The FHIR [Resource] returned by the server.
   */
  suspend fun delete(path: String, headers: Map<String, String>): Resource
}
