# Keep all model classes (used by Gson/Retrofit)
-keep class com.muort.upworker.core.model.** { *; }

# Keep Retrofit interfaces
-keep interface com.muort.upworker.core.network.** { *; }

# Keep Room database classes
-keep class com.muort.upworker.core.database.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Gson - 泛型签名、内部类、注解必须保留，否则 TypeToken 会 ClassCastException
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepattributes SourceFile, LineNumberTable
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# Gson TypeToken 子类（匿名类）需要保留泛型父类信息
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Retrofit - 官方完整规则（R8 full mode 必需）
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**
# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit
# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
# With R8 full mode, keep interfaces with Retrofit annotations
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation,allowshrinking interface <1>
# Keep Retrofit Response/Call generic info
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking interface retrofit2.Call

# OkHttp 5.x
# OkHttp 自带 consumer proguard rules，大部分规则由库自动提供
# 以下是补充规则：
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn okhttp3.internal.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
}

# Keep Fragment and Activity constructors
-keepclassmembers class * extends androidx.fragment.app.Fragment {
    public <init>();
}
-keepclassmembers class * extends androidx.appcompat.app.AppCompatActivity {
    public <init>();
}

# Sardine WebDAV
-keep class com.thegrizzlylabs.sardineandroid.** { *; }
-dontwarn com.thegrizzlylabs.sardineandroid.**

# AWS SDK for S3
-keep class com.amazonaws.** { *; }
-dontwarn com.amazonaws.**
-dontwarn org.apache.commons.**
-dontwarn org.joda.time.**

# Keep serialized names
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep all data class members (Gson reflection on fields)
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep all fields in model/repository/util data classes
-keep class com.muort.upworker.core.model.** { *; }
-keep class com.muort.upworker.core.repository.** { *; }
-keep class com.muort.upworker.core.util.** { *; }

# Retrofit - keep generic signatures for Call<T>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Release 版本移除非错误级别日志（保留 Log.e/Log.wtf 用于线上错误排查）
# 仅在使用 proguard-android-optimize.txt 时生效
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Release 版本移除非错误级别 Timber 日志调用
# 注意：不能用 assumenosideeffects 删除 Timber.d/i/w，否则应用内日志（InAppLogTree）
# 在 release 包中将收不到这些级别的日志，无法用于问题反馈。
# 改为依靠 ReleaseTree.isLoggable() 过滤 logcat 输出（仅 ERROR/WTF 输出到 logcat），
# InAppLogTree 则在日志开关开启时捕获全部级别写入应用内日志。
# 仅删除从未使用的 v 级别。
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
}
