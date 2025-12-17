package com.slovy.slovymovyapp.ui

/**
 * Represents an acknowledgment for an open source project or data source
 */
data class Acknowledgment(
    val name: String,
    val description: String,
    val license: String
)

/**
 * List of all open source projects and data sources used in this application
 */
val acknowledgments = listOf(
    Acknowledgment(
        name = "Wiktionary",
        description = "A free multilingual dictionary maintained by the Wikimedia Foundation. We are grateful for their comprehensive linguistic data that powers our dictionary content.",
        license = "Licensed under Creative Commons BY-SA 3.0"
    ),
    Acknowledgment(
        name = "Kaikki.org",
        description = "A parser for Wiktionary data that transforms raw wiki markup into structured, machine-readable format. Thank you for making linguistic data accessible to developers.",
        license = "Data provided under Creative Commons BY-SA 3.0"
    ),
    Acknowledgment(
        name = "wordfreq",
        description = "A library for word frequency data across multiple languages. We appreciate their research and data collection that helps us indicate word usage frequency.",
        license = "Licensed under MIT License"
    )
)
