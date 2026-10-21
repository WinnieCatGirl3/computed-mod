package dev.propulsionteam.computed.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import dev.propulsionteam.computed.content.items.PortableLinkData;

/**
 * Client-side placement of the glasses HUD elements, persisted as JSON in the config directory.
 * Every element is a text template with its own normalized position (0..1, so it survives
 * resolution and GUI-scale changes) and its own scale. The graph widget region (widgets wired
 * into the Glasses HUD node) carries its own normalized anchor and scale the same way. A y of {@link #DEFAULT_Y} means
 * "above the hotbar" and is resolved against the current GUI height at render time.
 */
public final class GlassesHudSettings {
    /** Sentinel for "just above the hotbar", resolved per frame against the GUI height. */
    public static final float DEFAULT_Y = -1f;

    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "computed-glasses-hud.json";

    private static GlassesHudSettings instance;

    /** HUD elements; an empty list turns the glasses HUD off. */
    public List<HudElement> elements = new ArrayList<>();
    /** Normalized horizontal center of the graph widget region (the Glasses HUD node's widgets). */
    public float widgetsX = 0.12f;
    /** Normalized vertical top of the graph widget region. */
    public float widgetsY = 0.1f;
    /** Scale of the graph widget region, clamped like element scales. */
    public float widgetsScale = 1f;

    private GlassesHudSettings() {}

    /**
     * One HUD line: an editable text template, a normalized position and an individual scale.
     * An empty template falls back to the localized "Connected with: {computer}" default.
     */
    public static final class HudElement {
        /** Template; see {@link #resolveTemplate(String, PortableLinkData)} for placeholders. */
        public String text = "";
        /** Normalized horizontal center. */
        public float x = 0.5f;
        /** Normalized vertical top, or {@link #DEFAULT_Y}. */
        public float y = DEFAULT_Y;
        /** Text scale multiplier for this element. */
        public float scale = 1.0f;
        /** Text color as {@code 0xRRGGBB}; {@code null} means plain white. */
        public Integer color;
        /** Visibility toggle like G&amp;G's overlay boxes; hidden elements render nowhere. */
        public boolean visible = true;

        public HudElement copy() {
            HudElement copy = new HudElement();
            copy.text = text;
            copy.x = x;
            copy.y = y;
            copy.scale = scale;
            copy.color = color;
            copy.visible = visible;
            return copy;
        }

        /** The element's text color, defaulting to white when unset. */
        public int textColor() {
            return color == null ? 0xFFFFFF : color;
        }

        /** Resolved vertical position in GUI pixels for the given GUI height. */
        public float resolvedY(int guiHeight) {
            return y == DEFAULT_Y ? guiHeight - 32f : y * guiHeight;
        }

        /** Resolved horizontal center in GUI pixels. */
        public float resolvedX(int guiWidth) {
            return x * guiWidth;
        }

        /** Clamps every field back into its valid range. */
        public void clamp() {
            x = Math.clamp(x, 0f, 1f);
            if (y != DEFAULT_Y) {
                y = Math.clamp(y, 0f, 1f);
            }
            scale = Math.clamp(scale, 0.5f, 2f);
            if (text == null) {
                text = "";
            }
            if (color != null) {
                color = color & 0xFFFFFF;
            }
        }
    }

    public static GlassesHudSettings get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** The localized default line, used when an element's template is empty. */
    public static String defaultText() {
        return Component.translatable("computed.glasses.connected", "{computer}").getString();
    }

    /**
     * Substitutes the placeholders of a template: {@code {computer}} (the link's display name),
     * {@code {x}}, {@code {y}}, {@code {z}} and {@code {dimension}}. An empty template renders the
     * localized default line instead.
     */
    public static String resolveTemplate(String template, PortableLinkData link) {
        String text = template == null || template.isEmpty() ? defaultText() : template;
        if (link == null) {
            return text;
        }
        return text.replace("{computer}", link.displayName())
                .replace("{x}", Integer.toString(link.x()))
                .replace("{y}", Integer.toString(link.y()))
                .replace("{z}", Integer.toString(link.z()))
                .replace("{dimension}", link.dimension());
    }

    /** Restores the single default line. */
    public void reset() {
        elements = new ArrayList<>();
        elements.add(new HudElement());
    }

    public void save() {
        try {
            Files.writeString(configFile(), GSON.toJson(this));
        } catch (Exception exception) {
            dev.propulsionteam.computed.Computed.LOGGER.warn(
                    "Could not save glasses HUD settings", exception);
        }
    }

    private static GlassesHudSettings load() {
        try {
            Path file = configFile();
            if (Files.isRegularFile(file)) {
                JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (json != null) {
                    return fromJson(json);
                }
            }
        } catch (Exception exception) {
            dev.propulsionteam.computed.Computed.LOGGER.warn(
                    "Could not load glasses HUD settings, using defaults", exception);
        }
        GlassesHudSettings defaults = new GlassesHudSettings();
        defaults.elements.add(new HudElement());
        return defaults;
    }

    /** Parses a config object, migrating the pre-element single-line format when needed. */
    static GlassesHudSettings fromJson(JsonObject json) {
        GlassesHudSettings settings = new GlassesHudSettings();
        if (json.has("elements") && json.get("elements").isJsonArray()) {
            HudElement[] parsed = GSON.fromJson(json.get("elements"), HudElement[].class);
            if (parsed != null) {
                for (HudElement element : parsed) {
                    if (element != null) {
                        element.clamp();
                        settings.elements.add(element);
                    }
                }
            }
        } else {
            // Legacy single-line file: carry its placement over into one default element.
            HudElement element = new HudElement();
            element.x = json.has("x") ? json.get("x").getAsFloat() : 0.5f;
            element.y = json.has("y") ? json.get("y").getAsFloat() : DEFAULT_Y;
            element.scale = json.has("scale") ? json.get("scale").getAsFloat() : 1.0f;
            element.clamp();
            settings.elements.add(element);
        }
        settings.widgetsX = json.has("widgetsX") ? json.get("widgetsX").getAsFloat() : settings.widgetsX;
        settings.widgetsY = json.has("widgetsY") ? json.get("widgetsY").getAsFloat() : settings.widgetsY;
        settings.widgetsScale = json.has("widgetsScale") ? json.get("widgetsScale").getAsFloat() : settings.widgetsScale;
        settings.clampWidgetPlacement();
        return settings;
    }

    /** Clamps the graph widget region's anchor and scale back into their valid ranges. */
    void clampWidgetPlacement() {
        widgetsX = Math.clamp(widgetsX, 0f, 1f);
        widgetsY = Math.clamp(widgetsY, 0f, 1f);
        widgetsScale = Math.clamp(widgetsScale, 0.5f, 2f);
    }

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }
}
