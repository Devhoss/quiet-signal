# Quiet Signal - release (R8) configuration
#
# Goals:
#   1. No verbose diagnostic logging in release builds.
#   2. Never strip the manifest-declared widget receivers / providers, the
#      app widget RemoteViews layouts, or the settings/state singletons that
#      widgets reach through reflection-free but entry-point-style access.
#
# Verbose QuietSignal/* tracing is debug-only. The release build removes
# Log.d / Log.v call sites entirely (they have no side effects; greppable proof:
# no call site consumes the int return value). Log.e is preserved so real
# failures still reach logcat/crash reports.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# Entry points the Android framework instantiates by name (launcher, AppWidget
# host). AGP already keeps manifest components, this is deliberate defence in
# depth so a future manifest refactor cannot silently break the widgets.
-keep class com.quiet.signal.widget.TailscaleWidgetProvider { *; }
-keep class com.quiet.signal.widget.TailscaleCompactWidgetProvider { *; }
-keep class com.quiet.signal.MainActivity { *; }

# Widget providers are constructed by the framework and call these objects.
-keep class com.quiet.signal.StateRepository { *; }
-keep class com.quiet.signal.SettingsRepository { *; }
-keep class com.quiet.signal.TailscaleIntegration { *; }
-keep class com.quiet.signal.TailscaleSnapshot { *; }

# Widget layouts are inflated by the launcher process from the published
# RemoteViews; keep the attribute names R8 can rename.
-keepattributes *Annotation*
