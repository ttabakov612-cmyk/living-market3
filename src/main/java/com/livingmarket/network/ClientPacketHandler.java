package com.livingmarket.network;

import com.livingmarket.client.ClientMarketState;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void handleSync(SyncMarketStatePacket msg) {
        ClientMarketState.update(
                msg.getContainerId(),
                msg.isEnabled(),
                msg.getOfferKinds(),
                msg.getRerollCost(),
                msg.getMaxPins(),
                msg.getUniqueProfessions(),
                msg.getBarterChancePercent(),
                msg.getBiomeRegionId(),
                msg.getProfessionPath(),
                msg.getPhaseKey(),
                msg.getWorkshopStatusKey(),
                msg.getMissingItemId()
        );
    }
}
