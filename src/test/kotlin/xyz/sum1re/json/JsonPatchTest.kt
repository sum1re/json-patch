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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertFailsWith

class JsonPatchTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun objectSample() {
        val element = Json.parseToJsonElement("{\"name\": \"John\"}")
        val operation = JsonPatchOperation("add", null, "/age", JsonPrimitive(30))
        val modifiedElement = JsonPatch(listOf(operation)).applyPatch(element)
        println(modifiedElement) // {"name":"John","age":30}
    }

    @Test
    fun arraySample() {
        val element = Json.parseToJsonElement("[\"apple\"]")
        val operation = JsonPatchOperation("add", null, "/1", JsonPrimitive("banana"))
        val modifiedElement = JsonPatch(listOf(operation)).applyPatch(element)
        println(modifiedElement) // ["apple","banana"]
    }

    @Test
    fun patchTest() {
        val testFile = Paths.get("src", "test", "resources", "tests.json")
        val testList = json.decodeFromString<List<TestJson>>(Files.readString(testFile))
        testList.forEach {
            println(it.comment)
            if (it.error != null)
                assertFails<IllegalArgumentException>(it.error) { JsonPatch(it.patch).applyPatch(it.doc) }
            else
                assertExpects(it.expected) { JsonPatch(it.patch).applyPatch(it.doc) }
        }
    }

    @Test
    fun extensionTest() {
        val jsonElement1 = json.parseToJsonElement(
            "{\"id\":1,\"age\":30,\"name\":\"John\",\"friends\":[{\"id\":10,\"name\":\"Colon\"}]}"
        )
        assertExpects(JsonPrimitive("John")) { jsonElement1.getNode("/name") }
        assertExpects("John") { jsonElement1.getNodeContent("/name") }
        assertExpects(null) { jsonElement1.getNode("//name") }
        assertExpects(json.parseToJsonElement("{\"id\":10,\"name\":\"Colon\"}")) { jsonElement1.getNode("/friends/0") }
        // element at /friends/0 is an object, return empty string
        assertExpects("") { jsonElement1.getNodeContent("/friends/0") }
        // /email is a non-existent element, return empty string
        assertExpects("") { jsonElement1.getNodeContent("/email") }
        assertFails<IllegalArgumentException>("path should start with a slash") { jsonElement1.getNode("name") }
        assertFails<IllegalArgumentException>("path can not end with a slash") { jsonElement1.getNode("/friends/0/") }
        // empty path means root node
        assertExpects(jsonElement1) { jsonElement1.getNode("") }
        // a slash path is invalid to get root node
        assertFails<IllegalArgumentException>("path can not end with a slash") { jsonElement1.getNode("/") }
        val jsonElement2 = json.parseToJsonElement("[1,2,3,4]")
        assertExpects(JsonPrimitive(1)) { jsonElement2.getNode("/0") }
        assertExpects("1") { jsonElement2.getNodeContent("/0") }
        assertFails<IllegalArgumentException>("Invalid array index: -1") { jsonElement2.getNode("/-1") }
        assertFails<IllegalArgumentException>("Invalid array index: le") { jsonElement2.getNode("/le") }
        // if element is JsonPrimitive, return itself
        assertExpects(JsonPrimitive(true)) { JsonPrimitive(true).getNode("") }
    }

    private fun <T> assertExpects(expected: T, block: () -> T) {
        assertEquals(expected, block())
    }

    private inline fun <reified T : Throwable> assertFails(message: String, block: () -> Unit) {
        val exception = assertFailsWith<T>(null, block)
        assertEquals(message, exception.message)
    }

}

@Serializable
class TestJson(
    val comment: String,
    val doc: JsonElement,
    val expected: JsonElement?,
    val patch: List<JsonPatchOperation>,
    val error: String?
)