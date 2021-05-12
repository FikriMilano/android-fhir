/*
 * Copyright 2020 Google LLC
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

package com.google.android.fhir.datacapture

import android.os.Build
import com.google.android.fhir.datacapture.views.QuestionnaireItemViewHolder
import com.google.android.fhir.datacapture.views.QuestionnaireItemViewHolderFactory
import com.google.android.fhir.datacapture.views.QuestionnaireItemViewItem
import com.google.common.truth.Truth.assertThat
import org.hl7.fhir.r4.model.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class QuestionnaireItemAdapterTest {
  @Test
  fun getItemViewType_groupItemType_shouldReturnGroupViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.GROUP),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.GROUP.value)
  }

  @Test
  fun getItemViewType_booleanItemType_shouldReturnBooleanViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.BOOLEAN),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.CHECK_BOX.value)
  }

  @Test
  fun getItemViewType_dateItemType_shouldReturnDatePickerViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.DATE),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.DATE_PICKER.value)
  }

  @Test
  fun getItemViewType_dateTimeItemType_shouldReturnDateTimePickerViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.DATETIME),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.DATE_TIME_PICKER.value)
  }

  @Test
  fun getItemViewType_stringItemType_shouldReturnEditTextViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.STRING),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.EDIT_TEXT_SINGLE_LINE.value)
  }

  @Test
  fun getItemViewType_textItemType_shouldReturnEditTextViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.TEXT),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.EDIT_TEXT_MULTI_LINE.value)
  }

  @Test
  fun getItemViewType_integerItemType_shouldReturnEditTextIntegerViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.INTEGER),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.EDIT_TEXT_INTEGER.value)
  }

  @Test
  fun getItemViewType_decimalItemType_shouldReturnEditTextDecimalViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.DECIMAL),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.EDIT_TEXT_DECIMAL.value)
  }

  @Test
  fun getItemViewType_choiceItemType_lessAnswerOptions_shouldReturnRadioGroupViewHolderType() {
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.CHOICE),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.RADIO_GROUP.value)
  }

  @Test
  fun getItemViewType_choiceItemType_moreAnswerOptions_shouldReturnDropDownViewHolderType() {
    val answerOptions =
      List(QuestionnaireItemAdapter.MINIMUM_NUMBER_OF_ANSWER_OPTIONS_FOR_DROP_DOWN) {
        Questionnaire.QuestionnaireItemAnswerOptionComponent()
          .setValue(Coding().setCode("test-code").setDisplay("Test Code"))
      }
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          Questionnaire.QuestionnaireItemComponent()
            .setType(Questionnaire.QuestionnaireItemType.CHOICE)
            .setAnswerOption(answerOptions),
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.DROP_DOWN.value)
  }

  @Test
  fun getItemViewType_choiceItemType_itemControlExtensionWithRadioButton_shouldReturnRadioGroupViewHolder() { // ktlint-disable max-line-length
    val answerOptions =
      List(QuestionnaireItemAdapter.MINIMUM_NUMBER_OF_ANSWER_OPTIONS_FOR_DROP_DOWN) {
        Questionnaire.QuestionnaireItemAnswerOptionComponent()
          .setValue(Coding().setCode("test-code").setDisplay("Test Code"))
      }
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    val questionnaireItem =
      Questionnaire.QuestionnaireItemComponent()
        .setType(Questionnaire.QuestionnaireItemType.CHOICE)
        .setAnswerOption(answerOptions)
    questionnaireItem.addExtension(
      Extension()
        .setUrl(EXTENSION_ITEM_CONTROL_URL)
        .setValue(
          CodeableConcept()
            .addCoding(
              Coding()
                .setCode(ITEM_CONTROL_RADIO_BUTTON)
                .setDisplay("Radio Button")
                .setSystem(EXTENSION_ITEM_CONTROL_SYSTEM)
            )
        )
    )
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          questionnaireItem,
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.RADIO_GROUP.value)
  }

  @Test
  fun getItemViewType_choiceItemType_itemControlExtensionWithDropDown_shouldReturnDropDownViewHolderType() { // ktlint-disable max-line-length
    val questionnaireItemAdapter = QuestionnaireItemAdapter(null)
    val questionnaireItem =
      Questionnaire.QuestionnaireItemComponent().setType(Questionnaire.QuestionnaireItemType.CHOICE)
    questionnaireItem.addExtension(
      Extension()
        .setUrl(EXTENSION_ITEM_CONTROL_URL)
        .setValue(
          CodeableConcept()
            .addCoding(
              Coding()
                .setCode(ITEM_CONTROL_DROP_DOWN)
                .setDisplay("Drop Down")
                .setSystem(EXTENSION_ITEM_CONTROL_SYSTEM)
            )
        )
    )
    questionnaireItemAdapter.submitList(
      listOf(
        QuestionnaireItemViewItem(
          questionnaireItem,
          QuestionnaireResponse.QuestionnaireResponseItemComponent()
        ) {}
      )
    )

    assertThat(questionnaireItemAdapter.getItemViewType(0))
      .isEqualTo(QuestionnaireItemViewHolderType.DROP_DOWN.value)
  }

  // TODO: test errors thrown for unsupported types

  @Test
  fun diffCallback_areItemsTheSame_sameLinkId_shouldReturnTrue() {
    assertThat(
        DiffCallback.areItemsTheSame(
          QuestionnaireItemViewItem(
            Questionnaire.QuestionnaireItemComponent().setLinkId("link-id-1").setText("text"),
            QuestionnaireResponse.QuestionnaireResponseItemComponent()
          ) {},
          QuestionnaireItemViewItem(
            Questionnaire.QuestionnaireItemComponent().setLinkId("link-id-1"),
            QuestionnaireResponse.QuestionnaireResponseItemComponent()
          ) {}
        )
      )
      .isTrue()
  }

  @Test
  fun diffCallback_areItemsTheSame_differentLinkId_shouldReturnFalse() {
    assertThat(
        DiffCallback.areItemsTheSame(
          QuestionnaireItemViewItem(
            Questionnaire.QuestionnaireItemComponent().setLinkId("link-id-1"),
            QuestionnaireResponse.QuestionnaireResponseItemComponent()
          ) {},
          QuestionnaireItemViewItem(
            Questionnaire.QuestionnaireItemComponent().setLinkId("link-id-2"),
            QuestionnaireResponse.QuestionnaireResponseItemComponent()
          ) {}
        )
      )
      .isFalse()
  }

  @Test
  fun diffCallback_areContentsTheSame_sameContents_shouldReturnTrue() {
    assertThat(
        DiffCallback.areContentsTheSame(
          QuestionnaireItemViewItem(
            Questionnaire.QuestionnaireItemComponent().setLinkId("link-id-1").setText("text"),
            QuestionnaireResponse.QuestionnaireResponseItemComponent()
          ) {},
          QuestionnaireItemViewItem(
            Questionnaire.QuestionnaireItemComponent().setLinkId("link-id-1").setText("text"),
            QuestionnaireResponse.QuestionnaireResponseItemComponent()
          ) {}
        )
      )
      .isTrue()
  }

  @Test
  fun diffCallback_areContentsTheSame_differentContents_shouldReturnFalse() {
    assertThat(
        DiffCallback.areContentsTheSame(
          QuestionnaireItemViewItem(
            Questionnaire.QuestionnaireItemComponent().setLinkId("link-id-1").setText("text"),
            QuestionnaireResponse.QuestionnaireResponseItemComponent()
          ) {},
          QuestionnaireItemViewItem(
            Questionnaire.QuestionnaireItemComponent()
              .setLinkId("link-id-1")
              .setText("different text"),
            QuestionnaireResponse.QuestionnaireResponseItemComponent()
          ) {}
        )
      )
      .isFalse()
  }

  @Test
  fun onCreateViewHolder_customViewType_shouldReturnCustomViewHolder() {
    val viewPicker : ViewPicker = mock()
    val questionnaireItemViewHolderFactory: QuestionnaireItemViewHolderFactory = mock()
    val expectedQuestionnaireItemViewHolder: QuestionnaireItemViewHolder = mock()
    whenever(viewPicker.pick(anyInt())).thenReturn(questionnaireItemViewHolderFactory)
    whenever(questionnaireItemViewHolderFactory.create(any())).thenReturn(expectedQuestionnaireItemViewHolder)

    val questionnaireItemAdapter = QuestionnaireItemAdapter(viewPicker)
    val actualQuestionnaireItemViewHolder = questionnaireItemAdapter.onCreateViewHolder(mock(), 40)
    assertEquals(expectedQuestionnaireItemViewHolder, actualQuestionnaireItemViewHolder)
  }


//  @Test
//  fun getItemViewType_customViewType_shouldReturnCustomType() {
//    val viewPicker : ViewPicker = mock()
//    val expectedQuestionnaireItemViewHolderFactory: QuestionnaireItemViewHolderFactory = mock()
//    val questionnaireItemViewHolder: QuestionnaireItemViewHolder = mock()
//    whenever(expectedQuestionnaireItemViewHolderFactory.create(any())).thenReturn(questionnaireItemViewHolder)
//    whenever(viewPicker.getType(any())).thenReturn(1)
//    whenever(viewPicker.pick(anyInt())).thenReturn(expectedQuestionnaireItemViewHolderFactory)
//
//    val questionnaireItemAdapter = QuestionnaireItemAdapter(viewPicker)
//    val actualQuestionnaireItemViewHolderFactory = questionnaireItemAdapter.onCreateViewHolder(mock(), 40)
//    assertEquals(expectedQuestionnaireItemViewHolderFactory, actualQuestionnaireItemViewHolderFactory)
//  }
}
