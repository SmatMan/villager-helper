package rewrite.villagerhelper.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VillagerLinkRequestPacket(int entityId, BlockPos blockPos) implements CustomPacketPayload {

    public static final Type<VillagerLinkRequestPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("villagerhelper", "villager_link_request"));

    public static final StreamCodec<FriendlyByteBuf, VillagerLinkRequestPacket> CODEC = StreamCodec.of(
        (StreamEncoder<FriendlyByteBuf, VillagerLinkRequestPacket>) (buf, pkt) -> {
            buf.writeInt(pkt.entityId);
            buf.writeBlockPos(pkt.blockPos);
        },
        (StreamDecoder<FriendlyByteBuf, VillagerLinkRequestPacket>) buf -> new VillagerLinkRequestPacket(buf.readInt(), buf.readBlockPos())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
