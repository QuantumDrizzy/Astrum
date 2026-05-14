# Astrum – ProGuard rules for Play Store release

# ── Keep all app classes ──────────────────────────────────────────────────
-keep class com.astrum.app.** { *; }

# ── Custom views referenced by class name in XML layouts ─────────────────
-keep class com.astrum.app.views.StarFieldView  { *; }
-keep class com.astrum.app.views.MoonView       { *; }
-keep class com.astrum.app.views.AzimuthView    { *; }

# ── Kotlin data classes used by the astronomy engine ─────────────────────
-keepclassmembers class com.astrum.app.astro.** {
    public <methods>;
    public <fields>;
}

# ── Navigation component ──────────────────────────────────────────────────
-keepnames class androidx.navigation.fragment.NavHostFragment

# ── ViewBinding (generated classes) ──────────────────────────────────────
-keep class com.astrum.app.databinding.** { *; }

# ── Material Components ───────────────────────────────────────────────────
-keep class com.google.android.material.** { *; }

# ── Kotlin metadata (reflection safety) ──────────────────────────────────
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ── JavaScript interface (future-proofing) ───────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── Remove debug logging in release ──────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
