package com.slovy.slovymovyapp.ui

import com.slovy.slovymovyapp.i18n.UiText

/**
 * State of a [FeedbackDialog] form, shared by every screen that submits a comment to GitHub:
 * app feedback in Settings, word feedback in Word Details, and list suggestions in Search.
 *
 * Screens embed this as a single field in their own `UiState` rather than repeating the six
 * values, and drive it through the transitions below so that opening, dismissing, and the
 * submit lifecycle behave identically everywhere.
 *
 * What each screen still owns is the submission itself — which client call to make, what must be
 * true before sending, and what to do once a URL comes back — because those genuinely differ.
 */
data class FeedbackFormState(
    val dialogVisible: Boolean = false,
    val comment: String = "",
    val email: String = "",
    val submitting: Boolean = false,
    val error: UiText? = null,
    /** URL of the created issue or discussion; non-null switches the dialog to its sent state. */
    val resultUrl: String? = null,
) {
    val trimmedComment: String get() = comment.trim()

    /** Trimmed email, or null when blank, so an empty field is omitted rather than sent as "". */
    val trimmedEmail: String? get() = email.trim().takeIf { it.isNotBlank() }

    /** Opens a blank form, discarding anything left over from a previous submission. */
    fun opened(): FeedbackFormState = FeedbackFormState(dialogVisible = true)

    /** Closes and clears the form. Callers guard this while [submitting] is true. */
    fun dismissed(): FeedbackFormState = FeedbackFormState()

    /** Typing clears the error so a rejected comment stops looking rejected as it is fixed. */
    fun withComment(value: String): FeedbackFormState = copy(comment = value, error = null)

    fun withEmail(value: String): FeedbackFormState = copy(email = value)

    fun submissionStarted(): FeedbackFormState = copy(submitting = true, error = null)

    fun submissionSucceeded(url: String?): FeedbackFormState =
        copy(submitting = false, error = null, resultUrl = url)

    fun submissionFailed(message: UiText): FeedbackFormState =
        copy(submitting = false, error = message)
}
