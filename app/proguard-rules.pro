# Compose / Kotlin 元数据
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# kotlinx.serialization
-keepclassmembers class com.planlist.app.data.backup.** {
    *** Companion;
}
-keepclasseswithmembers class com.planlist.app.data.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.planlist.app.**$$serializer { *; }
-keepclassmembers class com.planlist.app.** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

-keep class com.planlist.app.reminder.** { *; }
