# Historical journal snapshots and durable editor drafts use Gson reflection.
# Preserve field names and constructors across updates and exported schemas.
-keep class com.khaleel.newleaf.data.** { *; }
-keep class com.khaleel.newleaf.ui.EditorDraft { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
# The Android test runner calls this shared runtime library directly.
-keep class androidx.tracing.Trace { *; }
# Preserve the Kotlin runtime ABI shared with the mapped release test APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
# Compose instrumentation implements shared runtime interfaces, including
# default-method bridge classes that the application alone does not use.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
