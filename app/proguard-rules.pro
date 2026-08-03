# Room
-keep class androidx.room.** { *; }

# ML Kit text recognition
-keep class com.google.mlkit.vision.text.** { *; }

# OpenCSV (uses reflection for bean mapping in some paths)
-keep class com.opencsv.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep data/entity classes intact (Room + serialization reflect on these)
-keep class com.receiptintel.scanner.data.local.entity.** { *; }
-keep class com.receiptintel.scanner.parser.** { *; }
