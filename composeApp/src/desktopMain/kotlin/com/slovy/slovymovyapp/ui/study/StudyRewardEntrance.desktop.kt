package com.slovy.slovymovyapp.ui.study

import androidx.compose.runtime.Composable

// Desktop exposes no reduced-motion preference; always play the entrance.
@Composable
actual fun rememberReduceMotion(): Boolean = false