package dev.propulsionteam.computed.content.items;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Binding stored on a link card: the position and dimension of the linked computer, plus the
 * computer's display name and numeric id when they were known at bind time (older cards simply
 * lack both). Links are equal when they point at the same position — name and id are extra
 * payload, so re-binding the same computer reuses its channel even with an updated name.
 * Kept free of ItemStack so the tag round trip stays unit-testable; item-level read/write lives
 * on {@link LinkCardItem}.
 */
public record PortableLinkData(int x, int y, int z, String dimension, String name, int computerId) {
    public PortableLinkData(int x, int y, int z, String dimension) {
        this(x, y, z, dimension, "", -1);
    }

    public PortableLinkData {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("Link dimension must not be blank");
        }
        if (name == null) {
            name = "";
        }
    }

    public BlockPos blockPos() {
        return new BlockPos(x, y, z);
    }

    public ResourceLocation dimensionId() {
        return ResourceLocation.parse(dimension);
    }

    /** Player-facing label: the computer's name, or its coordinates when unnamed. */
    public String displayName() {
        return name.isBlank() ? x + ", " + y + ", " + z : name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PortableLinkData link
                && x == link.x
                && y == link.y
                && z == link.z
                && dimension.equals(link.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, dimension);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putString("dimension", dimension);
        if (!name.isEmpty()) {
            tag.putString("name", name);
        }
        if (computerId >= 0) {
            tag.putInt("computerId", computerId);
        }
        return tag;
    }

    public static PortableLinkData of(CompoundTag tag) {
        if (tag == null || !tag.contains("dimension")) {
            throw new IllegalArgumentException("Link tag is missing a dimension");
        }
        return new PortableLinkData(
                tag.getInt("x"),
                tag.getInt("y"),
                tag.getInt("z"),
                tag.getString("dimension"),
                tag.contains("name") ? tag.getString("name") : "",
                tag.contains("computerId") ? tag.getInt("computerId") : -1);
    }
}
