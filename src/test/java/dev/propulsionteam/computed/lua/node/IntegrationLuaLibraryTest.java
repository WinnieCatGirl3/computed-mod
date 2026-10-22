package dev.propulsionteam.computed.lua.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.propulsionteam.computed.lua.compiler.LuaSourceCompiler;
import dev.propulsionteam.computed.lua.sandbox.LuaSandbox;
import org.junit.jupiter.api.Test;

class IntegrationLuaLibraryTest {
    @Test
    void everyIntegrationDefinitionCompilesAndMatchesItsId() {
        LuaSourceCompiler compiler = new LuaSourceCompiler();
        LuaDefinitionLoader loader = new LuaDefinitionLoader();
        LuaSandbox sandbox = new LuaSandbox();
        var definitions = IntegrationLuaLibrary.load();

        assertEquals(22, definitions.size());
        definitions.forEach((id, source) -> {
            assertTrue(!source.source().contains("\r"), id + " contains a carriage return");
            LuaNodeDefinition definition =
                    loader.load(compiler.compile(source.apiVersion(), source.source()), sandbox);
            assertEquals(id, definition.id());
            assertEquals(dev.propulsionteam.computed.graph.LuaDefinitionSource.Origin.INTEGRATION, source.origin());
        });
        assertTrue(definitions.keySet().stream().anyMatch(id -> id.startsWith("computed:cc_")));
        assertTrue(definitions.keySet().stream().anyMatch(id -> id.startsWith("computed:create_")));
        assertTrue(definitions.keySet().stream().anyMatch(id -> id.startsWith("computed:aeronautics_")));
    }
}
