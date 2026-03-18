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

import com.google.android.fhir.sync.upload.patch.PatchGeneratorMode
import com.google.android.fhir.sync.upload.request.HttpVerb
import com.google.android.fhir.sync.upload.request.UploadRequestGeneratorMode

/** Defines the strategy for uploading FHIR resources to a server. */
class UploadStrategy
internal constructor(
  internal val localChangesFetchMode: LocalChangesFetchMode,
  internal val patchGeneratorMode: PatchGeneratorMode,
  internal val requestGeneratorMode: UploadRequestGeneratorMode,
) {
  companion object {
    /**
     * Creates an [UploadStrategy] that bundles changes into FHIR Transaction Bundles.
     *
     * @param methodForCreate The HTTP method for resource creation (PUT or POST).
     * @param methodForUpdate The HTTP method for resource updates (PUT or PATCH).
     * @param squash If `true`, squashes multiple changes to the same resource into one.
     * @param bundleSize The maximum number of entries per bundle.
     */
    fun forBundleRequest(
      methodForCreate: HttpCreateMethod = HttpCreateMethod.PUT,
      methodForUpdate: HttpUpdateMethod = HttpUpdateMethod.PATCH,
      squash: Boolean = true,
      bundleSize: Int = 500,
    ): UploadStrategy {
      require(squash) {
        "Bundle requests without squashing are not supported. " +
          "Use forIndividualRequest() for per-change uploads."
      }
      return UploadStrategy(
        localChangesFetchMode = LocalChangesFetchMode.AllChanges,
        patchGeneratorMode = PatchGeneratorMode.PerResource,
        requestGeneratorMode =
          UploadRequestGeneratorMode.BundleRequest(
            httpVerbForCreate = methodForCreate.toHttpVerb(),
            httpVerbForUpdate = methodForUpdate.toHttpVerb(),
            bundleSize = bundleSize,
          ),
      )
    }

    /**
     * Creates an [UploadStrategy] that sends each change as a separate HTTP request.
     *
     * @param methodForCreate The HTTP method for resource creation (PUT or POST).
     * @param methodForUpdate The HTTP method for resource updates (PUT or PATCH).
     * @param squash If `true`, squashes multiple changes to the same resource into one.
     */
    fun forIndividualRequest(
      methodForCreate: HttpCreateMethod = HttpCreateMethod.PUT,
      methodForUpdate: HttpUpdateMethod = HttpUpdateMethod.PATCH,
      squash: Boolean = true,
    ): UploadStrategy {
      return UploadStrategy(
        localChangesFetchMode =
          if (squash) LocalChangesFetchMode.AllChanges
          else LocalChangesFetchMode.PerResource,
        patchGeneratorMode =
          if (squash) PatchGeneratorMode.PerResource
          else PatchGeneratorMode.PerChange,
        requestGeneratorMode =
          UploadRequestGeneratorMode.UrlRequest(
            httpVerbForCreate = methodForCreate.toHttpVerb(),
            httpVerbForUpdate = methodForUpdate.toHttpVerb(),
          ),
      )
    }
  }
}

/** HTTP method to use for creating resources. */
enum class HttpCreateMethod {
  PUT,
  POST,
  ;

  internal fun toHttpVerb(): HttpVerb =
    when (this) {
      PUT -> HttpVerb.PUT
      POST -> HttpVerb.POST
    }
}

/** HTTP method to use for updating resources. */
enum class HttpUpdateMethod {
  PUT,
  PATCH,
  ;

  internal fun toHttpVerb(): HttpVerb =
    when (this) {
      PUT -> HttpVerb.PUT
      PATCH -> HttpVerb.PATCH
    }
}
