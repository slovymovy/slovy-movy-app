# Third-Party Notices

This repository contains project-owned application source code plus some
third-party and separately licensed materials. The repository `LICENSE` applies
only to the project-owned Licensed Work described there.

## Language data and test fixtures

Vocabulary, dictionary, translation, db-extract, processed word JSON, and other
language data are not project-owned application source code.

The JSON files in these server test-resource directories are language-data
fixtures:

- `server/src/test/resources/db_extract/`
- `server/src/test/resources/processed_json_files/`

They are copied from, derived from, or shaped to match data from the
`slovymovy/words` repository and Wiktionary-derived extraction pipelines. They
remain under Creative Commons Attribution-ShareAlike 4.0 International
(CC-BY-SA-4.0), or another license stated by their upstream source if a specific
fixture identifies one.

Sources:

- https://github.com/slovymovy/words
- https://www.wiktionary.org/
- https://kaikki.org/

## FSRS-Kotlin

Files under `shared/src/commonMain/kotlin/external/fsrs/` are adapted from
FSRS-Kotlin and remain under the MIT License. See the colocated notice:

- `shared/src/commonMain/kotlin/external/fsrs/THIRD_PARTY_NOTICES.md`
