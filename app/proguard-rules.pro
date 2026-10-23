# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Jejak tumpukan yang bisa dibaca saat dilaporkan pemain (nama berkas dan nomor baris tetap ada).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Berkas simpanan, log aksi dan kode tantangan memuat nama enum (Difficulty, SalesScheme, FactoryScheme, TransferPricingMode, Role, ...)
# lewat name()/valueOf(). Nama itu adalah nilai string, bukan nama kelas, jadi aman diobfuskasi; aturan ini hanya menjaga
# valueOf/values bila aturan bawaan berubah.
-keepclassmembers enum com.example.engine.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

