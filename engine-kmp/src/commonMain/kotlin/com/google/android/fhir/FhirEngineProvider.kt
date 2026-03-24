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

package com.google.android.fhir

import com.google.android.fhir.db.impl.DatabaseImpl
import com.google.android.fhir.impl.FhirEngineImpl
import com.google.android.fhir.index.ResourceIndexer
import com.google.android.fhir.index.SearchParamDefinition
import com.google.android.fhir.index.SearchParamDefinitionsProviderImpl

/**
 * Provides singleton access to the [FhirEngine] instance.
 *
 * Initialize with [init] before calling [getInstance]. On Android, pass the application `Context`
 * as `platformContext`. On Desktop and iOS, the parameter is ignored.
 *
 * ```
 * // Initialize (once, e.g. in Application.onCreate on Android)
 * FhirEngineProvider.init(FhirEngineConfiguration())
 *
 * // Get the engine
 * val fhirEngine = FhirEngineProvider.getInstance(context)
 * ```
 */
object FhirEngineProvider {
  private var configuration: FhirEngineConfiguration? = null
  private var fhirEngine: FhirEngine? = null

  /**
   * Initializes the [FhirEngineProvider] with the given [configuration].
   *
   * This must be called before [getInstance]. Calling it again after initialization will throw an
   * [IllegalStateException].
   */
  fun init(configuration: FhirEngineConfiguration) {
    check(this.configuration == null) {
      "FhirEngineProvider has already been initialized."
    }
    this.configuration = configuration
  }

  /**
   * Returns the [FhirEngine] instance, creating it if necessary.
   *
   * @param platformContext Platform-specific context. On Android, this should be the application
   *   `Context`. On Desktop and iOS, pass `Unit` or omit.
   */
  fun getInstance(platformContext: Any = Unit): FhirEngine {
    val config =
      checkNotNull(configuration) {
        "FhirEngineProvider not initialized. Call FhirEngineProvider.init() first."
      }
    if (fhirEngine == null) {
      fhirEngine = buildFhirEngine(platformContext, config)
    }
    return fhirEngine!!
  }

  /** Clears the singleton instance. Intended for testing only. */
  internal fun clearInstance() {
    fhirEngine = null
    configuration = null
  }

  private fun buildFhirEngine(
    platformContext: Any,
    config: FhirEngineConfiguration,
  ): FhirEngine {
    val searchParamDefinitionsProvider =
      SearchParamDefinitionsProviderImpl(customParams = buildCustomParamsMap(config))
    val resourceIndexer = ResourceIndexer(searchParamDefinitionsProvider)
    val database = DatabaseImpl(platformContext, resourceIndexer)
    return FhirEngineImpl(database)
  }

  private fun buildCustomParamsMap(
    config: FhirEngineConfiguration,
  ): Map<String, List<SearchParamDefinition>> {
    val params = config.customSearchParameters ?: return emptyMap()
    return params.groupBy { it.path.substringBefore(".") }
  }
}
