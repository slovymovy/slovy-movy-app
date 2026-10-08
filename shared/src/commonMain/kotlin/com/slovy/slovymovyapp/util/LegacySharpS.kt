package com.slovy.slovymovyapp.util

// TODO(data-version): delete this file at the next DataDbManager.VERSION bump, once every dictionary and
//  translation DB is rebuilt with the ß-folding stripAccents (the bump also wipes local caches). Callers then
//  look up the stripAccents result alone.

/**
 * The spellings a [stripAccents]-normalized query is looked up under, for data normalized before
 * [stripAccents] folded ß to "ss".
 *
 * v15 DBs built earlier, above all every `translation_*_de.db` ("Straße" stored as `straße`), and the
 * local caches filled from them keep ß in their normalized columns until a data-version reset, while
 * newer data and the query itself hold "ss". So each "ss" is also tried as "ß": `gross` yields `gross`
 * and `groß`, `strasse` yields `strasse` and `straße`. The query itself always comes first.
 *
 * Only the first [MAX_SHARP_S_SUBSTITUTIONS] occurrences are expanded, because the variant count
 * doubles with each one.
 */
fun legacySharpSSpellings(normalized: String): List<String> {
    val positions = mutableListOf<Int>()
    var index = normalized.indexOf("ss")
    while (index >= 0 && positions.size < MAX_SHARP_S_SUBSTITUTIONS) {
        positions += index
        index = normalized.indexOf("ss", index + 2)
    }
    if (positions.isEmpty()) return listOf(normalized)

    return (0 until (1 shl positions.size)).map { mask ->
        buildString {
            var last = 0
            positions.forEachIndexed { bit, position ->
                append(normalized, last, position)
                append(if (mask and (1 shl bit) != 0) "ß" else "ss")
                last = position + 2
            }
            append(normalized, last, normalized.length)
        }
    }
}

private const val MAX_SHARP_S_SUBSTITUTIONS = 3
