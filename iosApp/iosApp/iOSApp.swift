import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        // Called when the app is relaunched after being killed mid-download.
        // iOS delivers the pending delegate callbacks for the background session,
        // then we call completionHandler() to let iOS know we're done processing.
        completionHandler()
    }
}
