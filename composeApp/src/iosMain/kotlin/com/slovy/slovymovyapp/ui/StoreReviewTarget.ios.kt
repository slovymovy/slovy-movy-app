package com.slovy.slovymovyapp.ui

private const val RATE_URL = "https://apps.apple.com/app/openwords/id6754798978?action=write-review"

actual fun storeReviewTarget(): StoreReviewTarget? = StoreReviewTarget.APP_STORE

actual fun storeReviewUrl(): String? = RATE_URL
