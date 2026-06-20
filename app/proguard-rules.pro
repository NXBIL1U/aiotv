-keep class com.itrepos.aiotv.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# --- kotlinx.serialization ---
# Without these, R8 strips/renames the generated $$serializer classes and the
# Companion.serializer() methods, breaking all JSON parsing in release builds.
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.itrepos.aiotv.**$$serializer { *; }
-keepclassmembers class com.itrepos.aiotv.** {
    *** Companion;
}
-keepclasseswithmembers class com.itrepos.aiotv.** {
    kotlinx.serialization.KSerializer serializer(...);
}
