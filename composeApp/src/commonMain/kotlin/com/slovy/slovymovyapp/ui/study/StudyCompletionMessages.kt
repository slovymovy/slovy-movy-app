package com.slovy.slovymovyapp.ui.study

import com.slovy.slovymovyapp.data.Language

private val MESSAGES_BY_LANGUAGE: Map<Language, List<String>> = mapOf(
    Language.ENGLISH to listOf("Well done!", "Great work!", "Nicely done!", "Good job!"),
    Language.DUTCH to listOf("Goed gedaan!", "Goed bezig!", "Netjes!", "Lekker gewerkt!"),
    Language.POLISH to listOf("Dobra robota!", "Brawo!", "Świetnie!", "Udało się!"),
    Language.RUSSIAN to listOf("Отлично!", "Хорошая работа!", "Молодец!", "Готово!"),
)

fun randomCompletionMessage(language: Language?): String {
    val list = MESSAGES_BY_LANGUAGE[language] ?: MESSAGES_BY_LANGUAGE.getValue(Language.ENGLISH)
    return list.random()
}
