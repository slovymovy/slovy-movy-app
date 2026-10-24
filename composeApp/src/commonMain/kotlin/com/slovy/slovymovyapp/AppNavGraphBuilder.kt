package com.slovy.slovymovyapp

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.slovy.slovymovyapp.share.SharedTextDispatcher

/**
 * Registers `App`'s screens. Every screen states whether shared text may navigate away from it
 * (see [SharedTextDispatcher]), so a new screen cannot be added without deciding.
 */
internal class AppNavGraphBuilder(
    val builder: NavGraphBuilder,
    val sharedTextDispatcher: SharedTextDispatcher,
) {
    inline fun <reified T : AppDestination> screen(
        interruptibleByShare: Boolean,
        noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
    ) {
        if (interruptibleByShare) sharedTextDispatcher.allowInterrupting(T::class)
        builder.composable<T>(content = content)
    }
}
