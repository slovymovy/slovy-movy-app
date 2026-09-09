package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language

/** Playback phase of one inline speaker control. */
enum class RowAudioPhase {
    IDLE, PREPARING, PLAYING
}

/** Everything an inline speaker needs to render: its phase and a toggle callback. */
data class AudioControl(
    val phase: RowAudioPhase,
    val onToggle: () -> Unit,
)

/**
 * Addresses of the things one [RowAudioController] can speak. A sense's lemma keeps the bare sense
 * id so keys stay readable in logs; examples have no id of their own, so they are addressed by
 * their position inside the sense. Both the controller and [RowAudioUiState] build keys here so the
 * two can never drift, and no caller has to assemble — or parse — a key string itself.
 */
object RowAudioKeys {
    fun lemma(senseId: String): String = senseId

    fun example(senseId: String, index: Int): String = "$senseId#ex$index"

    /**
     * Word details' hero speaker, which reads the page's headline word rather than a row: there is
     * one per screen and it belongs to no sense, so it is keyed by the lemma under its own prefix.
     */
    fun word(lemma: String): String = "word:$lemma"
}

/**
 * Snapshot of [RowAudioController] state for list-style screens (My words, list detail). Plain data
 * so `*Content` composables and previews stay free of runtime services.
 */
data class RowAudioUiState(
    /** Key currently speaking (stop glyph). Null when nothing is playing. */
    val playingKey: String? = null,
    /** Key whose audio is loading (spinner). Null when nothing is preparing. */
    val preparingKey: String? = null,
    /** Non-null while the first-run voice setup sheet should be shown for this language. */
    val voiceSetupLanguage: Language? = null,
    /**
     * Languages the TTS engine can speak with at least one enabled voice. Null until availability
     * resolves; empty means no playable language (e.g. Desktop, or every voice disabled in
     * Settings). Drives whether a row shows the speaker at all.
     */
    val availableLanguages: Set<Language>? = null,
) {
    /**
     * Whether [language] can be spoken. True while availability is still unknown so the speaker
     * shows optimistically; once resolved, unplayable languages hide it.
     */
    fun isPlayable(language: Language): Boolean {
        val known = availableLanguages ?: return true
        return language in known
    }

    /**
     * Whether [language] is *known* to be speakable — false until availability resolves, where
     * [isPlayable] is optimistically true. Use this for a control that is always on screen and only
     * changes enabled state: optimism there buys no layout stability, and offers a button that
     * silently does nothing (and logs a play click) on a device with no voice for the language.
     */
    fun isKnownPlayable(language: Language): Boolean =
        availableLanguages?.contains(language) == true

    fun phaseFor(key: String): RowAudioPhase = when (key) {
        playingKey -> RowAudioPhase.PLAYING
        preparingKey -> RowAudioPhase.PREPARING
        else -> RowAudioPhase.IDLE
    }

    /** Speaker for a row's lemma, or null when [language] can't be spoken (no dead control). */
    fun controlFor(senseId: String, language: Language, actions: RowAudioActions): AudioControl? {
        if (!isPlayable(language)) return null
        return AudioControl(
            phase = phaseFor(RowAudioKeys.lemma(senseId)),
            onToggle = { actions.onToggle(senseId) },
        )
    }

    /**
     * Speaker for the example at [index] of [senseId]. Examples are in the same language as their
     * lemma, so the same availability gate applies; only the source sentence is spoken, never its
     * translation, which may be in a language with no installed voice.
     */
    fun controlForExample(
        senseId: String,
        index: Int,
        language: Language,
        actions: RowAudioActions,
    ): AudioControl? {
        if (!isPlayable(language)) return null
        return AudioControl(
            phase = phaseFor(RowAudioKeys.example(senseId, index)),
            onToggle = { actions.onToggleExample(senseId, index) },
        )
    }
}

/** Callbacks a screen wires from its view model to drive [RowAudioController]. */
data class RowAudioActions(
    val onToggle: (senseId: String) -> Unit = {},
    val onToggleExample: (senseId: String, index: Int) -> Unit = { _, _ -> },
    val onOpenVoiceSettings: () -> Unit = {},
    val onDismissVoiceSetup: () -> Unit = {},
    val onDismissVoiceSetupAndPlay: () -> Unit = {},
)
