-keep class com.adnan.memora.data.** { *; }
# TDLib calls these classes by name from native code: they must not be renamed or removed.
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**
