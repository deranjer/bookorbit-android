# kotlinx.serialization — keep @Serializable metadata and generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.bookorbit.core.model.** {
    *** Companion;
}
-keep,includedescriptorclasses class com.bookorbit.core.model.**$$serializer { *; }

# Retrofit / OkHttp
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Media3 / ExoPlayer keeps its own consumer rules; nothing extra required here.
