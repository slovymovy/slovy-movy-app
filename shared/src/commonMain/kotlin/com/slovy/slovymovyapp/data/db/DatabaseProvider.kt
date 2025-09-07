package com.slovy.slovymovyapp.data.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.dictionary.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.db.Settings
import com.slovy.slovymovyapp.dictionary.*
import com.slovy.slovymovyapp.translation.Example_translation
import com.slovy.slovymovyapp.translation.Sense_target_definition
import com.slovy.slovymovyapp.translation.Sense_translation
import com.slovy.slovymovyapp.translation.TranslationDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.uuid.Uuid

object DatabaseProvider {
    fun createAppDatabase(driver: SqlDriver): AppDatabase = AppDatabase(
        driver = driver,
        settingsAdapter = Settings.Adapter(
            json_valueAdapter = object : ColumnAdapter<JsonElement, String> {
                override fun decode(databaseValue: String): JsonElement {
                    return Json.parseToJsonElement(databaseValue)
                }

                override fun encode(value: JsonElement): String {
                    return value.toString()
                }
            },
            idAdapter = object : ColumnAdapter<Setting.Name, String> {
                override fun decode(databaseValue: String): Setting.Name {
                    return Setting.Name.valueOf(databaseValue)
                }

                override fun encode(value: Setting.Name): String {
                    return value.name
                }
            }
        ),
    )

    fun createTranslationDatabase(driver: SqlDriver): TranslationDatabase = TranslationDatabase(
        driver,
        example_translationAdapter = Example_translation.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter()
        ),
        sense_target_definitionAdapter = Sense_target_definition.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter()
        ),
        sense_translationAdapter = Sense_translation.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter()
        ),
    )

    fun createDictionaryDatabase(driver: SqlDriver): DictionaryDatabase = DictionaryDatabase(
        driver,
        formAdapter = Form.Adapter(
            lemma_idAdapter = UuidByteArrayColumnAdapter(),
            form_idAdapter = UuidByteArrayColumnAdapter(),
        ),
        form_tagAdapter = Form_tag.Adapter(
            form_idAdapter = UuidByteArrayColumnAdapter(),
        ),
        lemmaAdapter = Lemma.Adapter(
            idAdapter = UuidByteArrayColumnAdapter(),
            posAdapter = DictionaryPosIntColumnAdapter(),
        ),
        senseAdapter = Sense.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter(),
            lemma_idAdapter = UuidByteArrayColumnAdapter(),
            learner_levelAdapter = LearnerLevelIntColumnAdapter(),
            frequencyAdapter = SenseFrequencyIntColumnAdapter(),
            name_typeAdapter = NameTypeIntColumnAdapter()
        ),
        sense_antonymAdapter = Sense_antonym.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter(),

            ),
        sense_common_phraseAdapter = Sense_common_phrase.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter(),

            ),
        sense_exampleAdapter = Sense_example.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter(),

            ),
        sense_synonymAdapter = Sense_synonym.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter(),

            ),
        sense_traitAdapter = Sense_trait.Adapter(
            sense_idAdapter = UuidByteArrayColumnAdapter(),
            trait_typeAdapter = TraitTypeIntColumnAdapter()
        ),
    )
}

class UuidByteArrayColumnAdapter : ColumnAdapter<Uuid, ByteArray> {
    override fun decode(databaseValue: ByteArray): Uuid {
        return Uuid.fromByteArray(databaseValue)
    }

    override fun encode(value: Uuid): ByteArray {
        return value.toByteArray()
    }
}

class DictionaryPosIntColumnAdapter : ColumnAdapter<DictionaryPos, Long> {
    override fun decode(databaseValue: Long): DictionaryPos {
        return DictionaryPos.from(databaseValue)
    }

    override fun encode(value: DictionaryPos): Long {
        return value.i
    }
}

class LearnerLevelIntColumnAdapter : ColumnAdapter<LearnerLevel, Long> {
    override fun decode(databaseValue: Long): LearnerLevel {
        return LearnerLevel.from(databaseValue)
    }

    override fun encode(value: LearnerLevel): Long {
        return value.i.toLong()
    }
}

class SenseFrequencyIntColumnAdapter : ColumnAdapter<SenseFrequency, Long> {
    override fun decode(databaseValue: Long): SenseFrequency {
        return SenseFrequency.from(databaseValue)
    }

    override fun encode(value: SenseFrequency): Long {
        return value.i.toLong()
    }
}

class NameTypeIntColumnAdapter : ColumnAdapter<NameType, Long> {
    override fun decode(databaseValue: Long): NameType {
        return NameType.from(databaseValue)
    }

    override fun encode(value: NameType): Long {
        return value.i.toLong()
    }
}

class TraitTypeIntColumnAdapter : ColumnAdapter<TraitType, Long> {
    override fun decode(databaseValue: Long): TraitType {
        return TraitType.from(databaseValue)
    }

    override fun encode(value: TraitType): Long {
        return value.i.toLong()
    }
}

