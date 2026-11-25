package com.livingmarket.mixin;

import com.livingmarket.api.IMerchantOfferExt;
import com.livingmarket.api.IVillagerMarketExt;
import com.livingmarket.client.ClientMarketState;
import com.livingmarket.config.MarketConfig;
import com.livingmarket.logic.BiomeRegion;
import com.livingmarket.logic.VillageContextHelper;
import com.livingmarket.network.MarketNetwork;
import com.livingmarket.network.SyncMarketStatePacket;
import com.livingmarket.routine.DayPhase;
import com.livingmarket.routine.WorkshopHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {

    @Inject(method = "sendMerchantOffers", at = @At("TAIL"))
    private void livingmarket$syncMarketState(
            int containerId,
            MerchantOffers offers,
            int villagerLevel,
            int villagerXp,
            boolean showProgress,
            boolean canRestock,
            CallbackInfo ci
    ) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (self.containerMenu instanceof MerchantMenu menu && menu.containerId == containerId) {
            Merchant trader = ((MerchantMenuAccessor) menu).livingmarket$getTrader();
            if (trader instanceof Villager villager) {
                List<Byte> kinds = new ArrayList<>(offers.size());
                for (MerchantOffer offer : offers) {
                    IMerchantOfferExt ext = (IMerchantOfferExt) offer;
                    if (ext.livingmarket$isRareBarter()) {
                        kinds.add(ClientMarketState.KIND_RARE_BARTER);
                    } else if (ext.livingmarket$isDailyDeal()) {
                        kinds.add(ClientMarketState.KIND_DAILY_DEAL);
                    } else if (ext.livingmarket$isPinned()) {
                        kinds.add(ClientMarketState.KIND_PINNED);
                    } else {
                        kinds.add(ClientMarketState.KIND_NORMAL);
                    }
                }
                int rerollCost = ((IVillagerMarketExt) villager).livingmarket$getCurrentRerollCost();
                int maxPins = MarketConfig.MAX_PINNED_TRADES.get();
                int uniqueProfessions = VillageContextHelper.getUniqueProfessionCount(villager);
                int barterChance = VillageContextHelper.getRareBarterChancePercent(uniqueProfessions);
                String biomeRegionId = BiomeRegion.fromLevelAndPos(villager.level(), villager.blockPosition()).getId();

                String profPath = WorkshopHandler.getProfessionShortName(villager.getVillagerData().getProfession());
                String phaseKey = DayPhase.fromDayTime(villager.level().getDayTime()).getTranslationKey();
                WorkshopHandler.WorkshopStatus status = WorkshopHandler.evaluateStatus(villager);
                String missingItemId = "";
                if (status.missingItem() != null) {
                    ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(status.missingItem());
                    if (itemKey != null) {
                        missingItemId = itemKey.toString();
                    }
                }

                MarketNetwork.sendToPlayer(self, new SyncMarketStatePacket(
                        containerId, true, kinds, rerollCost, maxPins,
                        uniqueProfessions, barterChance, biomeRegionId,
                        profPath, phaseKey, status.statusKey(), missingItemId
                ));
                return;
            }
        }
        MarketNetwork.sendToPlayer(self, new SyncMarketStatePacket(
                containerId, false, Collections.emptyList(), 0, 0, 0, 0,
                "plains_forest", "none", "", "", ""
        ));
    }
}
