-dontwarn kotlinx.parcelize.Parcelize

-keep class io.github.supermonster003.autojs6.plugin.nodejs.NodeJsPluginInfoService { *; }
-keep class io.github.supermonster003.autojs6.plugin.nodejs.NodeJsPluginInfoService$* { *; }
-keep class io.github.supermonster003.autojs6.plugin.nodejs.NodeJsRuntimePluginService { *; }
-keep class io.github.supermonster003.autojs6.plugin.nodejs.NodeJsRuntimePluginService$* { *; }
-keep class org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge { *; }
-keep class org.autojs.plugin.common.api.** { *; }
-keep class org.autojs.plugin.nodejs.api.** { *; }

# JNI calls the sink through these method names, including in minified builds.
-keep interface org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge$BridgeSink { *; }
-keep interface org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge$OutputSink { *; }
-keepclassmembers class * implements org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge$OutputSink {
    public void onStdout(byte[]);
    public void onStderr(byte[]);
    public void onStdinState(byte[]);
}
-keepclassmembers class * implements org.autojs.autojs.engine.NativeNodeEmbeddedRuntimeBridge$BridgeSink {
    public boolean post(long, byte[]);
    public void useFileTransport();
}
