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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** Ktor implementation of the [FhirHttpService]. */
internal class KtorHttpService(private val client: HttpClient) : FhirHttpService {

  override suspend fun get(path: String, headers: Map<String, String>): Resource {
    return client
      .get(path) { headers { headers.forEach { (k, v) -> append(k, v) } } }
      .body<Resource>()
  }

  override suspend fun post(
    path: String,
    resource: Resource,
    headers: Map<String, String>,
  ): Resource {
    return client
      .post(path) {
        contentType(ContentType.Application.Json)
        headers { headers.forEach { (k, v) -> append(k, v) } }
        setBody(resource)
      }
      .body<Resource>()
  }

  override suspend fun put(
    path: String,
    resource: Resource,
    headers: Map<String, String>,
  ): Resource {
    return client
      .put(path) {
        contentType(ContentType.Application.Json)
        headers { headers.forEach { (k, v) -> append(k, v) } }
        setBody(resource)
      }
      .body<Resource>()
  }

  override suspend fun patch(
    path: String,
    patchDocument: JsonArray,
    headers: Map<String, String>,
  ): Resource {
    return client
      .patch(path) {
        contentType(ContentType.parse("application/json-patch+json"))
        headers { headers.forEach { (k, v) -> append(k, v) } }
        setBody(patchDocument)
      }
      .body<Resource>()
  }

  override suspend fun delete(path: String, headers: Map<String, String>): Resource {
    return client.delete(path) { headers { headers.forEach { (k, v) -> append(k, v) } } }.body()
  }

  companion object {
    fun create(baseUrl: String, json: Json): KtorHttpService {
      val client = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(Logging) {
          level = LogLevel.INFO
          logger =
            object : Logger {
              override fun log(message: String) {
                // We could use HttpLogger here
              }
            }
        }
      }
      return KtorHttpService(client)
    }
  }
}
