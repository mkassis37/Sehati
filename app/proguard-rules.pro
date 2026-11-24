# Room and SQLCipher use supported Android APIs; keep application models stable.
-keep class com.mkassis37.sehati.data.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# Keep JNI entry points and PDFBox reflection metadata in minified release builds.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
