package com.livingmarket.network;

import com.livingmarket.LivingMarketMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class MarketNetwork {
    private static final String PROTOCOL_VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LivingMarketMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private MarketNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(TogglePinPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TogglePinPacket::encode)
                .decoder(TogglePinPacket::decode)
                .consumerMainThread(TogglePinPacket::handle)
                .add();

        CHANNEL.messageBuilder(RerollTradesPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RerollTradesPacket::encode)
                .decoder(RerollTradesPacket::decode)
                .consumerMainThread(RerollTradesPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncMarketStatePacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncMarketStatePacket::encode)
                .decoder(SyncMarketStatePacket::decode)
                .consumerMainThread(SyncMarketStatePacket::handle)
                .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
