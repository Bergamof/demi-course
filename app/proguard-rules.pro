# R8 rules for the release build (minification and resource shrinking are on).
#
# The persisted session is JSON written by kotlinx.serialization from the classes of the `domain`
# module (StepSpec, AppSettings, SeanceData and their enums). Their generated serializers are looked
# up reflectively through `Companion.serializer()`, so they must survive shrinking, and the enum
# constant names are the JSON values already stored on users' phones: they must not be renamed.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keep,includedescriptorclasses class com.demicourse.domain.**$$serializer { *; }
-keepclassmembers class com.demicourse.domain.** {
    *** Companion;
}
-keepclasseswithmembers class com.demicourse.domain.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers enum com.demicourse.domain.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
