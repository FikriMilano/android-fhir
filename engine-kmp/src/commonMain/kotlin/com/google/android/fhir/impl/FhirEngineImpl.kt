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

package com.google.android.fhir.impl

import com.google.android.fhir.FhirEngine
import com.google.android.fhir.LocalChange
import com.google.android.fhir.SearchResult
import com.google.android.fhir.db.Database
import com.google.android.fhir.db.LocalChangeResourceReference
import com.google.android.fhir.search.Search
import com.google.android.fhir.search.count
import com.google.android.fhir.search.execute
import com.google.android.fhir.sync.ConflictResolver
import com.google.android.fhir.sync.upload.SyncUploadProgress
import com.google.android.fhir.sync.upload.UploadRequestResult
import com.google.android.fhir.sync.upload.UploadStrategy
import com.google.fhir.model.r4.Resource
import com.google.fhir.model.r4.terminologies.ResourceType
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Implementation of [FhirEngine] backed by a [Database]. Provides the minimum operations needed for
 * the vertical slice: create, get, syncDownload, and clearDatabase.
 */
internal class FhirEngineImpl(private val database: Database) : FhirEngine {

  override suspend fun create(vararg resource: Resource): List<String> {
    return database.insert(*resource)
  }

  override suspend fun get(type: ResourceType, id: String): Resource {
    return database.select(type, id)
  }

  override suspend fun update(vararg resource: Resource) {
    database.update(*resource)
  }

  override suspend fun delete(type: ResourceType, id: String) {
    database.delete(type, id)
  }

  override suspend fun <R : Resource> search(search: Search): List<SearchResult<R>> {
    return search.execute(database)
  }

  override suspend fun syncUpload(
    uploadStrategy: UploadStrategy,
    upload:
      (suspend (List<LocalChange>, List<LocalChangeResourceReference>) -> Flow<
          UploadRequestResult,
        >),
  ): Flow<SyncUploadProgress> {
    throw NotImplementedError("syncUpload() is not yet implemented in the vertical slice")
  }

  override suspend fun syncDownload(
    conflictResolver: ConflictResolver,
    download: suspend () -> Flow<List<Resource>>,
  ) {
    download().collect { resources ->
      database.withTransaction { database.insertSyncedResources(resources) }
    }
  }

  override suspend fun count(search: Search): Long {
    return search.count(database)
  }

  override suspend fun getLastSyncTimeStamp(): Instant? {
    // TODO: implement with FhirDataStore
    return null
  }

  override suspend fun clearDatabase() {
    database.clearDatabase()
  }

  override suspend fun getLocalChanges(type: ResourceType, id: String): List<LocalChange> {
    return database.getLocalChanges(type, id)
  }

  override suspend fun purge(type: ResourceType, id: String, forcePurge: Boolean) {
    database.purge(type, setOf(id), forcePurge)
  }

  override suspend fun purge(type: ResourceType, ids: Set<String>, forcePurge: Boolean) {
    database.purge(type, ids, forcePurge)
  }

  override suspend fun withTransaction(block: suspend FhirEngine.() -> Unit) {
    database.withTransaction { block() }
  }
}
