package com.slovy.slovymovyapp.server.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchemaConverterTest {

    @Test
    fun testBasicObjectConversion() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                },
                "age": {
                    "type": "number"
                }
            },
            "required": ["name"]
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        assertEquals("object", (result["type"] as JsonPrimitive).content)
        assertTrue(result.containsKey("properties"))
        assertTrue(result.containsKey("required"))

        val properties = result["properties"] as JsonObject
        assertTrue(properties.containsKey("name"))
        assertTrue(properties.containsKey("age"))

        val nameProperty = properties["name"] as JsonObject
        assertEquals("string", (nameProperty["type"] as JsonPrimitive).content)

        val ageProperty = properties["age"] as JsonObject
        assertEquals("number", (ageProperty["type"] as JsonPrimitive).content)

        // Test that the converted schema can be used with OpenAI's buildJsonSchema
        validateConvertedSchema(result)
    }

    @Test
    fun testEnumConversion() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "status": {
                    "type": "string",
                    "enum": ["active", "inactive", "pending"]
                }
            }
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        val properties = result["properties"] as JsonObject
        val statusProperty = properties["status"] as JsonObject

        assertEquals("string", (statusProperty["type"] as JsonPrimitive).content)
        assertTrue(statusProperty.containsKey("enum"))

        val enumValues = statusProperty["enum"] as JsonArray
        assertEquals(3, enumValues.size)
        assertEquals("active", (enumValues[0] as JsonPrimitive).content)
        assertEquals("inactive", (enumValues[1] as JsonPrimitive).content)
        assertEquals("pending", (enumValues[2] as JsonPrimitive).content)

        // Test that the converted schema can be used with OpenAI's buildJsonSchema
        validateConvertedSchema(result)
    }

    @Test
    fun testArrayWithStringItemsConversion() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "tags": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    }
                }
            }
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        val properties = result["properties"] as JsonObject
        val tagsProperty = properties["tags"] as JsonObject

        assertEquals("array", (tagsProperty["type"] as JsonPrimitive).content)
        assertTrue(tagsProperty.containsKey("items"))

        val items = tagsProperty["items"] as JsonObject
        assertEquals("string", (items["type"] as JsonPrimitive).content)

        // Test that the converted schema can be used with OpenAI's buildJsonSchema
        validateConvertedSchema(result)
    }

    @Test
    fun testArrayWithObjectItemsConversion() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "users": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "id": {
                                "type": "number"
                            },
                            "name": {
                                "type": "string"
                            }
                        },
                        "required": ["id"]
                    }
                }
            }
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        val properties = result["properties"] as JsonObject
        val usersProperty = properties["users"] as JsonObject

        assertEquals("array", (usersProperty["type"] as JsonPrimitive).content)

        val items = usersProperty["items"] as JsonObject
        assertEquals("object", (items["type"] as JsonPrimitive).content)
        assertTrue(items.containsKey("properties"))
        assertTrue(items.containsKey("required"))

        val itemProperties = items["properties"] as JsonObject
        assertTrue(itemProperties.containsKey("id"))
        assertTrue(itemProperties.containsKey("name"))
    }

    @Test
    fun testNestedObjectConversion() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "user": {
                    "type": "object",
                    "properties": {
                        "profile": {
                            "type": "object",
                            "properties": {
                                "avatar": {
                                    "type": "string"
                                },
                                "bio": {
                                    "type": "string"
                                }
                            }
                        }
                    }
                }
            }
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        val properties = result["properties"] as JsonObject
        val userProperty = properties["user"] as JsonObject

        assertEquals("object", (userProperty["type"] as JsonPrimitive).content)

        val userProperties = userProperty["properties"] as JsonObject
        val profileProperty = userProperties["profile"] as JsonObject

        assertEquals("object", (profileProperty["type"] as JsonPrimitive).content)

        val profileProperties = profileProperty["properties"] as JsonObject
        assertTrue(profileProperties.containsKey("avatar"))
        assertTrue(profileProperties.containsKey("bio"))
    }

    @Test
    fun testComplexSchemaWithTraitTypes() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "traits": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "trait_type": {
                                "type": "string",
                                "enum": ["dated", "colloquial", "slang", "technical", "formal", "informal"]
                            },
                            "comment": {
                                "type": "string"
                            }
                        },
                        "required": ["trait_type"]
                    }
                }
            }
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        val properties = result["properties"] as JsonObject
        val traitsProperty = properties["traits"] as JsonObject

        assertEquals("array", (traitsProperty["type"] as JsonPrimitive).content)

        val items = traitsProperty["items"] as JsonObject
        assertEquals("object", (items["type"] as JsonPrimitive).content)

        val itemProperties = items["properties"] as JsonObject
        val traitTypeProperty = itemProperties["trait_type"] as JsonObject

        assertEquals("string", (traitTypeProperty["type"] as JsonPrimitive).content)
        assertTrue(traitTypeProperty.containsKey("enum"))

        val enumValues = traitTypeProperty["enum"] as JsonArray
        assertEquals(6, enumValues.size)
        val enumStrings = enumValues.map { (it as JsonPrimitive).content }
        assertTrue(enumStrings.contains("technical"))
        assertTrue(enumStrings.contains("formal"))
        assertTrue(enumStrings.contains("informal"))

        // Test that the converted schema can be used with OpenAI's buildJsonSchema
        validateConvertedSchema(result)
    }

    @Test
    fun testArrayWithEnumItemsConversion() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "statuses": {
                    "type": "array",
                    "items": {
                        "type": "string",
                        "enum": ["success", "error", "warning"]
                    }
                }
            }
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        val properties = result["properties"] as JsonObject
        val statusesProperty = properties["statuses"] as JsonObject

        assertEquals("array", (statusesProperty["type"] as JsonPrimitive).content)

        val items = statusesProperty["items"] as JsonObject
        assertEquals("string", (items["type"] as JsonPrimitive).content)
        assertTrue(items.containsKey("enum"))

        val enumValues = items["enum"] as JsonArray
        assertEquals(3, enumValues.size)
    }

    @Test
    fun testMixedArrayAndObjectSchema() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "examples": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "text": {
                                "type": "string"
                            },
                            "translation": {
                                "type": "string"
                            }
                        },
                        "required": ["text"]
                    }
                },
                "synonyms": {
                    "type": "array",
                    "items": {
                        "type": "string"
                    }
                },
                "level": {
                    "type": "string",
                    "enum": ["A1", "A2", "B1", "B2", "C1", "C2"]
                }
            },
            "required": ["examples", "level"]
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        assertEquals("object", (result["type"] as JsonPrimitive).content)

        val properties = result["properties"] as JsonObject
        assertTrue(properties.containsKey("examples"))
        assertTrue(properties.containsKey("synonyms"))
        assertTrue(properties.containsKey("level"))

        // Test examples array with object items
        val examplesProperty = properties["examples"] as JsonObject
        assertEquals("array", (examplesProperty["type"] as JsonPrimitive).content)
        val examplesItems = examplesProperty["items"] as JsonObject
        assertEquals("object", (examplesItems["type"] as JsonPrimitive).content)

        // Test synonyms array with string items
        val synonymsProperty = properties["synonyms"] as JsonObject
        assertEquals("array", (synonymsProperty["type"] as JsonPrimitive).content)
        val synonymsItems = synonymsProperty["items"] as JsonObject
        assertEquals("string", (synonymsItems["type"] as JsonPrimitive).content)

        // Test level enum
        val levelProperty = properties["level"] as JsonObject
        assertEquals("string", (levelProperty["type"] as JsonPrimitive).content)
        assertTrue(levelProperty.containsKey("enum"))

        // Test required array
        assertTrue(result.containsKey("required"))
        val requiredArray = result["required"] as JsonArray
        assertEquals(2, requiredArray.size)

        // Test that the converted schema can be used with OpenAI's buildJsonSchema
        validateConvertedSchema(result)
    }

    @Test
    fun testEmptyObjectConversion() {
        val geminiSchema = """
        {
            "type": "object"
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        assertEquals("object", (result["type"] as JsonPrimitive).content)
        assertEquals(2, result.size) // Should only have the type field + additionalProperties
        assertTrue(result.containsKey("type"))
        assertTrue(result.containsKey("additionalProperties"))
        assertEquals("false", result["additionalProperties"]!!.toString())
    }

    @Test
    fun testSchemaWithAdditionalProperties() {
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                }
            },
            "additionalProperties": false
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        assertEquals("object", (result["type"] as JsonPrimitive).content)
        assertTrue(result.containsKey("properties"))
        assertTrue(result.containsKey("additionalProperties"))
        assertEquals("false", result["additionalProperties"]!!.toString())
    }

    @Test
    fun testSchemaConversionPreservesStructure() {
        // This test verifies that the overall structure is preserved
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "word": {
                    "type": "string"
                },
                "definitions": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "text": {"type": "string"},
                            "level": {
                                "type": "string", 
                                "enum": ["A1", "B1", "C1"]
                            }
                        }
                    }
                }
            },
            "required": ["word"]
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        // Verify the conversion maintains the structure
        assertTrue(result.containsKey("type"))
        assertTrue(result.containsKey("properties"))
        assertTrue(result.containsKey("required"))

        val properties = result["properties"] as JsonObject
        assertTrue(properties.containsKey("word"))
        assertTrue(properties.containsKey("definitions"))

        // Verify nested array structure is preserved
        val definitions = properties["definitions"] as JsonObject
        assertEquals("array", (definitions["type"] as JsonPrimitive).content)
        val items = definitions["items"] as JsonObject
        assertEquals("object", (items["type"] as JsonPrimitive).content)

        // Verify enum in nested structure is preserved
        val itemProps = items["properties"] as JsonObject
        val level = itemProps["level"] as JsonObject
        assertTrue(level.containsKey("enum"))
        val enumArray = level["enum"] as JsonArray
        assertEquals(3, enumArray.size)

        // Test that the converted schema can be used with OpenAI's buildJsonSchema
        validateConvertedSchema(result)
    }

    @Test
    fun testSchemaValidationWithComplexExample() {
        // Test a realistic schema that's used in the actual application
        val geminiSchema = """
        {
            "type": "object",
            "properties": {
                "pos": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "part_of_speech": {
                                "type": "string"
                            },
                            "senses": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "sense_definitions": {
                                            "type": "array",
                                            "items": {
                                                "type": "string"
                                            }
                                        },
                                        "learner_level": {
                                            "type": "string",
                                            "enum": ["A1", "A2", "B1", "B2", "C1", "C2"]
                                        },
                                        "frequency": {
                                            "type": "string",
                                            "enum": ["High", "Middle", "Low", "VeryLow"]
                                        },
                                        "traits": {
                                            "type": "array",
                                            "items": {
                                                "type": "object",
                                                "properties": {
                                                    "trait_type": {
                                                        "type": "string",
                                                        "enum": ["dated", "colloquial", "slang", "technical", "formal", "informal"]
                                                    },
                                                    "comment": {
                                                        "type": "string"
                                                    }
                                                },
                                                "required": ["trait_type"]
                                            }
                                        }
                                    },
                                    "required": ["sense_definitions", "learner_level", "frequency"]
                                }
                            }
                        },
                        "required": ["part_of_speech", "senses"]
                    }
                }
            },
            "required": ["pos"]
        }
        """.trimIndent()

        val result = SchemaConverter.geminiToOpenAI(geminiSchema)

        // Verify basic structure
        assertEquals("object", (result["type"] as JsonPrimitive).content)
        assertTrue(result.containsKey("properties"))
        assertTrue(result.containsKey("required"))

        // Validate that the converted schema works with OpenAI
        validateConvertedSchema(result)

        // Additional verification: check that nested enums are properly preserved
        val pos = (result["properties"] as JsonObject)["pos"] as JsonObject
        val posItems = pos["items"] as JsonObject
        val posItemsProps = posItems["properties"] as JsonObject
        val senses = posItemsProps["senses"] as JsonObject
        val sensesItems = senses["items"] as JsonObject
        val sensesItemsProps = sensesItems["properties"] as JsonObject

        // Check learner_level enum
        val learnerLevel = sensesItemsProps["learner_level"] as JsonObject
        assertTrue(learnerLevel.containsKey("enum"))
        val learnerLevelEnum = learnerLevel["enum"] as JsonArray
        assertEquals(6, learnerLevelEnum.size)

        // Check traits enum
        val traits = sensesItemsProps["traits"] as JsonObject
        val traitsItems = traits["items"] as JsonObject
        val traitsItemsProps = traitsItems["properties"] as JsonObject
        val traitType = traitsItemsProps["trait_type"] as JsonObject
        assertTrue(traitType.containsKey("enum"))
        val traitTypeEnum = traitType["enum"] as JsonArray
        assertEquals(6, traitTypeEnum.size)

        val enumStrings = traitTypeEnum.map { (it as JsonPrimitive).content }
        assertTrue(enumStrings.contains("technical"))
        assertTrue(enumStrings.contains("formal"))
        assertTrue(enumStrings.contains("informal"))
    }

    /**
     * Helper method to validate that a converted schema can be successfully used with OpenAI's buildJsonSchema
     * and that the resulting JsonSchema.Schema is valid.
     */
    private fun validateConvertedSchema(convertedSchema: JsonObject) {
        // Test that the converted schema can be built into an OpenAI JsonSchema
        val builtSchema = OpenAI.buildJsonSchema(convertedSchema).validate()
        assertNotNull(builtSchema, "buildJsonSchema should successfully create a JsonSchema.Schema")

        val schemaJson = builtSchema.toString()
        assertNotNull(schemaJson, "Schema should be serializable to JSON")
        assertTrue(schemaJson.isNotEmpty(), "Schema JSON should not be empty")
    }
}