# Slovy Movy is open source, so release builds should optimize and shrink code
# without obfuscating class, method, or field names. This keeps stack traces
# readable by humans while still letting R8 remove unused code and apply
# optimizations.
-dontobfuscate

# Keep source/line metadata for Crashlytics and retrace after R8 optimizations
# such as inlining. Keep the Android/Kotlin metadata attributes that libraries
# commonly rely on so this project file does not narrow the default rules.
-keepattributes SourceFile,LineNumberTable,SourceDebugExtension,*Annotation*,Signature,InnerClasses,EnclosingMethod
