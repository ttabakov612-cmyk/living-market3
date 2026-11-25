package com.livingmarket.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncMarketStatePacket {
    private final int containerId;
    private final boolean enabled;
    private final List<Byte> offerKinds;
    private final int rerollCost;
    private final int maxPins;
    private final int uniqueProfessions;
    private final int barterChancePercent;
    private final String biomeRegionId;
    private final String professionPath;
    private final String phaseKey;
    private final String workshopStatusKey;
    private final String missingItemId;

    public SyncMarketStatePacket(
            int containerId,
            boolean enabled,
            List<Byte> offerKinds,
            int rerollCost,
            int maxPins,
            int uniqueProfessions,
            int barterChancePercent,
            String biomeRegionId,
            String professionPath,
            String phaseKey,
            String workshopStatusKey,
            String missingItemId
    ) {
        this.containerId = containerId;
        this.enabled = enabled;
        this.offerKinds = offerKinds;
        this.rerollCost = rerollCost;
        this.maxPins = maxPins;
        this.uniqueProfessions = uniqueProfessions;
        this.barterChancePercent = barterChancePercent;
        this.biomeRegionId = biomeRegionId;
        this.professionPath = professionPath;
        this.phaseKey = phaseKey;
        this.workshopStatusKey = workshopStatusKey;
        this.missingItemId = missingItemId;
    }

    public int getContainerId() { return containerId; }
    public boolean isEnabled() { return enabled; }
    public List<Byte> getOfferKinds() { return offerKinds; }
    public int getRerollCost() { return rerollCost; }
    public int getMaxPins() { return maxPins; }
    public int getUniqueProfessions() { return uniqueProfessions; }
    public int getBarterChancePercent() { return barterChancePercent; }
    public String getBiomeRegionId() { return biomeRegionId; }
    public String getProfessionPath() { return professionPath; }
    public String getPhaseKey() { return phaseKey; }
    public String getWorkshopStatusKey() { return workshopStatusKey; }
    public String getMissingItemId() { return missingItemId; }

    public static void encode(SyncMarketStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.offerKinds.size());
        for (Byte kind : msg.offerKinds) {
            buf.writeByte(kind != null ? kind : 0);
        }
        buf.writeVarInt(msg.rerollCost);
        buf.writeVarInt(msg.maxPins);
        buf.writeVarInt(msg.uniqueProfessions);
        buf.writeVarInt(msg.barterChancePercent);
        buf.writeUtf(msg.biomeRegionId != null ? msg.biomeRegionId : "plains_forest", 64);
        buf.writeUtf(msg.professionPath != null ? msg.professionPath : "none", 64);
        buf.writeUtf(msg.phaseKey != null ? msg.phaseKey : "", 128);
        buf.writeUtf(msg.workshopStatusKey != null ? msg.workshopStatusKey : "", 128);
        buf.writeUtf(msg.missingItemId != null ? msg.missingItemId : "", 128);
    }

    public static SyncMarketStatePacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        boolean enabled = buf.readBoolean();
        int size = buf.readVarInt();
        List<Byte> kinds = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            kinds.add(buf.readByte());
        }
        int rerollCost = buf.readVarInt();
        int maxPins = buf.readVarInt();
        int uniqueProfessions = buf.readVarInt();
        int barterChancePercent = buf.readVarInt();
        String biomeRegionId = buf.readUtf(64);
        String professionPath = buf.readUtf(64);
        String phaseKey = buf.readUtf(128);
        String workshopStatusKey = buf.readUtf(128);
        String missingItemId = buf.readUtf(128);
        return new SyncMarketStatePacket(
                containerId, enabled, kinds, rerollCost, maxPins,
                uniqueProfessions, barterChancePercent, biomeRegionId,
                professionPath, phaseKey, workshopStatusKey, missingItemId
        );
    }

    public static void handle(SyncMarketStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg));
        ctx.get().setPacketHandled(true);
    }
}
