# Simple implementation of [RFC 6902 JSON Patch](https://datatracker.ietf.org/doc/html/rfc6902) written in kotlin. 

This is a simple implementation of RFC 6902 written in Kotlin, which uses [kotlinx-serialization](https://github.com/Kotlin/kotlinx.serialization)as its core. 
It can only apply JsonPatch to JsonElement, it doesn't support diff or generating JsonPatch, if you need it try [zjsonpatch](https://github.com/flipkart-incubator/zjsonpatch) or [json-patch](https://github.com/java-json-tools/json-patch).

Generates some test patches using [fast-json-patch](https://github.com/Starcounter-Jack/JSON-Patch).

## Support operations:
- add
- remove
- replace
- move
- copy

## Sample usage

```
// object element
val element = Json.parseToJsonElement("{\"name\": \"John\"}")
val operation = JsonPatchOperation("add", "/age", JsonPrimitive(30))
val modifiedElement = JsonPatch(listOf(operation)).applyPatch(element)
println(modifiedElement) // {"name":"John","age":30}

// array element
val element = Json.parseToJsonElement("[\"apple\"]")
val operation = JsonPatchOperation("add", "/1", JsonPrimitive("banana"))
val modifiedElement = JsonPatch(listOf(operation)).applyPatch(element)
println(modifiedElement) // ["apple","banana"]
```

