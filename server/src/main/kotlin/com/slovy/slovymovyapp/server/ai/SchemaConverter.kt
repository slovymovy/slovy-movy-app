package com.slovy.slovymovyapp.server.ai

import com.slovy.slovymovyapp.server.ServerJson
import kotlinx.serialization.json.*

/**
 * Converts the Gemini Schema format the enhancers are written in into standard JSON Schema.
 *
 * Gemini takes its own Schema dialect; OpenAI and Anthropic both take standard JSON Schema with
 * `additionalProperties: false`, so both providers share this conversion.
 */
object SchemaConverter {

    /**
     * Converts a Gemini schema string to standard JSON Schema.
     *
     * @param geminiSchema The schema string in Gemini format (JSON string)
     * @return JSON Schema as a JsonObject, accepted by OpenAI strict mode and Anthropic
     *   structured outputs alike
     */
    fun geminiToJsonSchema(geminiSchema: String): JsonObject {
        val geminiJsonObject = ServerJson.lenient.parseToJsonElement(geminiSchema).jsonObject

        return convertJsonObjectToOpenAI(geminiJsonObject)
    }

    /**
     * Flattens a [JsonElement] into the plain Kotlin maps/lists/primitives both SDKs wrap in their
     * own freeform schema values.
     *
     * Property order is preserved on purpose: structured outputs generate the fields in schema
     * order, so a reordered `properties` map makes a model write, say, examples before the
     * definition they illustrate - which is how a hollow but schema-valid card happens.
     */
    internal fun toPlainMap(element: JsonElement): Any {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content.equals("true", ignoreCase = true) -> true
                    element.content.equals("false", ignoreCase = true) -> false
                    element.content.toIntOrNull() != null -> element.content.toInt()
                    element.content.toDoubleOrNull() != null -> element.content.toDouble()
                    else -> element.content
                }
            }

            is JsonObject -> {
                val map = LinkedHashMap<String, Any>()
                element.forEach { (key, value) ->
                    map[key] = toPlainMap(value)
                }
                map
            }

            is JsonArray -> {
                element.map { toPlainMap(it) }
            }
        }
    }


    /**
     * Recursively converts a Gemini JSON schema object to OpenAI format.
     */
    private fun convertJsonObjectToOpenAI(geminiObj: JsonObject): JsonObject {
        val result = mutableMapOf<String, JsonElement>()

        geminiObj.forEach { (key, value) ->
            when (key) {
                "type" -> {
                    // Type mapping is generally the same
                    result[key] = value
                    if (value.jsonPrimitive.content == "object") {
                        // For objects, we need to add additional properties
                        result["additionalProperties"] = JsonPrimitive(false)
                    }
                }

                "properties" -> {
                    // Recursively convert properties
                    val propsObj = value.jsonObject
                    val convertedProps = mutableMapOf<String, JsonElement>()

                    propsObj.forEach { (propKey, propValue) ->
                        convertedProps[propKey] = convertJsonObjectToOpenAI(propValue.jsonObject)
                    }

                    result[key] = JsonObject(convertedProps)
                }

                "items" -> {
                    // Convert array items schema
                    when (value) {
                        is JsonObject -> {
                            result[key] = convertJsonObjectToOpenAI(value)
                        }

                        else -> {
                            result[key] = value
                        }
                    }
                }

                "enum" -> {
                    // Enum values are the same in both formats
                    result[key] = value
                }

                "required" -> {
                    // Required fields are the same in both formats
                    result[key] = value
                }

                else -> {
                    // Copy other properties as-is
                    result[key] = value
                }
            }
        }

        return JsonObject(result)
    }
}