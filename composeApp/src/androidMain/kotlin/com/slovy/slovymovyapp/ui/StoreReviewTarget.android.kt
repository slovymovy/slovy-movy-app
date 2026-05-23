package com.slovy.slovymovyapp.ui

private const val RATE_URL = "https://play.google.com/store/apps/details?id=com.slovy.slovymovyapp"

actual fun storeReviewTarget(): StoreReviewTarget? = StoreReviewTarget.GOOGLE_PLAY

actual fun storeReviewUrl(): String? = RATE_URL
