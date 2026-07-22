package com.slovy.slovymovyapp.data.export

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSURL
import platform.UIKit.*

/** Presents the system share sheet for [filePath] over the current top view controller. */
@OptIn(ExperimentalForeignApi::class)
internal fun presentFileShareSheet(filePath: String) {
    val presenter = topViewController() ?: error("No iOS view controller available for sharing.")
    val fileUrl = NSURL.fileURLWithPath(filePath)
    val shareSheet = UIActivityViewController(
        activityItems = listOf(fileUrl),
        applicationActivities = null
    )
    shareSheet.popoverPresentationController?.sourceView = presenter.view
    presenter.presentViewController(shareSheet, animated = true, completion = null)
}

internal fun topViewController(): UIViewController? {
    val controller = activeWindow()
        ?.rootViewController
        ?: UIApplication.sharedApplication.keyWindow?.rootViewController
    var current = controller
    while (current?.presentedViewController != null) {
        current = current.presentedViewController
    }
    return current
}

private fun activeWindow(): UIWindow? {
    val scenes = UIApplication.sharedApplication.connectedScenes
        .mapNotNull { it as? UIWindowScene }
    val foregroundScenes = scenes.filter { scene ->
        scene.activationState == UISceneActivationStateForegroundActive
    }
    val windows = (foregroundScenes.ifEmpty { scenes }).flatMap { scene ->
        scene.windows.mapNotNull { it as? UIWindow }
    }
    return windows.firstOrNull { window -> window.isKeyWindow() } ?: windows.firstOrNull()
}
