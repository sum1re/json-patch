/*
 * Copyright 2023, sum1re(sum1re@mail.sum1re.xyz)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.sum1re.json

import kotlinx.serialization.json.*

/**
 * Simple implementation of [RFC 6902 JSON Patch](https://datatracker.ietf.org/doc/html/rfc6902)
 * using [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization).
 *
 * Only applies patch to a [JsonElement], does not support diffing or generating patch.
 *
 * @sample xyz.sum1re.json.JsonPatchTest.objectSample
 * @sample xyz.sum1re.json.JsonPatchTest.arraySample
 */
@SinceKotlin("1.0.0")
class JsonPatch(private val patchNodeList: List<JsonPatchOperation>) {

    /**
     * Apply patch to specified [JsonElement].
     *
     * @param source the element to modify.
     * @return the modified element.
     */
    @SinceKotlin("1.0.0")
    fun applyPatch(source: JsonElement): JsonElement {
        var modified = source
        patchNodeList.forEach {
            modified = perform(it, modified)
        }
        return modified
    }

    /**
     * Performs the specified operation on the given [JsonElement].
     *
     * @param operation the operation details.
     * @param doc the [JsonElement] to modify.
     * @return the modified element.
     * @throws IllegalArgumentException if the op is not supported.
     */
    @SinceKotlin("1.0.0")
    private fun perform(operation: JsonPatchOperation, doc: JsonElement): JsonElement {
        val op = operation.op.lowercase()
        if (op in listOf("copy", "move"))
            require(operation.from != null) { "The \"from\" MUST exist for \"copy\" and \"move\" operation." }
        if (operation.path.isNotEmpty())
            require(operation.path.startsWith("/")) { "Invalid path property, it should start with a slash" }
        val key = operation.path.substringAfterLast("/")
        val elementPath = operation.path.substringBeforeLast("/")
        return when (op) {
            "add" -> add(doc, elementPath, key, operation.value)
            "remove" -> remove(doc, elementPath, key)
            "replace" -> replace(doc, elementPath, key, operation.value)
            "copy" -> copy(doc, operation.from!!, elementPath, key)
            "move" -> move(doc, operation.from!!, elementPath, key)
            else -> throw IllegalArgumentException("Supported op: add, remove, replace, move, copy")
        }
    }

    /**
     * Add the value at the target path.
     * - If the target path specifies an array index, a new value is inserted into the array at the specified index.
     * - If the target path specifies an object member that does not already exist, a new member is added to the object.
     * - If the target path specifies an object member that does exist, that member's value is replaced.
     *
     * @param doc the element to modify.
     * @param path the path to the element to add.
     * @param key the key of the [JsonElement] to add for [JsonObject] type, or the index for [JsonArray] type.
     * @param value the [JsonElement] to add.
     * @return the modified element.
     * @throws IllegalArgumentException if the target path is invalid or not
     */
    @SinceKotlin("1.0.0")
    private fun add(doc: JsonElement, path: String, key: String, value: JsonElement): JsonElement {
        if (path.isEmpty()) {
            return when (doc) {
                is JsonArray -> doc.add(key, value)
                is JsonObject -> doc.add(key, value)
                else -> doc
            }
        }
        val next = ElementNext(doc, path)
        require(next.element !is JsonNull) { "Invalid \"path\" prop: $path" }
        return when (doc) {
            is JsonArray -> JsonArray(
                doc.toMutableList()
                    .apply { this[next.currentPath.toInt()] = add(next.element, next.nextPath, key, value) })

            is JsonObject -> JsonObject(
                doc.toMutableMap().apply { this[next.currentPath] = add(next.element, next.nextPath, key, value) })

            else -> doc
        }
    }

    /**
     * Remove the value at the target path.
     *
     * @param doc the element to modify.
     * @param path the path to the element to add.
     * @param key the key of the [JsonElement] to add for [JsonObject] type, or the index for [JsonArray] type.
     * @return the modified element
     * @throws IllegalArgumentException if the path is invalid or the element don't have the key
     */
    @SinceKotlin("1.0.0")
    private fun remove(doc: JsonElement, path: String, key: String): JsonElement {
        if (key.isEmpty()) {
            return when (doc) {
                is JsonObject -> JsonObject(emptyMap())
                is JsonArray -> JsonArray(emptyList())
                else -> doc
            }
        }
        if (path.isEmpty()) {
            return when (doc) {
                is JsonObject -> doc.remove(key)
                is JsonArray -> doc.remove(key)
                else -> doc
            }
        }
        val next = ElementNext(doc, path)
        require(next.element !is JsonNull) { "Missing \"path\" property: $path" }
        return when (doc) {
            is JsonArray -> JsonArray(
                doc.toMutableList().apply { this[next.currentPath.toInt()] = remove(next.element, next.nextPath, key) })

            is JsonObject -> JsonObject(
                doc.toMutableMap().apply { this[next.currentPath] = remove(next.element, next.nextPath, key) })

            else -> doc
        }
    }

    /**
     * Replace the value at the target path with a new value.
     */
    @SinceKotlin("1.0.0")
    private fun replace(doc: JsonElement, path: String, key: String, value: JsonElement): JsonElement {
        return add(remove(doc, path, key), path, key, value)
    }

    /**
     * Copy the value at from path to the target path.
     */
    @SinceKotlin("1.0.0")
    private fun copy(doc: JsonElement, from: String, path: String, key: String): JsonElement {
        val value = doc.getNode(from)
        require(value != null) { "Missing path property: $from" }
        return add(doc, path, key, value)
    }

