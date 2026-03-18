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
import com.google.android.fhir.sync.upload.patch.StronglyConnectedPatchMappings

/** Generates [UploadRequest]s from [StronglyConnectedPatchMappings]. */
internal interface UploadRequestGenerator {
  /** Generates a list of [UploadRequest]s from the given patch mappings. */
  fun generateUploadRequests(
    mappings: List<StronglyConnectedPatchMappings>,
  ): List<UploadRequest>
}

internal object UploadRequestGeneratorFactory {
  fun byMode(mode: UploadRequestGeneratorMode): UploadRequestGenerator =
    when (mode) {
      is UploadRequestGeneratorMode.UrlRequest ->
        UrlRequestGenerator(
          httpVerbForCreate = mode.httpVerbForCreate,
          httpVerbForUpdate = mode.httpVerbForUpdate,
        )
      is UploadRequestGeneratorMode.BundleRequest ->
        TransactionBundleGenerator(
          httpVerbForCreate = mode.httpVerbForCreate,
          httpVerbForUpdate = mode.httpVerbForUpdate,
          bundleSize = mode.bundleSize,
        )
    }
}

/** Mode to decide the type of [UploadRequestGenerator] to use. */
internal sealed class UploadRequestGeneratorMode {
  data class UrlRequest(
    val httpVerbForCreate: HttpVerb,
    val httpVerbForUpdate: HttpVerb,
  ) : UploadRequestGeneratorMode()

  data class BundleRequest(
    val httpVerbForCreate: HttpVerb,
    val httpVerbForUpdate: HttpVerb,
    val bundleSize: Int,
  ) : UploadRequestGeneratorMode()
}

/** Maps a [UploadRequest] to the [PatchMapping]s that generated it. */
internal sealed class UploadRequestMapping {
  data class UrlUploadRequestMapping(
    val patchMapping: PatchMapping,
    val uploadRequest: UrlUploadRequest,
  ) : UploadRequestMapping()

  data class BundleUploadRequestMapping(
    val patchMappings: List<PatchMapping>,
    val uploadRequest: BundleUploadRequest,
  ) : UploadRequestMapping()
}
