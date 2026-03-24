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

import com.google.android.fhir.db.ResourceNotFoundException
import com.google.android.fhir.index.SearchParamDefinition
import com.google.android.fhir.index.SearchParamType
import com.google.android.fhir.search.Search
import com.google.android.fhir.search.StringClientParam
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.count
import com.google.android.fhir.search.search
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.terminologies.ResourceType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FhirEngineProviderTest {

  private val searchParams =
    listOf(
      SearchParamDefinition("name", SearchParamType.STRING, "Patient.name"),
      SearchParamDefinition("family", SearchParamType.STRING, "Patient.name.family"),
      SearchParamDefinition("given", SearchParamType.STRING, "Patient.name.given"),
      SearchParamDefinition("gender", SearchParamType.TOKEN, "Patient.gender"),
      SearchParamDefinition("active", SearchParamType.TOKEN, "Patient.active"),
    )

  @BeforeTest
  fun setUp() = runTest {
    registerResourceType(Patient::class, ResourceType.Patient)
    FhirEngineProvider.init(
      FhirEngineConfiguration(customSearchParameters = searchParams),
    )
    // Clear any leftover data from previous test runs (database file persists on disk)
    FhirEngineProvider.getInstance().clearDatabase()
  }

  @AfterTest
  fun tearDown() {
    FhirEngineProvider.clearInstance()
  }

  // --- CRUD tests ---

  @Test
  fun create_patientWithId_shouldReturnSameId() = runTest {
    val engine = FhirEngineProvider.getInstance()
    val patient = Patient(id = "test-patient-1")

    val ids = engine.create(patient)

    assertEquals(listOf("test-patient-1"), ids)
  }

  @Test
  fun create_patientWithoutId_shouldGenerateId() = runTest {
    val engine = FhirEngineProvider.getInstance()
    val patient = Patient()

    val ids = engine.create(patient)

    assertEquals(1, ids.size)
    assertTrue(ids.first().isNotEmpty())
  }

  @Test
  fun get_existingPatient_shouldReturnPatient() = runTest {
    val engine = FhirEngineProvider.getInstance()
    val patient =
      Patient(
        id = "test-patient-2",
        name =
          listOf(
            HumanName(
              family = com.google.fhir.model.r4.String(value = "Doe"),
              given = listOf(com.google.fhir.model.r4.String(value = "John")),
            ),
          ),
      )
    engine.create(patient)

    val result = engine.get(ResourceType.Patient, "test-patient-2")

    assertIs<Patient>(result)
    assertEquals("test-patient-2", result.id)
    assertEquals(1, result.name.size)
    assertEquals("Doe", result.name.first().family?.value)
    assertEquals("John", result.name.first().given.first().value)
  }

  @Test
  fun get_nonExistentPatient_shouldThrowResourceNotFoundException() = runTest {
    val engine = FhirEngineProvider.getInstance()

    assertFailsWith<ResourceNotFoundException> {
      engine.get(ResourceType.Patient, "non-existent-id")
    }
  }

  @Test
  fun create_multiplePatients_shouldReturnAllIds() = runTest {
    val engine = FhirEngineProvider.getInstance()
    val patient1 = Patient(id = "multi-1")
    val patient2 = Patient(id = "multi-2")
    val patient3 = Patient(id = "multi-3")

    val ids = engine.create(patient1, patient2, patient3)

    assertEquals(listOf("multi-1", "multi-2", "multi-3"), ids)

    // Verify all are retrievable
    assertIs<Patient>(engine.get(ResourceType.Patient, "multi-1"))
    assertIs<Patient>(engine.get(ResourceType.Patient, "multi-2"))
    assertIs<Patient>(engine.get(ResourceType.Patient, "multi-3"))
  }

  @Test
  fun delete_existingPatient_shouldRemoveFromDatabase() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "to-delete"))

    // Verify it exists
    assertIs<Patient>(engine.get(ResourceType.Patient, "to-delete"))

    // Delete it
    engine.delete(ResourceType.Patient, "to-delete")

    // Verify it's gone
    assertFailsWith<ResourceNotFoundException> {
      engine.get(ResourceType.Patient, "to-delete")
    }
  }

  // --- Update tests ---

  @Test
  fun update_existingPatient_shouldUpdateFields() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "update-test",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "OldName")),
          ),
      ),
    )

    val updated =
      Patient(
        id = "update-test",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "NewName")),
          ),
      )
    engine.update(updated)

    val result = engine.get(ResourceType.Patient, "update-test") as Patient
    assertEquals("NewName", result.name.first().family?.value)
  }

  @Test
  fun update_nonExistentPatient_shouldThrowResourceNotFoundException() = runTest {
    val engine = FhirEngineProvider.getInstance()

    assertFailsWith<ResourceNotFoundException> {
      engine.update(Patient(id = "does-not-exist"))
    }
  }

  // --- Search tests ---

  @Test
  fun search_byType_shouldReturnAllMatchingResources() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "s1"), Patient(id = "s2"), Patient(id = "s3"))

    val results = engine.search<Patient>(Search(ResourceType.Patient))

    assertEquals(3, results.size)
  }

  @Test
  fun search_byType_noResults_shouldReturnEmptyList() = runTest {
    val engine = FhirEngineProvider.getInstance()

    val results = engine.search<Patient>(Search(ResourceType.Patient))

    assertTrue(results.isEmpty())
  }

  @Test
  fun search_withCountLimit_shouldRespectLimit() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(id = "p1"),
      Patient(id = "p2"),
      Patient(id = "p3"),
      Patient(id = "p4"),
      Patient(id = "p5"),
    )

    val results =
      engine.search<Patient>(Search(ResourceType.Patient, count = 2))

    assertEquals(2, results.size)
  }

  @Test
  fun search_withCountAndOffset_shouldReturnPagedResults() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(id = "p1"),
      Patient(id = "p2"),
      Patient(id = "p3"),
      Patient(id = "p4"),
      Patient(id = "p5"),
    )

    val page1 =
      engine.search<Patient>(Search(ResourceType.Patient, count = 2, from = 0))
    val page2 =
      engine.search<Patient>(Search(ResourceType.Patient, count = 2, from = 2))
    val page3 =
      engine.search<Patient>(Search(ResourceType.Patient, count = 2, from = 4))

    assertEquals(2, page1.size)
    assertEquals(2, page2.size)
    assertEquals(1, page3.size)
  }

  @Test
  fun search_withStringFilter_shouldFilterByFamilyName() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "sf-1",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Smith")),
          ),
      ),
      Patient(
        id = "sf-2",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Johnson")),
          ),
      ),
      Patient(
        id = "sf-3",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Smithson")),
          ),
      ),
    )

    val results =
      engine.search<Patient> {
        filter(
          StringClientParam("family"),
          {
            value = "Smith"
            modifier = StringFilterModifier.MATCHES_EXACTLY
          },
        )
      }

    assertEquals(1, results.size)
    assertEquals("sf-1", (results.first().resource as Patient).id)
  }

  @Test
  fun search_withStringFilter_startsWith_shouldReturnMatches() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "sw-1",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Smith")),
          ),
      ),
      Patient(
        id = "sw-2",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Smithson")),
          ),
      ),
      Patient(
        id = "sw-3",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Johnson")),
          ),
      ),
    )

    val results =
      engine.search<Patient> {
        filter(
          StringClientParam("family"),
          {
            value = "Smith"
            modifier = StringFilterModifier.STARTS_WITH
          },
        )
      }

    assertEquals(2, results.size)
    val ids = results.map { (it.resource as Patient).id!! }.sorted()
    assertEquals(listOf("sw-1", "sw-2"), ids)
  }

  @Test
  fun search_withStringFilter_contains_shouldReturnMatches() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "ct-1",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Smith")),
          ),
      ),
      Patient(
        id = "ct-2",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Goldsmith")),
          ),
      ),
      Patient(
        id = "ct-3",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Johnson")),
          ),
      ),
    )

    val results =
      engine.search<Patient> {
        filter(
          StringClientParam("family"),
          {
            value = "smith"
            modifier = StringFilterModifier.CONTAINS
          },
        )
      }

    assertEquals(2, results.size)
    val ids = results.map { (it.resource as Patient).id!! }.sorted()
    assertEquals(listOf("ct-1", "ct-2"), ids)
  }

  // TODO: Token filter tests for gender (Enumeration<AdministrativeGender>) require adding
  // Enumeration handling in ResourceIndexer.tokenIndex(). Token filter tests for boolean active
  // require FHIRPath engine support for Patient.active. These can be added when the kotlin-fhir
  // FHIRPath engine and ResourceIndexer are extended to cover these types.

  // --- Count tests ---

  @Test
  fun count_withResources_shouldReturnCorrectCount() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "c1"), Patient(id = "c2"), Patient(id = "c3"))

    val count = engine.count<Patient> {}

    assertEquals(3, count)
  }

  @Test
  fun count_noResources_shouldReturnZero() = runTest {
    val engine = FhirEngineProvider.getInstance()

    val count = engine.count<Patient> {}

    assertEquals(0, count)
  }

  @Test
  fun count_withFilter_shouldReturnFilteredCount() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "cf-1",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Smith")),
          ),
      ),
      Patient(
        id = "cf-2",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Johnson")),
          ),
      ),
      Patient(
        id = "cf-3",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "Smith")),
          ),
      ),
    )

    val count =
      engine.count<Patient> {
        filter(
          StringClientParam("family"),
          {
            value = "Smith"
            modifier = StringFilterModifier.MATCHES_EXACTLY
          },
        )
      }

    assertEquals(2, count)
  }

  // --- Clear database tests ---

  @Test
  fun clearDatabase_shouldRemoveAllResources() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "clear-1"), Patient(id = "clear-2"))

    assertEquals(2, engine.count<Patient> {})

    engine.clearDatabase()

    assertEquals(0, engine.count<Patient> {})
  }

  // --- Integration tests ---

  @Test
  fun createThenUpdateThenSearch_shouldReturnUpdatedResource() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "int-1",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "OriginalName")),
          ),
      ),
    )

    engine.update(
      Patient(
        id = "int-1",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "UpdatedName")),
          ),
      ),
    )

    val results = engine.search<Patient>(Search(ResourceType.Patient))
    assertEquals(1, results.size)
    val patient = results.first().resource as Patient
    assertEquals("UpdatedName", patient.name.first().family?.value)
  }

  @Test
  fun createThenDeleteThenCount_shouldReflectDeletion() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "del-1"), Patient(id = "del-2"), Patient(id = "del-3"))

    assertEquals(3, engine.count<Patient> {})

    engine.delete(ResourceType.Patient, "del-2")

    assertEquals(2, engine.count<Patient> {})
  }

  @Test
  fun search_dslExtension_shouldWorkEquivalently() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "dsl-1"), Patient(id = "dsl-2"))

    val results = engine.search<Patient> {}

    assertEquals(2, results.size)
  }

  @Test
  fun updateThenSearch_shouldReturnUpdatedIndices() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "idx-1",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "OldFamily")),
          ),
      ),
    )

    // Search for old name
    val oldResults =
      engine.search<Patient> {
        filter(
          StringClientParam("family"),
          {
            value = "OldFamily"
            modifier = StringFilterModifier.MATCHES_EXACTLY
          },
        )
      }
    assertEquals(1, oldResults.size)

    // Update name
    engine.update(
      Patient(
        id = "idx-1",
        name =
          listOf(
            HumanName(family = com.google.fhir.model.r4.String(value = "NewFamily")),
          ),
      ),
    )

    // Old name should no longer match
    val afterUpdate =
      engine.search<Patient> {
        filter(
          StringClientParam("family"),
          {
            value = "OldFamily"
            modifier = StringFilterModifier.MATCHES_EXACTLY
          },
        )
      }
    assertTrue(afterUpdate.isEmpty())

    // New name should match
    val newResults =
      engine.search<Patient> {
        filter(
          StringClientParam("family"),
          {
            value = "NewFamily"
            modifier = StringFilterModifier.MATCHES_EXACTLY
          },
        )
      }
    assertEquals(1, newResults.size)
  }
}
