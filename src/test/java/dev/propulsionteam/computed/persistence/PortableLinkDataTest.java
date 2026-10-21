package dev.propulsionteam.computed.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.propulsionteam.computed.content.items.PortableLinkData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PortableLinkDataTest {
    @Test
    void toTagOfRoundTrips() {
        PortableLinkData link = new PortableLinkData(12, 64, -8, "minecraft:the_nether");
        PortableLinkData back = PortableLinkData.of(link.toTag());
        assertEquals(link, back);
        assertEquals(new BlockPos(12, 64, -8), back.blockPos());
        assertEquals("minecraft:the_nether", back.dimension());
    }

    @Test
    void toTagCarriesCoordinatesAndDimension() {
        CompoundTag tag = new PortableLinkData(-3, 0, 7, "minecraft:overworld").toTag();
        assertEquals(-3, tag.getInt("x"));
        assertEquals(0, tag.getInt("y"));
        assertEquals(7, tag.getInt("z"));
        assertEquals("minecraft:overworld", tag.getString("dimension"));
    }

    @Test
    void ofRejectsMissingDimension() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", 1);
        tag.putInt("y", 2);
        tag.putInt("z", 3);
        assertThrows(IllegalArgumentException.class, () -> PortableLinkData.of(tag));
    }

    @Test
    void ofRejectsBlankDimension() {
        CompoundTag tag = new PortableLinkData(0, 0, 0, "minecraft:overworld").toTag();
        tag.putString("dimension", "   ");
        assertThrows(IllegalArgumentException.class, () -> PortableLinkData.of(tag));
        tag.putString("dimension", "");
        assertThrows(IllegalArgumentException.class, () -> PortableLinkData.of(tag));
    }

    @Test
    void differentPositionsAreNotEqual() {
        assertNotEquals(
                new PortableLinkData(1, 2, 3, "minecraft:overworld"),
                new PortableLinkData(1, 2, 4, "minecraft:overworld"));
    }

    @Test
    void legacyTagWithoutNameOrIdLoads() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", 4);
        tag.putInt("y", 5);
        tag.putInt("z", 6);
        tag.putString("dimension", "minecraft:overworld");

        PortableLinkData link = PortableLinkData.of(tag);
        assertEquals("", link.name());
        assertEquals(-1, link.computerId());
        assertEquals(link, new PortableLinkData(4, 5, 6, "minecraft:overworld"));
    }

    @Test
    void toTagCarriesNameAndIdWhenPresent() {
        PortableLinkData link = new PortableLinkData(
                1, 2, 3, "minecraft:overworld", "My Computer", 42);
        PortableLinkData back = PortableLinkData.of(link.toTag());
        assertEquals("My Computer", back.name());
        assertEquals(42, back.computerId());
        assertEquals(link, back);
    }

    @Test
    void toTagOmitsDefaultNameAndId() {
        CompoundTag tag = new PortableLinkData(0, 0, 0, "minecraft:overworld").toTag();
        assertFalse(tag.contains("name"));
        assertFalse(tag.contains("computerId"));
    }

    @Test
    void equalityIgnoresNameAndId() {
        PortableLinkData bare = new PortableLinkData(7, 8, 9, "minecraft:overworld");
        PortableLinkData named =
                new PortableLinkData(7, 8, 9, "minecraft:overworld", "Renamed", 12);
        assertEquals(bare, named, "re-binding the same position stays the same channel");
        assertEquals(bare.hashCode(), named.hashCode());
    }

    @Test
    void displayNamePrefersNameThenFallsBackToCoordinates() {
        assertEquals("My Computer", new PortableLinkData(
                        1, 64, -8, "minecraft:overworld", "My Computer", 3)
                .displayName());
        assertEquals("1, 64, -8", new PortableLinkData(1, 64, -8, "minecraft:overworld")
                .displayName());
    }

    @Test
    void nullNameBecomesEmpty() {
        PortableLinkData link =
                new PortableLinkData(0, 0, 0, "minecraft:overworld", null, -1);
        assertEquals("", link.name());
    }
}
