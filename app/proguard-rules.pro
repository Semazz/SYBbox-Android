# The gomobile binding is reached from native code by name, so nothing under it may be
# renamed or stripped. `Platform` in particular is implemented in Kotlin and invoked from Go.
-keep class com.sybbox.libcore.** { *; }
-keep class go.** { *; }
-keep class com.sybbox.core.SingBoxPlatform { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Room reads these reflectively.
-keep class com.sybbox.data.db.entity.** { *; }

# Gson maps JSON onto these by field name, so the names have to survive.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class com.sybbox.domain.model.** { *; }
-keep class com.sybbox.ui.settings.SettingsState { *; }
-keepclassmembers class com.sybbox.core.SingBoxPlatform$* { <fields>; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp ships references to optional platform pieces that are not present at runtime.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# SnakeYAML resolves constructors reflectively when parsing Clash subscriptions.
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn java.beans.**
