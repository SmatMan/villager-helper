package rewrite.villagerhelper.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record VillagerUnlinkRequestPacket(int entityId) implements CustomPacketPayload {

    public static final Type<VillagerUnlinkRequestPacket> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath("villagerhelper", "villager_unlink_request"));

    public static final StreamCodec<FriendlyByteBuf, VillagerUnlinkRequestPacket> CODEC = StreamCodec.of(
        (StreamEncoder<FriendlyByteBuf, VillagerUnlinkRequestPacket>) (buf, pkt) -> buf.writeInt(pkt.entityId),
        (StreamDecoder<FriendlyByteBuf, VillagerUnlinkRequestPacket>) buf -> new VillagerUnlinkRequestPacket(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
