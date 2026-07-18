package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language

/** Playback phase of one row's speaker control. */
enum class RowAudioPhase {
    IDLE, PREPARING, PLAYING
}

/** Everything a row's inline speaker needs to render: its phase and a toggle callback. */
data class LemmaAudioControl(
    val phase: RowAudioPhase,
    val onToggle: () -> Unit,
)

/**
 * Snapshot of [RowAudioController] state for list-style screens (My words, list detail). Plain data
 * so `*Content` composables and previews stay free of runtime services.
 */
data class RowAudioUiState(
    /** Sense currently speaking (stop glyph). Null when nothing is playing. */
    val playingSenseId: String? = null,
    /** Sense whose audio is loading (spinner). Null when nothing is preparing. */
    val preparingSenseId: String? = null,
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

    fun phaseFor(senseId: String): RowAudioPhase = when (senseId) {
        playingSenseId -> RowAudioPhase.PLAYING
        preparingSenseId -> RowAudioPhase.PREPARING
        else -> RowAudioPhase.IDLE
    }

    /** Per-row speaker control, or null when [language] can't be spoken (no dead control). */
    fun controlFor(senseId: String, language: Language, actions: RowAudioActions): LemmaAudioControl? {
        if (!isPlayable(language)) return null
        return LemmaAudioControl(phase = phaseFor(senseId), onToggle = { actions.onToggle(senseId) })
    }
}

/** Callbacks a screen wires from its view model to drive [RowAudioController]. */
data class RowAudioActions(
    val onToggle: (senseId: String) -> Unit = {},
    val onOpenVoiceSettings: () -> Unit = {},
    val onDismissVoiceSetup: () -> Unit = {},
    val onDismissVoiceSetupAndPlay: () -> Unit = {},
)
