package io.github.supermonster003.autojs6.plugin.nodejs;

import org.autojs.plugin.common.api.PluginActions;
import org.autojs.plugin.nodejs.api.NodeJsPluginActions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NodeJsPluginActionsTest {

    @Test
    public void frozenInfoActionMatchesTheGenericPluginContract() {
        assertEquals(PluginActions.INFO, NodeJsPluginActions.INFO);
    }
}
