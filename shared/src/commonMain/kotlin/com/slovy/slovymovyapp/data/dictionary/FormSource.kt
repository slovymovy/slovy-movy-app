package com.slovy.slovymovyapp.data.dictionary

enum class FormSource(val i: Int) {
    NATIVE(0),
    EN(1),
    HEURISTIC(2);

    companion object {
        fun from(databaseValue: Long): FormSource {
            return FormSource.entries.first { it.i.toLong() == databaseValue }
        }
    }
}
