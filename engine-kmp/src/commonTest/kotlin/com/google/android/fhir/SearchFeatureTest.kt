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

import com.google.android.fhir.index.SearchParamDefinition
import com.google.android.fhir.index.SearchParamType
import com.google.android.fhir.search.DateClientParam
import com.google.android.fhir.search.Operation
import com.google.android.fhir.search.Order
import com.google.android.fhir.search.ParamPrefixEnum
import com.google.android.fhir.search.ReferenceClientParam
import com.google.android.fhir.search.StringClientParam
import com.google.android.fhir.search.StringFilterModifier
import com.google.android.fhir.search.TokenClientParam
import com.google.android.fhir.search.count
import com.google.android.fhir.search.has
import com.google.android.fhir.search.include
import com.google.android.fhir.search.revInclude
import com.google.android.fhir.search.search
import com.google.fhir.model.r4.Code
import com.google.fhir.model.r4.CodeableConcept
import com.google.fhir.model.r4.Coding
import com.google.fhir.model.r4.Date
import com.google.fhir.model.r4.FhirDate
import com.google.fhir.model.r4.HumanName
import com.google.fhir.model.r4.Observation
import com.google.fhir.model.r4.Patient
import com.google.fhir.model.r4.Reference
import com.google.fhir.model.r4.Uri
import com.google.fhir.model.r4.terminologies.ResourceType
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class SearchFeatureTest {

  private val searchParams =
    listOf(
      // Patient params
      SearchParamDefinition("family", SearchParamType.STRING, "Patient.name.family"),
      SearchParamDefinition("given", SearchParamType.STRING, "Patient.name.given"),
      SearchParamDefinition("name", SearchParamType.STRING, "Patient.name"),
      SearchParamDefinition("birthdate", SearchParamType.DATE, "Patient.birthDate"),
      // Observation params
      SearchParamDefinition("code", SearchParamType.TOKEN, "Observation.code"),
      SearchParamDefinition("subject", SearchParamType.REFERENCE, "Observation.subject"),
    )

  @BeforeTest
  fun setUp() = runTest {
    registerResourceType(Patient::class, ResourceType.Patient)
    registerResourceType(Observation::class, ResourceType.Observation)
    FhirEngineProvider.init(
      FhirEngineConfiguration(customSearchParameters = searchParams),
    )
    FhirEngineProvider.getInstance().clearDatabase()
  }

  @AfterTest
  fun tearDown() {
    FhirEngineProvider.clearInstance()
  }

  // --- Token filter tests ---

  @Test
  fun tokenFilter_byCoding_shouldMatchObservation() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Observation(
        id = "obs-1",
        code =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "http://loinc.org"),
                  code = Code(value = "8867-4"),
                ),
              ),
          ),
      ),
      Observation(
        id = "obs-2",
        code =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "http://loinc.org"),
                  code = Code(value = "8480-6"),
                ),
              ),
          ),
      ),
    )

    val results =
      engine.search<Observation> {
        filter(
          TokenClientParam("code"),
          { value = TokenClientParam.TokenFilterValue.coding("http://loinc.org", "8867-4") },
        )
      }

    assertEquals(1, results.size)
    assertEquals("obs-1", results.first().resource.id)
  }

  @Test
  fun tokenFilter_byCodeOnly_shouldMatchObservation() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Observation(
        id = "obs-code-1",
        code =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "http://loinc.org"),
                  code = Code(value = "12345-6"),
                ),
              ),
          ),
      ),
    )

    val results =
      engine.search<Observation> {
        filter(
          TokenClientParam("code"),
          { value = TokenClientParam.TokenFilterValue.string("12345-6") },
        )
      }

    assertEquals(1, results.size)
    assertEquals("obs-code-1", results.first().resource.id)
  }

  @Test
  fun tokenFilter_noMatch_shouldReturnEmpty() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Observation(
        id = "obs-no-match",
        code =
          CodeableConcept(
            coding =
              listOf(
                Coding(
                  system = Uri(value = "http://loinc.org"),
                  code = Code(value = "8867-4"),
                ),
              ),
          ),
      ),
    )

    val results =
      engine.search<Observation> {
        filter(
          TokenClientParam("code"),
          { value = TokenClientParam.TokenFilterValue.string("99999-9") },
        )
      }

    assertTrue(results.isEmpty())
  }

  // --- Reference filter tests ---

  @Test
  fun referenceFilter_bySubject_shouldMatchObservation() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "ref-patient-1"))
    engine.create(
      Observation(
        id = "obs-ref-1",
        subject = Reference(reference = com.google.fhir.model.r4.String(value = "Patient/ref-patient-1")),
        code = CodeableConcept(
          coding = listOf(Coding(system = Uri(value = "http://loinc.org"), code = Code(value = "1234-5"))),
        ),
      ),
      Observation(
        id = "obs-ref-2",
        subject = Reference(reference = com.google.fhir.model.r4.String(value = "Patient/ref-patient-2")),
        code = CodeableConcept(
          coding = listOf(Coding(system = Uri(value = "http://loinc.org"), code = Code(value = "1234-5"))),
        ),
      ),
    )

    val results =
      engine.search<Observation> {
        filter(
          ReferenceClientParam("subject"),
          { value = "Patient/ref-patient-1" },
        )
      }

    assertEquals(1, results.size)
    assertEquals("obs-ref-1", results.first().resource.id)
  }

  @Test
  fun referenceFilter_noMatch_shouldReturnEmpty() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Observation(
        id = "obs-ref-no-match",
        subject = Reference(reference = com.google.fhir.model.r4.String(value = "Patient/some-patient")),
        code = CodeableConcept(
          coding = listOf(Coding(system = Uri(value = "http://loinc.org"), code = Code(value = "1234-5"))),
        ),
      ),
    )

    val results =
      engine.search<Observation> {
        filter(
          ReferenceClientParam("subject"),
          { value = "Patient/non-existent" },
        )
      }

    assertTrue(results.isEmpty())
  }

  // --- Date filter tests ---

  @Test
  fun dateFilter_equal_shouldMatchBirthDate() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "date-p1",
        birthDate = Date(value = FhirDate.Date(LocalDate(1990, 1, 15))),
      ),
      Patient(
        id = "date-p2",
        birthDate = Date(value = FhirDate.Date(LocalDate(1985, 6, 20))),
      ),
    )

    val results =
      engine.search<Patient> {
        filter(
          DateClientParam("birthdate"),
          {
            value = FhirDate.Date(LocalDate(1990, 1, 15))
            prefix = ParamPrefixEnum.EQUAL
          },
        )
      }

    assertEquals(1, results.size)
    assertEquals("date-p1", results.first().resource.id)
  }

  @Test
  fun dateFilter_greaterThan_shouldFilterCorrectly() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "date-gt-1",
        birthDate = Date(value = FhirDate.Date(LocalDate(1980, 1, 1))),
      ),
      Patient(
        id = "date-gt-2",
        birthDate = Date(value = FhirDate.Date(LocalDate(1990, 1, 1))),
      ),
      Patient(
        id = "date-gt-3",
        birthDate = Date(value = FhirDate.Date(LocalDate(2000, 1, 1))),
      ),
    )

    val results =
      engine.search<Patient> {
        filter(
          DateClientParam("birthdate"),
          {
            value = FhirDate.Date(LocalDate(1990, 1, 1))
            prefix = ParamPrefixEnum.GREATERTHAN
          },
        )
      }

    assertEquals(1, results.size)
    assertEquals("date-gt-3", results.first().resource.id)
  }

  @Test
  fun dateFilter_lessThan_shouldFilterCorrectly() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "date-lt-1",
        birthDate = Date(value = FhirDate.Date(LocalDate(1980, 1, 1))),
      ),
      Patient(
        id = "date-lt-2",
        birthDate = Date(value = FhirDate.Date(LocalDate(1990, 1, 1))),
      ),
      Patient(
        id = "date-lt-3",
        birthDate = Date(value = FhirDate.Date(LocalDate(2000, 1, 1))),
      ),
    )

    val results =
      engine.search<Patient> {
        filter(
          DateClientParam("birthdate"),
          {
            value = FhirDate.Date(LocalDate(1990, 1, 1))
            prefix = ParamPrefixEnum.LESSTHAN
          },
        )
      }

    assertEquals(1, results.size)
    assertEquals("date-lt-1", results.first().resource.id)
  }

  // --- Sort tests ---

  @Test
  fun sort_byStringAscending_shouldReturnOrdered() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "sort-c",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Charlie"))),
      ),
      Patient(
        id = "sort-a",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Alpha"))),
      ),
      Patient(
        id = "sort-b",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Bravo"))),
      ),
    )

    val results =
      engine.search<Patient> {
        sort(StringClientParam("family"), Order.ASCENDING)
      }

    assertEquals(3, results.size)
    assertEquals("sort-a", results[0].resource.id)
    assertEquals("sort-b", results[1].resource.id)
    assertEquals("sort-c", results[2].resource.id)
  }

  @Test
  fun sort_byStringDescending_shouldReturnOrdered() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "sort-d-a",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Alpha"))),
      ),
      Patient(
        id = "sort-d-c",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Charlie"))),
      ),
      Patient(
        id = "sort-d-b",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Bravo"))),
      ),
    )

    val results =
      engine.search<Patient> {
        sort(StringClientParam("family"), Order.DESCENDING)
      }

    assertEquals(3, results.size)
    assertEquals("sort-d-c", results[0].resource.id)
    assertEquals("sort-d-b", results[1].resource.id)
    assertEquals("sort-d-a", results[2].resource.id)
  }

  @Test
  fun sort_byDateAscending_shouldReturnOrdered() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "sort-date-3",
        birthDate = Date(value = FhirDate.Date(LocalDate(2000, 1, 1))),
      ),
      Patient(
        id = "sort-date-1",
        birthDate = Date(value = FhirDate.Date(LocalDate(1980, 1, 1))),
      ),
      Patient(
        id = "sort-date-2",
        birthDate = Date(value = FhirDate.Date(LocalDate(1990, 1, 1))),
      ),
    )

    val results =
      engine.search<Patient> {
        sort(DateClientParam("birthdate"), Order.ASCENDING)
      }

    assertEquals(3, results.size)
    assertEquals("sort-date-1", results[0].resource.id)
    assertEquals("sort-date-2", results[1].resource.id)
    assertEquals("sort-date-3", results[2].resource.id)
  }

  // --- Include tests ---

  @Test
  fun include_forwardInclude_shouldReturnReferencedResources() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "inc-patient"))
    engine.create(
      Observation(
        id = "inc-obs",
        subject = Reference(reference = com.google.fhir.model.r4.String(value = "Patient/inc-patient")),
        code = CodeableConcept(
          coding = listOf(Coding(system = Uri(value = "http://loinc.org"), code = Code(value = "8867-4"))),
        ),
      ),
    )

    val results =
      engine.search<Observation> {
        include(ResourceType.Patient, ReferenceClientParam("subject"))
      }

    assertEquals(1, results.size)
    assertNotNull(results.first().included)
    val includedPatients = results.first().included!!["subject"]
    assertNotNull(includedPatients)
    assertEquals(1, includedPatients.size)
    assertEquals("inc-patient", includedPatients.first().id)
  }

  @Test
  fun include_noIncludeSpecified_shouldReturnNullIncluded() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Observation(
        id = "no-inc-obs",
        code = CodeableConcept(
          coding = listOf(Coding(system = Uri(value = "http://loinc.org"), code = Code(value = "8867-4"))),
        ),
      ),
    )

    val results = engine.search<Observation> {}

    assertEquals(1, results.size)
    assertNull(results.first().included)
  }

  // --- RevInclude tests ---

  @Test
  fun revInclude_shouldReturnReferencingResources() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "rev-patient"))
    engine.create(
      Observation(
        id = "rev-obs-1",
        subject = Reference(reference = com.google.fhir.model.r4.String(value = "Patient/rev-patient")),
        code = CodeableConcept(
          coding = listOf(Coding(system = Uri(value = "http://loinc.org"), code = Code(value = "8867-4"))),
        ),
      ),
      Observation(
        id = "rev-obs-2",
        subject = Reference(reference = com.google.fhir.model.r4.String(value = "Patient/rev-patient")),
        code = CodeableConcept(
          coding = listOf(Coding(system = Uri(value = "http://loinc.org"), code = Code(value = "8480-6"))),
        ),
      ),
    )

    val results =
      engine.search<Patient> {
        revInclude(ResourceType.Observation, ReferenceClientParam("subject"))
      }

    assertEquals(1, results.size)
    assertNotNull(results.first().revIncluded)
    val revIncluded = results.first().revIncluded!!
    val key = Pair(ResourceType.Observation, "subject")
    assertNotNull(revIncluded[key])
    assertEquals(2, revIncluded[key]!!.size)
  }

  @Test
  fun revInclude_noReferencingResources_shouldReturnNullRevIncluded() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "rev-no-obs"))

    val results =
      engine.search<Patient> {
        revInclude(ResourceType.Observation, ReferenceClientParam("subject"))
      }

    assertEquals(1, results.size)
    // revIncluded should be null or have empty lists when no referencing resources exist
    val revIncluded = results.first().revIncluded
    if (revIncluded != null) {
      val key = Pair(ResourceType.Observation, "subject")
      assertTrue(revIncluded[key].isNullOrEmpty())
    }
  }

  // --- Nested search (has) tests ---

  @Test
  fun has_shouldFilterByNestedCriteria() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(id = "has-p1"),
      Patient(id = "has-p2"),
    )
    engine.create(
      Observation(
        id = "has-obs-1",
        subject = Reference(reference = com.google.fhir.model.r4.String(value = "Patient/has-p1")),
        code = CodeableConcept(
          coding = listOf(Coding(system = Uri(value = "http://loinc.org"), code = Code(value = "8867-4"))),
        ),
      ),
    )
    // has-p2 has no observations

    val results =
      engine.search<Patient> {
        has(ResourceType.Observation, ReferenceClientParam("subject")) {
          filter(
            TokenClientParam("code"),
            { value = TokenClientParam.TokenFilterValue.coding("http://loinc.org", "8867-4") },
          )
        }
      }

    assertEquals(1, results.size)
    assertEquals("has-p1", results.first().resource.id)
  }

  @Test
  fun has_noMatch_shouldReturnEmpty() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(Patient(id = "has-no-match"))
    // No observations at all

    val results =
      engine.search<Patient> {
        has(ResourceType.Observation, ReferenceClientParam("subject")) {
          filter(
            TokenClientParam("code"),
            { value = TokenClientParam.TokenFilterValue.string("8867-4") },
          )
        }
      }

    assertTrue(results.isEmpty())
  }

  // --- OR operation test ---

  @Test
  fun filter_withOrOperation_shouldReturnUnion() = runTest {
    val engine = FhirEngineProvider.getInstance()
    engine.create(
      Patient(
        id = "or-1",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Smith"))),
      ),
      Patient(
        id = "or-2",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Johnson"))),
      ),
      Patient(
        id = "or-3",
        name = listOf(HumanName(family = com.google.fhir.model.r4.String(value = "Williams"))),
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
          {
            value = "Johnson"
            modifier = StringFilterModifier.MATCHES_EXACTLY
          },
          operation = Operation.OR,
        )
      }

    assertEquals(2, results.size)
    val ids = results.map { it.resource.id!! }.sorted()
    assertEquals(listOf("or-1", "or-2"), ids)
  }
}
