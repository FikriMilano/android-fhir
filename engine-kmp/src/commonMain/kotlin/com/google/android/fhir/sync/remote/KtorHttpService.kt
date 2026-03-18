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

import com.google.android.fhir.db.impl.deserializeResource
import com.google.android.fhir.db.impl.serializeResource
import com.google.fhir.model.r4.Resource
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/** Ktor-based implementation of [FhirHttpService] for cross-platform HTTP operations. */
internal class KtorHttpService(
  private val httpClient: HttpClient,
  private val baseUrl: String,
) : FhirHttpService {

  override suspend fun get(path: String, headers: Map<String, String>): Resource {
    val response =
      httpClient.get("$baseUrl/$path") {
        headers {
          headers.forEach { (key, value) -> append(key, value) }
          append("Accept", FHIR_JSON_CONTENT_TYPE)
        }
      }
    return deserializeResource(response.bodyAsText())
  }

  override suspend fun post(
    path: String,
    resource: Resource,
    headers: Map<String, String>,
  ): Resource {
    val response =
      httpClient.post("$baseUrl/$path") {
        headers {
          headers.forEach { (key, value) -> append(key, value) }
          append("Accept", FHIR_JSON_CONTENT_TYPE)
        }
        contentType(ContentType.parse(FHIR_JSON_CONTENT_TYPE))
        setBody(serializeResource(resource))
      }
    return deserializeResource(response.bodyAsText())
  }

  override suspend fun postJson(
    path: String,
    jsonPayload: String,
    headers: Map<String, String>,
  ): Resource {
    val response =
      httpClient.post("$baseUrl/$path") {
        headers {
          headers.forEach { (key, value) -> append(key, value) }
          append("Accept", FHIR_JSON_CONTENT_TYPE)
        }
        contentType(ContentType.parse(FHIR_JSON_CONTENT_TYPE))
        setBody(jsonPayload)
      }
    return deserializeResource(response.bodyAsText())
  }

  override suspend fun put(
    path: String,
    jsonPayload: String,
    headers: Map<String, String>,
  ): Resource {
    val response =
      httpClient.put("$baseUrl/$path") {
        headers {
          headers.forEach { (key, value) -> append(key, value) }
          append("Accept", FHIR_JSON_CONTENT_TYPE)
        }
        contentType(ContentType.parse(FHIR_JSON_CONTENT_TYPE))
        setBody(jsonPayload)
      }
    return deserializeResource(response.bodyAsText())
  }

  override suspend fun patch(
    path: String,
    jsonPayload: String,
    headers: Map<String, String>,
  ): Resource {
    val response =
      httpClient.patch("$baseUrl/$path") {
        headers {
          headers.forEach { (key, value) -> append(key, value) }
          append("Accept", FHIR_JSON_CONTENT_TYPE)
        }
        contentType(ContentType.parse("application/json-patch+json"))
        setBody(jsonPayload)
      }
    return deserializeResource(response.bodyAsText())
  }

  override suspend fun delete(
    path: String,
    headers: Map<String, String>,
  ): Resource {
    val response =
      httpClient.delete("$baseUrl/$path") {
        headers {
          headers.forEach { (key, value) -> append(key, value) }
          append("Accept", FHIR_JSON_CONTENT_TYPE)
        }
      }
    return deserializeResource(response.bodyAsText())
  }

  companion object {
    private const val FHIR_JSON_CONTENT_TYPE = "application/fhir+json"
  }
}
