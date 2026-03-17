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

package com.google.android.fhir.db.impl

import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.android.fhir.LocalChange
import com.google.android.fhir.LocalChangeToken
import com.google.android.fhir.db.Database
import com.google.android.fhir.db.LocalChangeResourceReference
import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.db.ResourceWithUUID
import com.google.android.fhir.search.ReferencedResourceResult
import com.google.android.fhir.db.impl.entities.DateIndexEntity
import com.google.android.fhir.db.impl.entities.DateTimeIndexEntity
import com.google.android.fhir.db.impl.entities.NumberIndexEntity
import com.google.android.fhir.db.impl.entities.PositionIndexEntity
import com.google.android.fhir.db.impl.entities.QuantityIndexEntity
import com.google.android.fhir.db.impl.entities.ReferenceIndexEntity
import com.google.android.fhir.db.impl.entities.ResourceEntity
import com.google.android.fhir.db.impl.entities.StringIndexEntity
import com.google.android.fhir.db.impl.entities.TokenIndexEntity
import com.google.android.fhir.db.impl.entities.UriIndexEntity
import com.google.android.fhir.index.ResourceIndexer
import com.google.android.fhir.index.ResourceIndices
import com.google.android.fhir.resourceType
import com.google.android.fhir.search.SearchQuery
import com.google.fhir.model.r4.Resource
import com.google.fhir.model.r4.terminologies.ResourceType
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * The implementation for the persistence layer using Room KMP. Provides the minimum operations
 * needed for the vertical slice: insert, select, insertRemote, and insertSyncedResources.
 *
 * Note: Room KMP does not provide `withTransaction` as an extension function. For the vertical
 * slice, DAO calls are made sequentially without explicit transaction wrapping. Full transaction
 * support will be added when the database layer is widened.
 */
