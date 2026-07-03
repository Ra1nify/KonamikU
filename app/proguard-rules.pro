-keep class org.cf0x.konamiku.xposed.KonamikuModule { *; }
-keep class io.github.libxposed.api.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class org.cf0x.konamiku.data.NfcCard { *; }

# HiddenApiBypass — used at runtime for NFC hidden API fallback
-keep class org.lsposed.hiddenapibypass.** { *; }

# Our wrappers that reflectively access hidden system APIs
-keep class org.cf0x.konamiku.system.HiddenApiNfcChecker { *; }
-keep class org.cf0x.konamiku.util.SystemPropertyHelper { *; }