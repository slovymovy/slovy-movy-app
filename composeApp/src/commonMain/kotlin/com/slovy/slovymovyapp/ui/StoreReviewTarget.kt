package com.slovy.slovymovyapp.ui

enum class StoreReviewTarget {
    GOOGLE_PLAY,
    APP_STORE
}

expect fun storeReviewTarget(): StoreReviewTarget?

expect fun storeReviewUrl(): String?