internal class DatabaseImpl(
  platformContext: Any,
  private val resourceIndexer: ResourceIndexer,
) : Database {

  private val db: ResourceDatabase =
    getDatabaseBuilder(platformContext)
      .setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(Dispatchers.IO)
      .build()

  private val resourceDao by lazy { db.resourceDao() }

  override suspend fun <R : Resource> insert(vararg resource: R): List<String> {
    return resource.map { res ->
      val resourceId = res.id ?: Uuid.random().toString()
      val resourceUuid = Uuid.random().toString()
      val resourceTypeName = res.resourceType
      val now = Clock.System.now().toEpochMilliseconds()

      // If the resource has no id, create a copy with the generated id via JSON round-trip
      val resourceWithId =
        if (res.id == null) {
          val json = serializeResource(res)
          val jsonWithId = ensureIdInJson(json, resourceId)
          deserializeResource(jsonWithId)
        } else {
          res
        }

      val entity =
        ResourceEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceTypeName,
          resourceId = resourceId,
          serializedResource = serializeResource(resourceWithId),
          versionId = null,
          lastUpdatedRemote = null,
          lastUpdatedLocal = now,
        )
      resourceDao.insertResource(entity)

      val indices = resourceIndexer.index(resourceWithId)
      insertIndices(resourceUuid, resourceTypeName, indices)

      resourceId
    }
  }

  override suspend fun <R : Resource> insertRemote(vararg resource: R) {
    resource.forEach { res ->
      val resourceId = res.id ?: error("Remote resource must have an id")
      val resourceUuid = Uuid.random().toString()
      val resourceTypeName = res.resourceType
      val now = Clock.System.now().toEpochMilliseconds()

      val entity =
        ResourceEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceTypeName,
          resourceId = resourceId,
          serializedResource = serializeResource(res),
          versionId = null,
          lastUpdatedRemote = now,
          lastUpdatedLocal = now,
        )
      resourceDao.insertResource(entity)

      val indices = resourceIndexer.index(res)
      insertIndices(resourceUuid, resourceTypeName, indices)
    }
  }

  override suspend fun select(type: ResourceType, id: String): Resource {
    val json = resourceDao.getResource(resourceId = id, resourceType = type.name)
    return if (json != null) {
      deserializeResource(json)
    } else {
      throw ResourceNotFoundException(type.name, id)
    }
  }

  override suspend fun insertSyncedResources(resources: List<Resource>) {
    insertRemote(*resources.toTypedArray())
  }

  override suspend fun withTransaction(block: suspend () -> Unit) {
    // TODO: Implement proper transaction support with Room KMP useWriterConnection
    block()
  }

  override fun close() {
    db.close()
  }

  override suspend fun clearDatabase() {
    resourceDao.deleteAllResources()
  }

  // --- Methods not needed for the vertical slice ---

  override suspend fun update(vararg resources: Resource) {
    resources.forEach { res ->
      val resourceId = res.id ?: error("Resource must have an id to be updated")
      val resourceTypeName = res.resourceType
      val existing =
        resourceDao.getResourceEntity(resourceId = resourceId, resourceType = resourceTypeName)
          ?: throw ResourceNotFoundException(resourceTypeName, resourceId)

      val now = Clock.System.now().toEpochMilliseconds()
      val updatedEntity =
        existing.copy(
          serializedResource = serializeResource(res),
          lastUpdatedLocal = now,
        )
      resourceDao.insertResource(updatedEntity)

      val indices = resourceIndexer.index(res)
      insertIndices(existing.resourceUuid, resourceTypeName, indices)
    }
  }

  override suspend fun updateVersionIdAndLastUpdated(
    resourceId: String,
    resourceType: ResourceType,
    versionId: String?,
    lastUpdated: Instant?,
  ) {
    throw NotImplementedError(
      "updateVersionIdAndLastUpdated() is not yet implemented in the vertical slice",
    )
  }

  override suspend fun updateResourcePostSync(
    oldResourceId: String,
    newResourceId: String,
    resourceType: ResourceType,
    versionId: String?,
    lastUpdated: Instant?,
  ) {
    throw NotImplementedError(
      "updateResourcePostSync() is not yet implemented in the vertical slice",
    )
  }

  override suspend fun delete(type: ResourceType, id: String) {
    resourceDao.deleteResource(resourceId = id, resourceType = type.name)
  }

  @Suppress("UNCHECKED_CAST")
  override suspend fun <R : Resource> search(query: SearchQuery): List<ResourceWithUUID<R>> {
    return db.useReaderConnection { transactor ->
      transactor.usePrepared(query.query) { statement ->
        bindArgs(statement, query.args)
        val results = mutableListOf<ResourceWithUUID<R>>()
        while (statement.step()) {
          val uuid = statement.getText(0)
          val json = statement.getText(1)
          val resource = deserializeResource(json) as R
          results.add(ResourceWithUUID(Uuid.parse(uuid), resource))
        }
        results
      }
    }
  }

  override suspend fun count(query: SearchQuery): Long {
    return db.useReaderConnection { transactor ->
      transactor.usePrepared(query.query) { statement ->
        bindArgs(statement, query.args)
        if (statement.step()) {
          statement.getLong(0)
        } else {
          0L
        }
      }
    }
  }

  override suspend fun searchReferencedResources(
    query: SearchQuery,
  ): List<ReferencedResourceResult> {
    return db.useReaderConnection { transactor ->
      transactor.usePrepared(query.query) { statement ->
        bindArgs(statement, query.args)
        val results = mutableListOf<ReferencedResourceResult>()
        while (statement.step()) {
          val searchIndex = statement.getText(0)
          val baseId = statement.getText(1)
          val json = statement.getText(2)
          val resource = deserializeResource(json)
          results.add(ReferencedResourceResult(searchIndex, baseId, resource))
        }
        results
      }
    }
  }

  override suspend fun getAllLocalChanges(): List<LocalChange> {
    throw NotImplementedError("getAllLocalChanges() is not yet implemented in the vertical slice")
  }

  override suspend fun getAllChangesForEarliestChangedResource(): List<LocalChange> {
    throw NotImplementedError(
      "getAllChangesForEarliestChangedResource() is not yet implemented in the vertical slice",
    )
  }

  override suspend fun getLocalChangesCount(): Int {
    throw NotImplementedError("getLocalChangesCount() is not yet implemented in the vertical slice")
  }

  override suspend fun deleteUpdates(token: LocalChangeToken) {
    throw NotImplementedError("deleteUpdates() is not yet implemented in the vertical slice")
  }

  override suspend fun deleteUpdates(resources: List<Resource>) {
    throw NotImplementedError("deleteUpdates() is not yet implemented in the vertical slice")
  }

  override suspend fun updateResourceAndReferences(
    currentResourceId: String,
    updatedResource: Resource,
  ) {
    throw NotImplementedError(
      "updateResourceAndReferences() is not yet implemented in the vertical slice",
    )
  }

  override suspend fun getLocalChanges(type: ResourceType, id: String): List<LocalChange> {
    throw NotImplementedError("getLocalChanges() is not yet implemented in the vertical slice")
  }

  override suspend fun getLocalChanges(resourceUuid: Uuid): List<LocalChange> {
    throw NotImplementedError("getLocalChanges() is not yet implemented in the vertical slice")
  }

  override suspend fun purge(type: ResourceType, ids: Set<String>, forcePurge: Boolean) {
    throw NotImplementedError("purge() is not yet implemented in the vertical slice")
  }

  override suspend fun getLocalChangeResourceReferences(
    localChangeIds: List<Long>,
  ): List<LocalChangeResourceReference> {
    throw NotImplementedError(
      "getLocalChangeResourceReferences() is not yet implemented in the vertical slice",
    )
  }

  // --- Private helpers ---

  private fun bindArgs(statement: SQLiteStatement, args: List<Any>) {
    args.forEachIndexed { i, arg ->
      when (arg) {
        is String -> statement.bindText(i + 1, arg)
        is Long -> statement.bindLong(i + 1, arg)
        is Double -> statement.bindDouble(i + 1, arg)
        is Int -> statement.bindLong(i + 1, arg.toLong())
        else -> statement.bindText(i + 1, arg.toString())
      }
    }
  }

  /** Injects an `"id"` field into a JSON resource string if not already present. */
  private fun ensureIdInJson(json: String, id: String): String {
    val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(json)
    if (jsonObj is kotlinx.serialization.json.JsonObject && "id" !in jsonObj) {
      val mutable = jsonObj.toMutableMap()
      mutable["id"] = kotlinx.serialization.json.JsonPrimitive(id)
      return kotlinx.serialization.json.Json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        kotlinx.serialization.json.JsonObject(mutable),
      )
    }
    return json
  }

  private suspend fun insertIndices(
    resourceUuid: String,
    resourceType: String,
    indices: ResourceIndices,
  ) {
    indices.stringIndices.forEach {
      resourceDao.insertStringIndex(
        StringIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
    indices.referenceIndices.forEach {
      resourceDao.insertReferenceIndex(
        ReferenceIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
    indices.tokenIndices.forEach {
      resourceDao.insertTokenIndex(
        TokenIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
    indices.quantityIndices.forEach {
      resourceDao.insertQuantityIndex(
        QuantityIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
    indices.uriIndices.forEach {
      resourceDao.insertUriIndex(
        UriIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
    indices.dateIndices.forEach {
      resourceDao.insertDateIndex(
        DateIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
    indices.dateTimeIndices.forEach {
      resourceDao.insertDateTimeIndex(
        DateTimeIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
    indices.numberIndices.forEach {
      resourceDao.insertNumberIndex(
        NumberIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
    indices.positionIndices.forEach {
      resourceDao.insertPositionIndex(
        PositionIndexEntity(
          id = 0,
          resourceUuid = resourceUuid,
          resourceType = resourceType,
          index = it,
        ),
      )
    }
  }
}