    /**
     * Move the value at from path to the target path.
     */
    @SinceKotlin("1.0.0")
    private fun move(doc: JsonElement, from: String, path: String, pathKey: String): JsonElement {
        val value = doc.getNode(from)
        require(value != null) { "Missing path property: $from" }
        val fromPath = from.substringBeforeLast("/")
        val fromKey = from.substringAfterLast("/")
        return add(remove(doc, fromPath, fromKey), path, pathKey, value)
    }

    /**
     * Add the specified [JsonElement] to the [JsonElement].
     *
     * @param key the key of the [JsonElement] to add for [JsonObject] type, or the index for [JsonArray] type.
     * @param value the [JsonElement] to add.
     * @return the modified [JsonElement].
     */
    @SinceKotlin("1.0.0")
    private fun JsonElement.add(key: String, value: JsonElement): JsonElement {
        return when (this) {
            is JsonArray -> JsonArray(this.toMutableList().apply { this.add(key.toElementIndex(this.size), value) })
            is JsonObject -> JsonObject(this.toMutableMap().apply { this[key] = value })
            else -> this
        }
    }

    /**
     * Removes the [JsonElement] associated with the specified key.
     *
     * @param key the key of the [JsonElement] to remove for [JsonObject] type, or the index for [JsonArray] type.
     * @return the modified [JsonElement].
     */
    @SinceKotlin("1.0.0")
    private fun JsonElement.remove(key: String): JsonElement {
        return when (this) {
            is JsonArray -> JsonArray(this.toMutableList().apply { this.removeAt(key.toElementIndex(this.size - 1)) })
            is JsonObject -> {
                require(this.containsKey(key)) { "Missing \"path\" property: $key" }
                JsonObject(this.toMutableMap().apply { this.remove(key) })
            }

            else -> this
        }
    }

    /**
     * Inner class that encapsulates the next element and path during recursive navigation in a [JsonElement]
     *
     * @param doc the element to navigate.
     * @param path the path to the element to navigate to.
     */
    @SinceKotlin("1.0.0")
    private class ElementNext(doc: JsonElement, path: String) {
        /**
         * The current path to the element.
         */
        var currentPath: String
            private set

        /**
         * The remaining path to the element to navigate to.
         */
        var nextPath: String
            private set

        /**
         * The element at the current path. Set to [JsonNull] if no element exists, or the element neither [JsonObject] or [JsonArray].
         */
        var element: JsonElement
            private set

        init {
            currentPath = path.substringAfter("/").substringBefore("/")
            nextPath = path.substring(currentPath.length + 1)
            element = when (doc) {
                is JsonArray -> doc[currentPath.toInt()]
                is JsonObject -> doc[currentPath] ?: JsonNull
                else -> JsonNull
            }
        }
    }

}

/**
 * Retrieves the [JsonElement] associated with the specified path.
 *
 * ```
 * val element = Json.parseToJsonElement("{\"name\": \"John\"}")
 * val result: JsonElement? = element.getNode("/name")
 * println(result) // result: JsonPrimitive("John")
 * ```
 * @param path the path to the desired element, it should start with "/" and cannot end with "/".
 * @return the [JsonElement] associated with the specified path, or null if there is no element with such path.
 */
@SinceKotlin("1.0.0")
fun JsonElement.getNode(path: String): JsonElement? {
    if (this !is JsonObject && this !is JsonArray) return this
    // empty path represents root, return itself
    if (path.isBlank()) return this
    require(path.startsWith("/")) { "path should start with a slash" }
    require(!path.endsWith("/")) { "path can not end with a slash" }
    // Split the path into segments
    val pathIterator = path.substring(1).split("/").iterator()
    // Traverse the element tree
    var element: JsonElement? = this
    while (pathIterator.hasNext()) {
        element = when (element) {
            is JsonArray -> element[pathIterator.next().toElementIndex(element.size)]
            is JsonObject -> element[pathIterator.next()]
            // if element is null or JsonPrimitive, ignore the remaining path and break the loop
            else -> break
        }
    }
    return element
}

/**
 * Retrieves the text content of the [JsonElement] associated with the specified path.
 *
 * ```
 * val element = Json.parseToJsonElement("{\"name\": \"John\"}")
 * val text: String = element.getNodeText("/name")
 * println(test) // result: John
 * ```
 * @param path the path to the desired element, it should start with "/" and cannot end with "/".
 * @return the text content of the [JsonElement] at the path, or empty string if there is no element with such path.
 * @see [getNode]
 */
@SinceKotlin("1.0.0")
fun JsonElement.getNodeContent(path: String): String {
    val node = this.getNode(path)
    if (node !is JsonPrimitive) return ""
    return node.content
}

/**
 * Parses the string as an [Int] number with in the specified size and returns the result.
 *
 * ```
 * val index = "1".toElementIndex(10) // index: 1
 * val lastIndex = "-".toElementIndex(10) // lastIndex: 9
 * val negativeIndex = "-1".toElementIndex(10) // throw IllegalArgumentException
 * val outOfBoundIndex = "11".toElementIndex(10) // throw IllegalArgumentException
 * ```
 * @param size the size of the [JsonArray].
 * @return return the number, if the string is `-`, it will return [size].
 * @throws IllegalArgumentException the string is not `-`, the result is a negative number or larger than [size].
 */
@SinceKotlin("1.0.0")
private fun String.toElementIndex(size: Int): Int {
    val index = this.toIntOrNull()
    require(this == "-" || (index != null && index >= 0)) { "Invalid array index: $this" }
    require(index == null || index <= size) { "Index out of bounds: $index" }
    return index ?: size
}
