# Room and SQLCipher use supported Android APIs; keep application models stable.
-keep class com.mkassis37.sehati.data.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# Keep JNI entry points and PDFBox reflection metadata in minified release builds.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }

# PDFBox JPX support is optional on Android. The bundled PDFBox Android
# library references Gemalto JP2 classes only when decoding/encoding JPEG 2000
# images; Sehati does not bundle that optional codec.
-dontwarn com.gemalto.jp2.**
