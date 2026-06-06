# JNI: keep native method names and their declaring classes so the Rust library's
# Java_native_PayloadExtractNative_* symbols still resolve after ProGuard shrinking.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
