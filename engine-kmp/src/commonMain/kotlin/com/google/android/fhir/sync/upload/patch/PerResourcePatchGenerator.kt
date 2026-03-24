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

package com.google.android.fhir.sync.upload.patch

import com.google.android.fhir.LocalChange
import com.google.android.fhir.LocalChange.Type
import com.google.android.fhir.db.LocalChangeResourceReference
import com.google.android.fhir.sync.upload.patch.PatchOrdering.sccOrderByReferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generates a [Patch] for all [LocalChange]es made to a single FHIR resource.
 *
 * Used when individual client-side changes do not need to be uploaded to the server in order to
 * maintain an audit trail, but instead, multiple changes made to the same FHIR resource on the
 * client can be recorded as a single change on the server.
 */
internal object PerResourcePatchGenerator : PatchGenerator {

  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun generate(
    localChanges: List<LocalChange>,
    localChangesReferences: List<LocalChangeResourceReference>,
  ): List<StronglyConnectedPatchMappings> {
    return generateSquashedChangesMapping(localChanges).sccOrderByReferences(localChangesReferences)
  }

  internal fun generateSquashedChangesMapping(localChanges: List<LocalChange>) =
    localChanges
      .groupBy { it.resourceType to it.resourceId }
      .values
      .mapNotNull { resourceLocalChanges ->
        mergeLocalChangesForSingleResource(resourceLocalChanges)?.let { patch ->
          PatchMapping(
            localChanges = resourceLocalChanges,
            generatedPatch = patch,
          )
        }
      }

  private fun mergeLocalChangesForSingleResource(localChanges: List<LocalChange>): Patch? {
    val firstDeleteLocalChange = localChanges.indexOfFirst { it.type == Type.DELETE }
    require(firstDeleteLocalChange == -1 || firstDeleteLocalChange == localChanges.size - 1) {
      "Changes after deletion of resource are not permitted"
    }

    val lastInsertLocalChange = localChanges.indexOfLast { it.type == Type.INSERT }
    require(lastInsertLocalChange == -1 || lastInsertLocalChange == 0) {
      "Changes before creation of resource are not permitted"
    }

    return when {
      localChanges.first().type == Type.INSERT && localChanges.last().type == Type.DELETE -> null
      localChanges.first().type == Type.INSERT -> {
        createPatch(
          localChanges = localChanges,
          type = Patch.Type.INSERT,
          payload = localChanges.map { it.payload }.reduce(::applyPatch),
        )
      }
      localChanges.last().type == Type.DELETE -> {
        createPatch(
          localChanges = localChanges,
          type = Patch.Type.DELETE,
          payload = "",
        )
      }
      else -> {
        createPatch(
          localChanges = localChanges,
          type = Patch.Type.UPDATE,
          payload = localChanges.map { it.payload }.reduce(::mergePatches),
        )
      }
    }
  }

  private fun createPatch(localChanges: List<LocalChange>, type: Patch.Type, payload: String) =
    Patch(
      resourceId = localChanges.first().resourceId,
      resourceType = localChanges.first().resourceType,
      type = type,
      payload = payload,
      versionId = localChanges.first().versionId,
      timestamp = localChanges.last().timestamp,
    )

  /** Update a JSON object with a JSON patch (RFC 6902). */
  private fun applyPatch(resourceString: String, patchString: String): String {
    var resource: JsonElement = json.parseToJsonElement(resourceString)
    val patchArray = json.parseToJsonElement(patchString).jsonArray

    for (operation in patchArray) {
      val op = operation.jsonObject["op"]?.jsonPrimitive?.content ?: continue
      val path = operation.jsonObject["path"]?.jsonPrimitive?.content ?: continue
      val value = operation.jsonObject["value"]

      resource =
        when (op) {
          "add" -> {
            if (value != null) addValueAtPath(resource, parsePath(path), value) else resource
          }
          "replace" -> {
            if (value != null) replaceValueAtPath(resource, parsePath(path), value) else resource
          }
          "remove" -> removeValueAtPath(resource, parsePath(path))
          "move" -> {
            val from = operation.jsonObject["from"]?.jsonPrimitive?.content ?: continue
            val movedValue = getValueAtPath(resource, parsePath(from))
            if (movedValue != null) {
              addValueAtPath(removeValueAtPath(resource, parsePath(from)), parsePath(path), movedValue)
            } else {
              resource
            }
          }
          "copy" -> {
            val from = operation.jsonObject["from"]?.jsonPrimitive?.content ?: continue
            val copiedValue = getValueAtPath(resource, parsePath(from))
            if (copiedValue != null) addValueAtPath(resource, parsePath(path), copiedValue)
            else resource
          }
          "test" -> resource // test is a no-op for application
          else -> resource
        }
    }

    return Json.encodeToString(resource)
  }

  /** Parses a JSON Pointer (RFC 6901) path into segments. */
  private fun parsePath(path: String): List<String> {
    if (path.isEmpty() || path == "/") return emptyList()
    return path.trimStart('/').split("/").map {
      it.replace("~1", "/").replace("~0", "~") // JSON Pointer escaping
    }
  }

  /** Gets a value at the given JSON Pointer path segments. */
  private fun getValueAtPath(element: JsonElement, parts: List<String>): JsonElement? {
    var current = element
    for (part in parts) {
      current =
        when {
          current is JsonObject -> current[part] ?: return null
          current is JsonArray -> {
            val index = part.toIntOrNull() ?: return null
            if (index < 0 || index >= current.size) return null
            current[index]
          }
          else -> return null
        }
    }
    return current
  }

