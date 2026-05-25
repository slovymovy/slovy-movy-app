import SwiftUI
import ComposeApp
import FirebaseCore
import FirebaseAnalytics
import FirebaseAppCheck
import FirebaseCrashlytics
import FirebasePerformance

private class SlovyAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        if #available(iOS 14.0, *) {
            return AppAttestProvider(app: app)
        }
        return DeviceCheckProvider(app: app)
    }
}

private class FirebaseAnalyticsLogger: AnalyticsLogger {
    func logEvent(name: String, params: [String: Any]) {
        FirebaseAnalytics.Analytics.logEvent(name, parameters: params)
    }

    func setUserProperty(name: String, value: String?) {
        FirebaseAnalytics.Analytics.setUserProperty(value, forName: name)
    }
}


private class FirebasePerformanceTrace: PerformanceTrace {
    private let trace: Trace

    init(trace: Trace) {
        self.trace = trace
    }

    func putAttribute(name: String, value: String) {
        trace.setValue(value, forAttribute: name)
    }

    func putMetric(name: String, value: Int64) {
        trace.setValue(value, forMetric: name)
    }

    func incrementMetric(name: String, by: Int64) {
        trace.incrementMetric(name, by: by)
    }

    func stop() {
        trace.stop()
    }
}

private class FirebasePerformanceMonitor: PerformanceMonitor {
    func startTrace(name: String) -> PerformanceTrace {
        guard let trace = Performance.startTrace(name: name) else {
            return NoOpPerformanceTrace.shared
        }
        return FirebasePerformanceTrace(trace: trace)
    }
}

private class FirebaseCrashlyticsAppLogSink: AppLogSink {
    func log(level: AppLogLevel, tag: String, message: String, throwable: KotlinThrowable?) {
        let crashlytics = Crashlytics.crashlytics()
        let levelName = level.name
        crashlytics.log("\(levelName)/\(tag): \(message)")

        guard let throwable else {
            return
        }

        let error = NSError(
            domain: "com.slovy.slovymovyapp.applogger",
            code: levelName == "ERROR" ? 2 : 1,
            userInfo: [
                NSLocalizedDescriptionKey: message,
                "level": levelName,
                "tag": tag,
                "throwable": String(describing: throwable)
            ]
        )
        crashlytics.record(error: error)
    }
}

@main
struct iOSApp: App {
    init() {
        #if DEBUG
        AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
        #else
        AppCheck.setAppCheckProviderFactory(SlovyAppCheckProviderFactory())
        #endif
        FirebaseApp.configure()
        Analytics.shared.logger = FirebaseAnalyticsLogger()
        PerformanceMonitoring.shared.monitor = FirebasePerformanceMonitor()
        AppLogger.shared.remoteLogger = FirebaseCrashlyticsAppLogSink()
    }

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// Background-download relaunch handling.
///
/// When iOS terminates the app while a background URLSession download is
/// still in flight and later relaunches us specifically to deliver pending
/// events, it calls `application(_:handleEventsForBackgroundURLSession:completionHandler:)`.
///
/// The KMP download path in `IosDownloadHelper.kt` cannot resume into the
/// in-process Kotlin coroutine that originally started the download — that
/// continuation died with the previous app process and we do not persist
/// destination paths anywhere. So even if we recreated the session and the
/// delegate received the temp file, we would have nowhere to move it.
///
/// To at least be honest with iOS (so it stops keeping us awake / suspended
/// waiting on us to finish), we recreate a URLSession with the same
/// identifier and attach a minimal cleanup delegate. iOS delivers any
/// pending events to that delegate, the temp file is allowed to be cleaned
/// up by the system, and we call the supplied completion handler from
/// `urlSessionDidFinishEvents(forBackgroundURLSession:)`. The user must
/// re-trigger the download from inside the app to complete it.
class AppDelegate: NSObject, UIApplicationDelegate {
    private var cleanupSessions: [String: URLSession] = [:]

    func application(
        _ application: UIApplication,
        handleEventsForBackgroundURLSession identifier: String,
        completionHandler: @escaping () -> Void
    ) {
        let config = URLSessionConfiguration.background(withIdentifier: identifier)
        let delegate = BackgroundCleanupDelegate(identifier: identifier, completionHandler: completionHandler) { [weak self] id in
            self?.cleanupSessions.removeValue(forKey: id)
        }
        let session = URLSession(configuration: config, delegate: delegate, delegateQueue: nil)
        cleanupSessions[identifier] = session
    }
}

/// Minimal `URLSessionDownloadDelegate` used only to drain a recreated
/// background session after app relaunch (see `AppDelegate`). It intentionally
/// does not move the downloaded temp file anywhere — there is no persisted
/// destination to move it to.
private class BackgroundCleanupDelegate: NSObject, URLSessionDownloadDelegate {
    private let identifier: String
    private let completionHandler: () -> Void
    private let onFinished: (String) -> Void

    init(identifier: String, completionHandler: @escaping () -> Void, onFinished: @escaping (String) -> Void) {
        self.identifier = identifier
        self.completionHandler = completionHandler
        self.onFinished = onFinished
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        // No-op: we have no persisted destination, so we let iOS reclaim the
        // temp file when this delegate method returns.
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        // No-op: nothing to recover.
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        DispatchQueue.main.async {
            self.completionHandler()
            session.invalidateAndCancel()
            self.onFinished(self.identifier)
        }
    }
}
