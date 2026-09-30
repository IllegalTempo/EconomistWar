package com.economistwars.network;

import com.economistwars.EconomistWars;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Read-only market book snapshot sent when a player opens a settlement market. */
public record SettlementMarketPayload(BlockPos position, List<Offer> offers, List<Trade> history)
        implements CustomPacketPayload {
    public static final Type<SettlementMarketPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(EconomistWars.MOD_ID, "settlement_market")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementMarketPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.position().getX());
                buffer.writeVarInt(payload.position().getY());
                buffer.writeVarInt(payload.position().getZ());
                buffer.writeVarInt(payload.offers().size());
                for (Offer offer : payload.offers()) {
                    buffer.writeUtf(offer.householdId().toString());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, offer.offered());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, offer.requested());
                }
                buffer.writeVarInt(payload.history().size());
                for (Trade trade : payload.history()) {
                    buffer.writeUtf(trade.sellerId().toString());
                    buffer.writeUtf(trade.buyerId().toString());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, trade.sellerItem());
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, trade.buyerItem());
                    buffer.writeLong(trade.gameTime());
                }
            },
            buffer -> {
                BlockPos position = new BlockPos(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
                int offerCount = Math.clamp(buffer.readVarInt(), 0, 256);
                List<Offer> offers = new ArrayList<>(offerCount);
                for (int index = 0; index < offerCount; index++) {
                    offers.add(new Offer(UUID.fromString(buffer.readUtf()),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
                }
                int tradeCount = Math.clamp(buffer.readVarInt(), 0, 256);
                List<Trade> history = new ArrayList<>(tradeCount);
                for (int index = 0; index < tradeCount; index++) {
                    history.add(new Trade(UUID.fromString(buffer.readUtf()), UUID.fromString(buffer.readUtf()),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readLong()));
                }
                return new SettlementMarketPayload(position, offers, history);
            }
    );

    public SettlementMarketPayload {
        offers = List.copyOf(offers);
        history = List.copyOf(history);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Offer(UUID householdId, ItemStack offered, ItemStack requested) {
        public Offer {
            offered = offered.copy();
            requested = requested.copy();
        }
    }

    public record Trade(UUID sellerId, UUID buyerId, ItemStack sellerItem, ItemStack buyerItem, long gameTime) {
        public Trade {
            sellerItem = sellerItem.copyWithCount(1);
            buyerItem = buyerItem.copyWithCount(1);
        }
    }
}
