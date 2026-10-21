package dev.propulsionteam.computed.network;

import dev.propulsionteam.computed.Computed;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client asks for the glasses HUD lines of the computer its bound link card points at, once when
 * the link resolves and periodically as a fallback. The server answers only when the requesting
 * player carries a link to that computer, and only when the line hash differs from
 * {@code knownHash}.
 */
public record RequestGlassesHudPayload(String dimension, BlockPos computerPos, long knownHash)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestGlassesHudPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Computed.MODID, "request_glasses_hud"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestGlassesHudPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    RequestGlassesHudPayload::dimension,
                    BlockPos.STREAM_CODEC,
                    RequestGlassesHudPayload::computerPos,
                    ByteBufCodecs.VAR_LONG,
                    RequestGlassesHudPayload::knownHash,
                    RequestGlassesHudPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
