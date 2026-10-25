package com.slovy.slovymovyapp

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slovy.slovymovyapp.share.SharedTextReceiver

/**
 * Registers `App`'s screens. Every screen states whether shared text may navigate away from it
 * (see [SharedTextReceiver.allowInterrupting]), so a new screen cannot be added without deciding.
 */
internal class AppNavGraphBuilder(
    val builder: NavGraphBuilder,
    val sharedTextReceiver: SharedTextReceiver,
) {
    inline fun <reified T : AppDestination> screen(
        interruptibleByShare: Boolean,
        noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
    ) {
        if (interruptibleByShare) sharedTextReceiver.allowInterrupting(T::class)
        builder.composable<T>(content = content)
    }
}
