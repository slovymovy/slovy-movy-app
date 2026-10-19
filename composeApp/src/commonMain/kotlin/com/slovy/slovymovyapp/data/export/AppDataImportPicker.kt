package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.remote.PlatformFileInput

/**
 * Lets the user choose an export archive with the platform file picker. Staging and validation
 * are platform-independent and live in [AppDataImportManager].
 */
expect class AppDataImportPicker(androidContext: Any? = null) {
    val isSupported: Boolean

    /** Whether [quitToApply] can end the process so the next launch applies a staged import. */
    val canQuitToApply: Boolean

    /** Returns `null` when the user dismissed the picker without choosing a file. */
    suspend fun pickArchive(): PickedAppDataArchive?

    fun quitToApply()
}

class PickedAppDataArchive(
    val displayName: String,
    /** Archive size when the platform reports it, so staging can check free space before copying. */
    val sizeBytes: Long?,
    /** Open stream positioned at the start of the archive; the consumer closes it. */
    val input: PlatformFileInput,
    /** Releases any temporary copy the picker made. Safe to call once the stream is closed. */
    val release: () -> Unit,
)
