# Historical journal snapshots and durable editor drafts use Gson reflection.
# Preserve field names and constructors across updates and exported schemas.
-keep class com.khaleel.newleaf.data.** { *; }
-keep class com.khaleel.newleaf.ui.EditorDraft { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
