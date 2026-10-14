-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class app.saathi.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
