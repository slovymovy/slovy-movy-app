import SwiftUI
import ComposeApp

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
        // Store the handler so the KMP session delegate can call it after all
        // pending background session events have been delivered via
        // URLSessionDidFinishEventsForBackgroundURLSession.
        BackgroundSessionRegistry.shared.registerCompletionHandler(
            sessionId: identifier,
            handler: MainThreadCompletionCallback(completionHandler)
        )
    }
}

/// Wraps an iOS background-session completion handler so it can be stored in
/// the KMP BackgroundSessionRegistry and invoked from the session delegate.
private class MainThreadCompletionCallback: BackgroundDownloadCompletionCallback {
    private let handler: () -> Void
    init(_ handler: @escaping () -> Void) { self.handler = handler }
    func call() { DispatchQueue.main.async { self.handler() } }
}