  /** Adds a value at the given path (RFC 6902 "add" semantics). */
  private fun addValueAtPath(
    element: JsonElement,
    parts: List<String>,
    value: JsonElement,
  ): JsonElement {
    if (parts.isEmpty()) return value
    return addValueRecursive(element, parts, 0, value)
  }

  private fun addValueRecursive(
    element: JsonElement,
    parts: List<String>,
    index: Int,
    value: JsonElement,
  ): JsonElement {
    val key = parts[index]
    if (index == parts.size - 1) {
      return when {
        element is JsonObject -> JsonObject(element.toMutableMap().apply { this[key] = value })
        element is JsonArray && key == "-" -> JsonArray(element + value)
        element is JsonArray -> {
          val i = key.toInt()
          val list = element.toMutableList()
          list.add(i, value)
          JsonArray(list)
        }
        else -> element
      }
    }
    return when {
      element is JsonObject -> {
        val child = element[key] ?: return element
        JsonObject(
          element.toMutableMap().apply {
            this[key] = addValueRecursive(child, parts, index + 1, value)
          },
        )
      }
      element is JsonArray -> {
        val i = key.toInt()
        if (i < 0 || i >= element.size) return element
        val list = element.toMutableList()
        list[i] = addValueRecursive(list[i], parts, index + 1, value)
        JsonArray(list)
      }
      else -> element
    }
  }

  /** Replaces a value at the given path (RFC 6902 "replace" semantics). */
  private fun replaceValueAtPath(
    element: JsonElement,
    parts: List<String>,
    value: JsonElement,
  ): JsonElement {
    if (parts.isEmpty()) return value
    return replaceValueRecursive(element, parts, 0, value)
  }

  private fun replaceValueRecursive(
    element: JsonElement,
    parts: List<String>,
    index: Int,
    value: JsonElement,
  ): JsonElement {
    val key = parts[index]
    if (index == parts.size - 1) {
      return when {
        element is JsonObject -> JsonObject(element.toMutableMap().apply { this[key] = value })
        element is JsonArray -> {
          val i = key.toInt()
          val list = element.toMutableList()
          list[i] = value
          JsonArray(list)
        }
        else -> element
      }
    }
    return when {
      element is JsonObject -> {
        val child = element[key] ?: return element
        JsonObject(
          element.toMutableMap().apply {
            this[key] = replaceValueRecursive(child, parts, index + 1, value)
          },
        )
      }
      element is JsonArray -> {
        val i = key.toInt()
        if (i < 0 || i >= element.size) return element
        val list = element.toMutableList()
        list[i] = replaceValueRecursive(list[i], parts, index + 1, value)
        JsonArray(list)
      }
      else -> element
    }
  }

  /** Removes a value at the given path (RFC 6902 "remove" semantics). */
  private fun removeValueAtPath(element: JsonElement, parts: List<String>): JsonElement {
    if (parts.isEmpty()) return JsonObject(emptyMap())
    return removeValueRecursive(element, parts, 0)
  }

  private fun removeValueRecursive(
    element: JsonElement,
    parts: List<String>,
    index: Int,
  ): JsonElement {
    val key = parts[index]
    if (index == parts.size - 1) {
      return when {
        element is JsonObject -> JsonObject(element.toMutableMap().apply { remove(key) })
        element is JsonArray -> {
          val i = key.toInt()
          val list = element.toMutableList()
          list.removeAt(i)
          JsonArray(list)
        }
        else -> element
      }
    }
    return when {
      element is JsonObject -> {
        val child = element[key] ?: return element
        JsonObject(
          element.toMutableMap().apply {
            this[key] = removeValueRecursive(child, parts, index + 1)
          },
        )
      }
      element is JsonArray -> {
        val i = key.toInt()
        if (i < 0 || i >= element.size) return element
        val list = element.toMutableList()
        list[i] = removeValueRecursive(list[i], parts, index + 1)
        JsonArray(list)
      }
      else -> element
    }
  }

  /**
   * Merges two JSON patches represented as strings.
   *
   * - "replace" and "remove" operations from the second patch will overwrite any existing
   *   operations for the same path.
   * - "add" operations from the second patch will be added to the list of operations for that path,
   *   even if operations already exist for that path.
   */
  private fun mergePatches(firstPatch: String, secondPatch: String): String {
    val firstPatchNode = json.parseToJsonElement(firstPatch).jsonArray
    val secondPatchNode = json.parseToJsonElement(secondPatch).jsonArray
    val mergedOperations = linkedMapOf<String, MutableList<JsonElement>>()

    firstPatchNode.forEach { patchNode ->
      val path = patchNode.jsonObject["path"]?.jsonPrimitive?.content ?: return@forEach
      mergedOperations.getOrPut(path) { mutableListOf() }.add(patchNode)
    }

    secondPatchNode.forEach { patchNode ->
      val path = patchNode.jsonObject["path"]?.jsonPrimitive?.content ?: return@forEach
      val opType = patchNode.jsonObject["op"]?.jsonPrimitive?.content
      when (opType) {
        "replace",
        "remove", -> mergedOperations[path] = mutableListOf(patchNode)
        "add" -> mergedOperations.getOrPut(path) { mutableListOf() }.add(patchNode)
      }
    }

    return Json.encodeToString(JsonArray(mergedOperations.values.flatten()))
  }
}
