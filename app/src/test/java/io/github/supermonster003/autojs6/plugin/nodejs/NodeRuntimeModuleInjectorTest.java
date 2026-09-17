package io.github.supermonster003.autojs6.plugin.nodejs;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NodeRuntimeModuleInjectorTest {

    @Test
    public void explicitRequestModeOpensDirectInteractiveCheckpointPolicy() {
        NodeRuntimeModuleInjector.LifecycleRequestPolicy policy =
                NodeRuntimeModuleInjector.resolveLifecycleRequest(
                        "interactive_long_running",
                        null,
                        null
                );

        assertEquals("interactive_long_running", policy.executionMode);
        assertEquals("interactive_session", policy.launchSurface);
        assertTrue(policy.checkpointEnabled);
    }

    @Test
    public void explicitRequestModeOverridesConflictingEngineInfo() {
        NodeRuntimeModuleInjector.LifecycleRequestPolicy policy =
                NodeRuntimeModuleInjector.resolveLifecycleRequest(
                        "one_shot",
                        "interactive_long_running",
                        "interactive_session"
                );

        assertEquals("one_shot", policy.executionMode);
        assertFalse(policy.checkpointEnabled);
    }

    @Test
    public void missingRequestKeyRetainsEngineInfoCompatibilityFallback() {
        NodeRuntimeModuleInjector.LifecycleRequestPolicy policy =
                NodeRuntimeModuleInjector.resolveLifecycleRequest(
                        null,
                        "interactive_long_running",
                        "interactive_session"
                );

        assertEquals("interactive_long_running", policy.executionMode);
        assertEquals("interactive_session", policy.launchSurface);
        assertTrue(policy.checkpointEnabled);
    }

    @Test
    public void retiredPackagedLongRunningSurfaceNoLongerOpensCheckpoint() {
        NodeRuntimeModuleInjector.LifecycleRequestPolicy policy =
                NodeRuntimeModuleInjector.resolveLifecycleRequest(
                        "interactive_long_running",
                        "interactive_long_running",
                        "packaged_long_running"
                );

        assertEquals("interactive_long_running", policy.executionMode);
        assertEquals("packaged_long_running", policy.launchSurface);
        assertFalse(policy.checkpointEnabled);
    }

    @Test
    public void incompatibleLaunchSurfaceKeepsCheckpointClosed() {
        NodeRuntimeModuleInjector.LifecycleRequestPolicy policy =
                NodeRuntimeModuleInjector.resolveLifecycleRequest(
                        "interactive_long_running",
                        "interactive_long_running",
                        "script"
                );

        assertFalse(policy.checkpointEnabled);
    }

    /** Roadmap M18.2: the scheduled mode infers the scheduler surface and never opens checkpoints. */
    @Test
    public void scheduledRequestResolvesSchedulerSurfaceWithoutCheckpoint() {
        NodeRuntimeModuleInjector.LifecycleRequestPolicy policy =
                NodeRuntimeModuleInjector.resolveLifecycleRequest("scheduled", null, null);

        assertEquals("scheduled", policy.executionMode);
        assertEquals("scheduled_runner", policy.launchSurface);
        assertFalse(policy.checkpointEnabled);
    }

    @Test
    public void scheduledEngineInfoFallbackKeepsSchedulerSurface() {
        NodeRuntimeModuleInjector.LifecycleRequestPolicy policy =
                NodeRuntimeModuleInjector.resolveLifecycleRequest(null, "scheduled", "scheduled_runner");

        assertEquals("scheduled", policy.executionMode);
        assertEquals("scheduled_runner", policy.launchSurface);
        assertFalse(policy.checkpointEnabled);
    }
}
