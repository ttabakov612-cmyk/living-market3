package com.livingmarket.network;

import com.livingmarket.api.IMerchantOfferExt;
import com.livingmarket.api.IVillagerMarketExt;
import com.livingmarket.logic.TradeGenerationHelper;
import com.livingmarket.mixin.MerchantMenuAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RerollTradesPacket {
    public RerollTradesPacket() {}

    public static void encode(RerollTradesPacket msg, FriendlyByteBuf buf) {
    }

    public static RerollTradesPacket decode(FriendlyByteBuf buf) {
        return new RerollTradesPacket();
    }

    public static void handle(RerollTradesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof MerchantMenu menu) {
            Merchant trader = ((MerchantMenuAccessor) menu).livingmarket$getTrader();
            if (trader instanceof Villager villager) {
                IVillagerMarketExt ext = (IVillagerMarketExt) villager;
                ext.livingmarket$checkDailyRefresh();

                int rerollableNormal = 0;
                for (MerchantOffer offer : villager.getOffers()) {
                    IMerchantOfferExt offerExt = (IMerchantOfferExt) offer;
                    if (!offerExt.livingmarket$isSpecial() && !offerExt.livingmarket$isPinned()) {
                        rerollableNormal++;
                    }
                }

                if (rerollableNormal > 0) {
                    int cost = ext.livingmarket$getCurrentRerollCost();
                    if (TradeGenerationHelper.consumeEmeralds(player, cost)) {
                        if (ext.livingmarket$refreshUnpinnedTrades(true)) {
                            ext.livingmarket$setRerollsToday(ext.livingmarket$getRerollsToday() + 1);
                            villager.level().playSound(
                                    null,
                                    villager.blockPosition(),
                                    SoundEvents.VILLAGER_WORK_LIBRARIAN,
                                    SoundSource.NEUTRAL,
                                    1.0F,
                                    1.05F
                            );
                            player.sendMerchantOffers(
                                    menu.containerId,
                                    villager.getOffers(),
                                    villager.getVillagerData().getLevel(),
                                    villager.getVillagerXp(),
                                    villager.showProgressBar(),
                                    villager.canRestock()
                            );
                        }
                    }
                }
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
