# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keep class com.wqry085.deployesystem.** { *; }
# Jsoup 规则
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-keepattributes Signature,InnerClasses

# javax.annotation 规则
-dontwarn javax.annotation.**
-keep class javax.annotation.** { *; }

# 或者直接忽略所有缺失类的警告
-ignorewarnings
# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile