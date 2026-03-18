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

import co.touchlab.kermit.Logger
import com.google.android.fhir.sync.download.Downloader
import com.google.android.fhir.sync.upload.ResourceConsolidator
import com.google.android.fhir.sync.upload.ResourceSyncException
import com.google.android.fhir.sync.upload.Uploader
import com.google.android.fhir.sync.upload.UploadRequestResult
import com.google.android.fhir.sync.upload.LocalChangeFetcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Orchestrates FHIR synchronization by coordinating download and upload operations. */
internal class FhirSynchronizer(
  private val uploader: Uploader,
  private val resourceConsolidator: ResourceConsolidator,
  private val localChangeFetcher: LocalChangeFetcher,
) {

  private val _syncState = MutableSharedFlow<SyncJobStatus>()
  val syncState: SharedFlow<SyncJobStatus> = _syncState.asSharedFlow()
  private val mutex = Mutex()

  /** Runs the upload synchronization, emitting [SyncJobStatus] updates. */
  suspend fun upload(): SyncJobStatus {
    return mutex.withLock {
      _syncState.emit(SyncJobStatus.Started())

      try {
        val result = performUpload()
        _syncState.emit(result)
        result
      } catch (e: Exception) {
        Logger.e("FhirSynchronizer") { "Upload failed: ${e.message}" }
        val failed =
          SyncJobStatus.Failed(
            exceptions =
              listOf(
                ResourceSyncException(
                  resourceType = "Unknown",
                  exception = e,
                ),
              ),
          )
        _syncState.emit(failed)
        failed
      }
    }
  }

  private suspend fun performUpload(): SyncJobStatus {
    var totalUploaded = 0
    val errors = mutableListOf<ResourceSyncException>()

    while (localChangeFetcher.hasNext()) {
      val (localChanges, references) = localChangeFetcher.next()

      _syncState.emit(
        SyncJobStatus.InProgress(
          syncOperation = SyncOperation.UPLOAD,
          total = localChangeFetcher.total,
          completed = totalUploaded,
        ),
      )

      uploader.upload(localChanges, references).collect { result ->
        resourceConsolidator.consolidate(result)
        when (result) {
          is UploadRequestResult.Success -> {
            totalUploaded += result.successfulUploadResponseMappings.size
          }
          is UploadRequestResult.Failure -> {
            errors.add(result.uploadError)
          }
        }
      }
    }

    return if (errors.isEmpty()) {
      SyncJobStatus.Succeeded()
    } else {
      SyncJobStatus.Failed(exceptions = errors)
    }
  }
}
