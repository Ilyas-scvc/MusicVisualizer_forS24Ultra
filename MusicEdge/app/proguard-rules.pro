# The app has no reflection-based frameworks, so R8 needs almost no help.

# Entry points instantiated by the framework by name.
-keep class com.musicedge.visualizer.media.MediaEdgeListenerService { *; }
-keep class com.musicedge.visualizer.system.BootReceiver { *; }

# Strip verbose logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
