# Rules needed only for running Android instrumentation against a minified
# target app. AndroidJUnitRunner and androidx.test.platform execute from the
# test APK, but some shared runtime classes are loaded from the target app APK.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.io.** { *; }

# These smoke tests intentionally call app data-layer APIs from the test APK.
# Keep those tested app entrypoints and models in the minified target APK so R8
# does not remove members that production code does not directly reference.
-keep class com.slovy.slovymovyapp.data.Language { *; }
-keep class com.slovy.slovymovyapp.data.remote.** { *; }
-keep class com.slovy.slovymovyapp.data.local.** { *; }
-keep class com.slovy.slovymovyapp.data.settings.** { *; }
-keep class com.slovy.slovymovyapp.data.favorites.** { *; }
