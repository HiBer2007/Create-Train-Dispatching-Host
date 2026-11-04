package top.hibermod.ctdhost.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端把屏蔽门的配置写回服务端。 */
public record CtdDoorConfigPayload(BlockPos pos, String station, String platform,
                                   String doorId, float speed, float height, boolean testMode)
        implements CustomPacketPayload {

    public static final Type<CtdDoorConfigPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("ctdhost", "door_config"));

    public static final StreamCodec<FriendlyByteBuf, CtdDoorConfigPayload> CODEC =
        new StreamCodec<>() {
            @Override
            public CtdDoorConfigPayload decode(FriendlyByteBuf buf) {
                return new CtdDoorConfigPayload(buf.readBlockPos(), buf.readUtf(64),
                    buf.readUtf(64), buf.readUtf(32), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean());
            }

            @Override
            public void encode(FriendlyByteBuf buf, CtdDoorConfigPayload p) {
                buf.writeBlockPos(p.pos());
                buf.writeUtf(p.station(), 64);
                buf.writeUtf(p.platform(), 64);
                buf.writeUtf(p.doorId(), 32);
                buf.writeFloat(p.speed());
                buf.writeFloat(p.height());
                buf.writeBoolean(p.testMode());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
