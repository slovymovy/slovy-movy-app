package com.slovy.slovymovyapp.ui

enum class StoreReviewTarget {
    GOOGLE_PLAY,
    APP_STORE
}

expect fun storeReviewTarget(): StoreReviewTarget?

fun StoreReviewTarget.reviewUrl(): String = when (this) {
    // Google Play has no public external URL that opens the rating dialog directly.
    StoreReviewTarget.GOOGLE_PLAY -> "https://play.google.com/store/apps/details?id=com.slovy.slovymovyapp"
    StoreReviewTarget.APP_STORE -> "https://apps.apple.com/app/openwords/id6754798978?action=write-review"
}
