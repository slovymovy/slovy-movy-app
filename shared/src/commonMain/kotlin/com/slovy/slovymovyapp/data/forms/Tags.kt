package com.slovy.slovymovyapp.data.forms

import com.slovy.slovymovyapp.data.forms.TagMapping.resolve


/** A typed grammatical tag: encapsulates both the key and the value for a [GridCell.Data] entry. */
interface FormTag {
    val key: String
    val value: String
}

enum class VerbForm(override val value: String) : FormTag {
    INFINITIVE("infinitive"),
    FINITE("finite"),
    PARTICIPLE("participle"),
    GERUND("gerund"),
    ADVERBIAL("adverbial"),
    SHORT("short"),
    LONG("long"),
    DIMINUTIVE("diminutive");

    override val key = "verb_form"
}

enum class Tense(override val value: String) : FormTag {
    PRESENT("present"),
    PAST("past"),
    FUTURE("future");

    override val key = "tense"
}

enum class Mood(override val value: String) : FormTag {
    INDICATIVE("indicative"),
    SUBJUNCTIVE("subjunctive"),
    IMPERATIVE("imperative"),
    CONDITIONAL("conditional"),
    PREDICATIVE("predicative"),
    PARTITIVE("partitive"),
    IMPERSONAL("impersonal");

    override val key = "mood"
}

enum class Aspect(override val value: String) : FormTag {
    IMPERFECTIVE("imperfective"),
    PERFECTIVE("perfective");

    override val key = "aspect"
}

/** Grammatical number. Named [Num] to avoid shadowing [kotlin.Number]. */
enum class Num(override val value: String) : FormTag {
    SG("sg"),
    PL("pl");

    override val key = "number"
}

enum class Person(override val value: String) : FormTag {
    FIRST("1"),
    SECOND("2"),
    THIRD("3");

    override val key = "person"
}

enum class Gender(override val value: String) : FormTag {
    MASC("masc"),
    FEM("fem"),
    NEUT("neut"),
    COMMON("common"),
    VIRILE("virile"),
    NONVIRILE("nonvirile");

    override val key = "gender"
}

enum class Animacy(override val value: String) : FormTag {
    ANIM("anim"),
    INANIM("inanim");

    override val key = "animacy"
}

enum class Case(override val value: String) : FormTag {
    NOMINATIVE("nominative"),
    GENITIVE("genitive"),
    DATIVE("dative"),
    ACCUSATIVE("accusative"),
    INSTRUMENTAL("instrumental"),
    PREPOSITIONAL("prepositional"),
    LOCATIVE("locative"),
    VOCATIVE("vocative");

    override val key = "case"
}

enum class Degree(override val value: String) : FormTag {
    POSITIVE("positive"),
    COMPARATIVE("comparative"),
    SUPERLATIVE("superlative");

    override val key = "degree"
}

enum class Clause(override val value: String) : FormTag {
    MAIN("main"),
    SUBORDINATE("subordinate");

    override val key = "clause"
}

enum class Voice(override val value: String) : FormTag {
    ACTIVE("active"),
    PASSIVE("passive");

    override val key = "voice"
}

enum class Definiteness(override val value: String) : FormTag {
    DEFINITE("definite"),
    INDEFINITE("indefinite");

    override val key = "definiteness"
}

enum class Pronoun(override val value: String) : FormTag {
    JIJ("jij"),
    U("u"),
    GIJ("gij");

    override val key = "pronoun"
}

/** Grammatical function of a personal pronoun form (subjective, objective, or reflexive). */
enum class PronounForm(override val value: String) : FormTag {
    SUBJECTIVE("subjective"),
    OBJECTIVE("objective"),
    REFLEXIVE("reflexive");

    override val key = "pronoun_form"
}

/** Distinguishes possessive determiner ("my dog") from independent possessive pronoun ("mine"). */
enum class PossessiveType(override val value: String) : FormTag {
    DEPENDENT("dependent"),
    INDEPENDENT("independent");

    override val key = "possessive_type"
}

/**
 * Maps raw form-tag strings (as stored in the `form_tag` DB table) to typed [FormTag] enum values.
 *
 * Not every DB tag has an enum equivalent — tags that carry metadata (e.g. "canonical", "stressed",
 * "US", "UK", "error-unrecognized-form") or that are not yet modelled (e.g. "perfect",
 * "pluperfect") are absent from the map and [resolve] returns null for them.
 */
object TagMapping {

