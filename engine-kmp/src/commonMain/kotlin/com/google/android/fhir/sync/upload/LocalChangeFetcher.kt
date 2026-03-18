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

package com.google.android.fhir.sync.upload

import com.google.android.fhir.LocalChange
import com.google.android.fhir.db.Database
import com.google.android.fhir.db.LocalChangeResourceReference

/** Fetches local changes from the database in batches for upload. */
internal interface LocalChangeFetcher {
  /** The total number of local changes to upload. */
  val total: Int

  /** Returns `true` if there are more local changes to fetch. */
  suspend fun hasNext(): Boolean

  /** Fetches the next batch of local changes and their resource references. */
  suspend fun next(): Pair<List<LocalChange>, List<LocalChangeResourceReference>>

  /** Returns the current upload progress. */
  fun getProgress(uploadError: ResourceSyncException? = null): SyncUploadProgress
}

/** Fetches all local changes at once. */
internal class AllChangesLocalChangeFetcher(
  private val database: Database,
) : LocalChangeFetcher {

  private var localChanges: List<LocalChange>? = null
  private var fetched = false

  override val total: Int
    get() = localChanges?.size ?: 0

  override suspend fun hasNext(): Boolean {
    if (localChanges == null) {
      localChanges = database.getAllLocalChanges()
    }
    return !fetched && localChanges!!.isNotEmpty()
  }

  override suspend fun next(): Pair<List<LocalChange>, List<LocalChangeResourceReference>> {
    if (localChanges == null) {
      localChanges = database.getAllLocalChanges()
    }
    fetched = true
    val changes = localChanges!!
    val references =
      database.getLocalChangeResourceReferences(changes.flatMap { it.token.ids })
    return changes to references
  }

  override fun getProgress(uploadError: ResourceSyncException?): SyncUploadProgress {
    val remaining = if (fetched) 0 else (localChanges?.size ?: 0)
    return SyncUploadProgress(
      remaining = remaining,
      initialTotal = total,
      uploadError = uploadError,
    )
  }
}

/** Fetches local changes per earliest changed resource. */
internal class PerResourceLocalChangeFetcher(
  private val database: Database,
) : LocalChangeFetcher {

  private var _total: Int = 0
  private var uploaded: Int = 0
  private var initialized = false

  override val total: Int
    get() = _total

  override suspend fun hasNext(): Boolean {
    if (!initialized) {
      _total = database.getLocalChangesCount()
      initialized = true
    }
    return uploaded < _total
  }

  override suspend fun next(): Pair<List<LocalChange>, List<LocalChangeResourceReference>> {
    val changes = database.getAllChangesForEarliestChangedResource()
    val references =
      database.getLocalChangeResourceReferences(changes.flatMap { it.token.ids })
    uploaded += changes.size
    return changes to references
  }

  override fun getProgress(uploadError: ResourceSyncException?): SyncUploadProgress {
    return SyncUploadProgress(
      remaining = _total - uploaded,
      initialTotal = _total,
      uploadError = uploadError,
    )
  }
}

/** Mode for fetching local changes. */
internal sealed class LocalChangesFetchMode {
  data object AllChanges : LocalChangesFetchMode()

  data object PerResource : LocalChangesFetchMode()

  data object EarliestChange : LocalChangesFetchMode()
}

internal object LocalChangeFetcherFactory {
  fun byMode(mode: LocalChangesFetchMode, database: Database): LocalChangeFetcher =
    when (mode) {
      is LocalChangesFetchMode.AllChanges -> AllChangesLocalChangeFetcher(database)
      is LocalChangesFetchMode.PerResource -> PerResourceLocalChangeFetcher(database)
      is LocalChangesFetchMode.EarliestChange -> PerResourceLocalChangeFetcher(database)
    }
}
