package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server answer carrying the glasses HUD widget draw list of one computer: the wire-encoded
 * {@code WidgetDrawList} (widgets at their own x/y/size, like on a monitor) plus the state hash.
 * Hash-gated like the portable display frame.
 */
public record GlassesHudWidgetsPayload(String dimension, BlockPos computerPos, long hash, byte[] drawListBytes)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GlassesHudWidgetsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "glasses_hud_widgets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GlassesHudWidgetsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    GlassesHudWidgetsPayload::dimension,
                    BlockPos.STREAM_CODEC,
                    GlassesHudWidgetsPayload::computerPos,
                    ByteBufCodecs.VAR_LONG,
                    GlassesHudWidgetsPayload::hash,
                    ByteBufCodecs.BYTE_ARRAY,
                    GlassesHudWidgetsPayload::drawListBytes,
                    GlassesHudWidgetsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
