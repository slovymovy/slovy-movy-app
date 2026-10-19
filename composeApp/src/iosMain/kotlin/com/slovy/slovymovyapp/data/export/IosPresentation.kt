package com.slovy.slovymovyapp.data.export

import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/** Finds the view controller that can present system sheets (share, document picker) right now. */
internal object IosPresentation {
    fun topViewController(): UIViewController? {
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
}
