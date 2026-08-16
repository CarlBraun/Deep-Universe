# The save file is kotlinx.serialization JSON, so the generated serializers and the enum names
# they write must survive shrinking — a renamed enum constant would silently invalidate every
# existing player's save.
-keepclassmembers class com.deepuniverse.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.deepuniverse.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class kotlin.Metadata

# ML Kit loads its models and native entry points reflectively.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