    val DB_TAG_TO_FORM_TAG: Map<String, FormTag> = mapOf(
        // ── Number ───────────────────────────────────────────────────────────
        "singular" to Num.SG,
        "plural" to Num.PL,

        // ── Person ───────────────────────────────────────────────────────────
        "first-person" to Person.FIRST,
        "second-person" to Person.SECOND,
        "third-person" to Person.THIRD,

        // ── Gender ───────────────────────────────────────────────────────────
        "masculine" to Gender.MASC,
        "feminine" to Gender.FEM,
        "neuter" to Gender.NEUT,
        "common" to Gender.COMMON,
        "virile" to Gender.VIRILE,       // pl: masculine-personal plural
        "nonvirile" to Gender.NONVIRILE,    // pl: non-masculine-personal plural

        // ── Animacy ──────────────────────────────────────────────────────────
        "animate" to Animacy.ANIM,
        "inanimate" to Animacy.INANIM,

        // ── Case ─────────────────────────────────────────────────────────────
        "nominative" to Case.NOMINATIVE,
        "genitive" to Case.GENITIVE,
        "dative" to Case.DATIVE,
        "accusative" to Case.ACCUSATIVE,
        "instrumental" to Case.INSTRUMENTAL,
        "prepositional" to Case.PREPOSITIONAL,
        "locative" to Case.LOCATIVE,
        "vocative" to Case.VOCATIVE,

        // ── Tense ────────────────────────────────────────────────────────────
        "present" to Tense.PRESENT,
        "past" to Tense.PAST,
        "future" to Tense.FUTURE,
        // Dutch wiktionary uses "imperfect" to mean "onvoltooid" (non-compound aspect); Dutch present
        // forms carry it too. NlConjugationScheme.preprocessForms strips "imperfect" from non-past
        // Dutch forms before the global mapping runs, so the global Tense.PAST mapping is safe.
        "imperfect" to Tense.PAST,

        // ── Aspect ───────────────────────────────────────────────────────────
        "imperfective" to Aspect.IMPERFECTIVE,
        "perfective" to Aspect.PERFECTIVE,

        // ── Mood ─────────────────────────────────────────────────────────────
        "indicative" to Mood.INDICATIVE,
        "subjunctive" to Mood.SUBJUNCTIVE,
        "imperative" to Mood.IMPERATIVE,
        "conditional" to Mood.CONDITIONAL,
        "impersonal" to Mood.IMPERSONAL,
        "predicative" to Mood.PREDICATIVE,
        "partitive" to Mood.PARTITIVE,

        // ── VerbForm ─────────────────────────────────────────────────────────
        "finite" to VerbForm.FINITE,
        "infinitive" to VerbForm.INFINITIVE,
        "participle" to VerbForm.PARTICIPLE,
        "adverbial" to VerbForm.ADVERBIAL,
        "gerund" to VerbForm.GERUND,
        "short-form" to VerbForm.SHORT,      // ru: short adjective form
        "long-form" to VerbForm.LONG,
        "diminutive" to VerbForm.DIMINUTIVE, // nl: diminutive noun form

        // ── Voice ────────────────────────────────────────────────────────────
        "active" to Voice.ACTIVE,
        "passive" to Voice.PASSIVE,

        // ── Degree ───────────────────────────────────────────────────────────
        "positive" to Degree.POSITIVE,
        "comparative" to Degree.COMPARATIVE,
        "superlative" to Degree.SUPERLATIVE,

        // ── Clause (nl) ──────────────────────────────────────────────────────
        "main-clause" to Clause.MAIN,
        "subordinate-clause" to Clause.SUBORDINATE,

        // ── Definiteness (nl) ────────────────────────────────────────────────
        "definite" to Definiteness.DEFINITE,
        "indefinite" to Definiteness.INDEFINITE,

        // ── Pronoun (nl) ─────────────────────────────────────────────────────
        "jij" to Pronoun.JIJ,
        "u" to Pronoun.U,
        "gij" to Pronoun.GIJ,

        // ── PronounForm (en) ─────────────────────────────────────────────────
        "subjective" to PronounForm.SUBJECTIVE,
        "objective" to PronounForm.OBJECTIVE,
        "oblique" to PronounForm.OBJECTIVE,   // he/she use "oblique" instead of "objective"
        "reflexive" to PronounForm.REFLEXIVE,

        // ── PossessiveType (en) ──────────────────────────────────────────────
        "possessive" to PossessiveType.DEPENDENT,
        "without-noun" to PossessiveType.INDEPENDENT,  // mine, ours, yours
        "noun" to PossessiveType.INDEPENDENT,           // theirs = "noun possessive"

        // Unmapped (documented here for reference):
        // "perfect"       — nl compound past (hebben/zijn + past participle); no enum yet
        // "inflected" / "uninflected" — nl adjective; no enum yet
        // "pluperfect", "anterior", "potential" — pl rare tenses; no enum yet
        // "adjectival"    — pl participial; overlaps with VerbForm.PARTICIPLE contextually
        // "contemporary"  — pl contemporary adverbial participle label; no enum yet
        // "canonical"     — marks the dictionary headword, not a paradigm slot
        // "stressed" / "unstressed", "transliteration", "US", "UK" — metadata, not paradigm tags
        // "error-unrecognized-form", "rare", "depreciative" — data-quality / register markers
    )

    /** Returns the typed [FormTag] for a raw DB tag string, or null if not modelled. */
    fun resolve(dbTag: String): FormTag? = DB_TAG_TO_FORM_TAG[dbTag]

    /**
     * Converts a flat collection of DB tag strings to typed [FormTag] values,
     * silently dropping any tags that have no enum equivalent.
     */
    fun resolve(dbTags: Iterable<String>): List<FormTag> = dbTags.mapNotNull { resolve(it) }
}