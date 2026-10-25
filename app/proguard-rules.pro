# MouthBand release rules (R8 full mode). Libraries that ship their own consumer rules
# (Compose, Room, DataStore, kotlinx.serialization, Navigation, Google Mobile Ads, UMP) need
# nothing here.

# ONNX Runtime (SwiftF0 pitch model): the native library creates and reads these Java classes,
# fields and methods by name through JNI, so R8 must neither rename nor remove them.
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { native <methods>; }
-dontwarn ai.onnxruntime.**

# Keep source file names and line numbers for readable crash reports; rename the file attribute.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
