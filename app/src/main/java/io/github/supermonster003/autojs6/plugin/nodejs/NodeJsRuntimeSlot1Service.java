package io.github.supermonster003.autojs6.plugin.nodejs;

/** Manifest-only worker endpoint; callers bind the public dispatcher. */
public final class NodeJsRuntimeSlot1Service extends NodeJsRuntimePluginService {
    @Override int runtimeSlotId() { return 1; }
}
