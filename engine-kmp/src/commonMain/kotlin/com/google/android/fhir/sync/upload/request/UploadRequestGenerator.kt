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

import com.google.android.fhir.sync.upload.patch.Patch
import com.google.android.fhir.sync.upload.patch.PatchMapping
import com.google.android.fhir.sync.upload.patch.StronglyConnectedPatchMappings

/**
 * Generator that generates [UploadRequest]s from the [Patch]es present in the
 * [List<[StronglyConnectedPatchMappings]>].
 */
internal interface UploadRequestGenerator {
  /** Generates a list of [UploadRequest]s from the [PatchMapping]s */
  fun generateUploadRequests(
    mappedPatches: List<StronglyConnectedPatchMappings>,
  ): List<UploadRequest>
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
    val bundleSize: Int = 500,
  ) : UploadRequestGeneratorMode()
}

internal object UploadRequestGeneratorFactory {
  fun byMode(
    mode: UploadRequestGeneratorMode,
  ): UploadRequestGenerator =
    when (mode) {
      is UploadRequestGeneratorMode.UrlRequest ->
        UrlRequestGenerator.getGenerator(mode.httpVerbForCreate, mode.httpVerbForUpdate)
      is UploadRequestGeneratorMode.BundleRequest ->
        TransactionBundleGenerator.getGenerator(
          mode.httpVerbForCreate,
          mode.httpVerbForUpdate,
          mode.bundleSize,
        )
    }
}
