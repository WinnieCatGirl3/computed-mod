package dev.propulsionteam.computed.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.propulsionteam.computed.content.items.PortableLinkData;
import org.junit.jupiter.api.Test;

class GlassesHudSettingsTest {
    @Test
    void migratesLegacySingleLineConfig() {
        JsonObject json = JsonParser
                .parseString("{\"x\":0.25,\"y\":0.8,\"scale\":1.5}")
                .getAsJsonObject();

        GlassesHudSettings settings = GlassesHudSettings.fromJson(json);

        assertEquals(1, settings.elements.size());
        GlassesHudSettings.HudElement element = settings.elements.get(0);
        assertEquals(0.25f, element.x, 1e-6f);
        assertEquals(0.8f, element.y, 1e-6f);
        assertEquals(1.5f, element.scale, 1e-6f);
    }

    @Test
    void legacyConfigValuesAreClamped() {
        JsonObject json = JsonParser
                .parseString("{\"x\":5,\"y\":-4,\"scale\":9}")
                .getAsJsonObject();

        GlassesHudSettings.HudElement element =
                GlassesHudSettings.fromJson(json).elements.get(0);

        assertEquals(1f, element.x, 1e-6f);
        assertEquals(0f, element.y, 1e-6f);
        assertEquals(2f, element.scale, 1e-6f);
    }

    @Test
    void legacyDefaultYSurvivesMigration() {
        JsonObject json = JsonParser
                .parseString("{\"x\":0.5,\"y\":-1,\"scale\":1}")
                .getAsJsonObject();

        GlassesHudSettings.HudElement element =
                GlassesHudSettings.fromJson(json).elements.get(0);

        assertEquals(GlassesHudSettings.DEFAULT_Y, element.y, 1e-6f);
        assertEquals(240f - 32f, element.resolvedY(240), 1e-6f);
    }

    @Test
    void readsElementList() {
        JsonObject json = JsonParser.parseString(
                        "{\"elements\":["
                                + "{\"text\":\"hi {computer}\",\"x\":0.1,\"y\":0.2,\"scale\":0.7},"
                                + "{\"text\":\"\",\"x\":0.9,\"y\":-1,\"scale\":2.5}]}")
                .getAsJsonObject();

        GlassesHudSettings settings = GlassesHudSettings.fromJson(json);

        assertEquals(2, settings.elements.size());
        assertEquals("hi {computer}", settings.elements.get(0).text);
        assertEquals(2f, settings.elements.get(1).scale, 1e-6f);
    }

    @Test
    void widgetRegionKeepsDefaultsWhenAbsent() {
        JsonObject json = JsonParser.parseString("{\"elements\":[]}").getAsJsonObject();

        GlassesHudSettings settings = GlassesHudSettings.fromJson(json);

        assertEquals(0.12f, settings.widgetsX, 1e-6f);
        assertEquals(0.1f, settings.widgetsY, 1e-6f);
        assertEquals(1f, settings.widgetsScale, 1e-6f);
    }

    @Test
    void widgetRegionIsReadAndClamped() {
        JsonObject json = JsonParser.parseString(
                        "{\"elements\":[],\"widgetsX\":7,\"widgetsY\":-2,\"widgetsScale\":9}")
                .getAsJsonObject();

        GlassesHudSettings settings = GlassesHudSettings.fromJson(json);

        assertEquals(1f, settings.widgetsX, 1e-6f);
        assertEquals(0f, settings.widgetsY, 1e-6f);
        assertEquals(2f, settings.widgetsScale, 1e-6f);
    }

    @Test
    void resolvesPlaceholders() {
        PortableLinkData link = new PortableLinkData(2, -60, 0, "minecraft:overworld", "Test", 0);

        assertEquals(
                "Test at 2 -60 0 in minecraft:overworld",
                GlassesHudSettings.resolveTemplate(
                        "{computer} at {x} {y} {z} in {dimension}", link));
    }

    @Test
    void missingLinkLeavesPlaceholdersUnchanged() {
        assertEquals(
                "{computer} {x}",
                GlassesHudSettings.resolveTemplate("{computer} {x}", null));
    }
}
