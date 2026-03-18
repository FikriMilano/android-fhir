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

import com.google.android.fhir.sync.upload.ResourceSyncException
import kotlin.time.Clock
import kotlin.time.Instant

/** Enum representing the type of synchronization operation. */
enum class SyncOperation {
  DOWNLOAD,
  UPLOAD,
}

/** Sealed class representing different states of a synchronization operation. */
sealed class SyncJobStatus {
  val timestamp: Instant = Clock.System.now()

  /** Sync job has been started on the client but the syncing is not necessarily in progress. */
  class Started : SyncJobStatus()

  /** Syncing in progress with the server. */
  data class InProgress(
    val syncOperation: SyncOperation,
    val total: Int = 0,
    val completed: Int = 0,
  ) : SyncJobStatus()

  /** Sync job finished successfully. */
  class Succeeded : SyncJobStatus()

  /** Sync job failed. */
  data class Failed(val exceptions: List<ResourceSyncException>) : SyncJobStatus()
}

/**
 * Data class representing the state of a periodic synchronization operation.
 *
 * @property lastSyncJobStatus The result of the last synchronization job.
 * @property currentSyncJobStatus The current state of the synchronization job.
 */
data class PeriodicSyncJobStatus(
  val lastSyncJobStatus: LastSyncJobStatus?,
  val currentSyncJobStatus: CurrentSyncJobStatus,
)

/**
 * Sealed class representing the result of a synchronization operation. These are terminal states of
 * the sync operation.
 *
 * @property timestamp The timestamp when the synchronization result occurred.
 */
sealed class LastSyncJobStatus(val timestamp: Instant) {
  /** Represents a successful synchronization result. */
  class Succeeded(timestamp: Instant) : LastSyncJobStatus(timestamp)

  /** Represents a failed synchronization result. */
  class Failed(timestamp: Instant) : LastSyncJobStatus(timestamp)
}

/** Sealed class representing the current state of a synchronization job. */
sealed class CurrentSyncJobStatus {
  /** State indicating that the synchronization operation is enqueued. */
  data object Enqueued : CurrentSyncJobStatus()

  /**
   * State indicating that the synchronization operation is running.
   *
   * @param inProgressSyncJob The current status of the synchronization job.
   */
  data class Running(val inProgressSyncJob: SyncJobStatus) : CurrentSyncJobStatus()

  /**
   * State indicating that the synchronization operation succeeded.
   *
   * @param timestamp The timestamp when the synchronization result occurred.
   */
  class Succeeded(val timestamp: Instant) : CurrentSyncJobStatus()

  /**
   * State indicating that the synchronization operation failed.
   *
   * @param timestamp The timestamp when the synchronization result occurred.
   */
  class Failed(val timestamp: Instant) : CurrentSyncJobStatus()

  /** State indicating that the synchronization operation is canceled. */
  data object Cancelled : CurrentSyncJobStatus()

  /** State indicating that the synchronization operation is blocked. */
  data object Blocked : CurrentSyncJobStatus()
}
