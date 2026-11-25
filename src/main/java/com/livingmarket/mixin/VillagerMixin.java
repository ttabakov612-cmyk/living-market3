package com.livingmarket.mixin;

import com.livingmarket.api.IMerchantOfferExt;
import com.livingmarket.api.IVillagerMarketExt;
import com.livingmarket.config.MarketConfig;
import com.livingmarket.logic.TradeGenerationHelper;
import com.livingmarket.logic.VillageContextHelper;
import com.livingmarket.routine.VillagerRoutineController;
import com.livingmarket.routine.VillagerRoutineData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements IVillagerMarketExt {
    @Shadow
    public abstract VillagerData getVillagerData();

    @Unique
    private long livingmarket$lastRefreshDay = -1L;

    @Unique
    private int livingmarket$rerollsToday = 0;

    @Unique
    private final VillagerRoutineData livingmarket$routineData = new VillagerRoutineData();

    protected VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void livingmarket$saveData(CompoundTag tag, CallbackInfo ci) {
        tag.putLong("lmLastRefreshDay", this.livingmarket$lastRefreshDay);
        tag.putInt("lmRerollsToday", this.livingmarket$rerollsToday);
        tag.put("lmRoutine", this.livingmarket$routineData.save());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void livingmarket$loadData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("lmLastRefreshDay")) {
            this.livingmarket$lastRefreshDay = tag.getLong("lmLastRefreshDay");
        }
        if (tag.contains("lmRerollsToday")) {
            this.livingmarket$rerollsToday = tag.getInt("lmRerollsToday");
        }
        if (tag.contains("lmRoutine")) {
            this.livingmarket$routineData.load(tag.getCompound("lmRoutine"));
        }
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void livingmarket$updateTrades(CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        VillagerData data = this.getVillagerData();
        VillagerProfession profession = data.getProfession();
        if (!VillageContextHelper.isWorkingProfession(profession)) {
            return;
        }

        int level = Math.max(1, Math.min(5, data.getLevel()));
        boolean firstInit = (this.offers == null || this.offers.isEmpty());
        if (this.offers == null) {
            this.offers = new MerchantOffers();
        }
        TradeGenerationHelper.populateLevelTrades(self, this.offers, level);

        if (!this.level().isClientSide()) {
            long currentDay = this.level().getDayTime() / 24000L;
            if (this.livingmarket$lastRefreshDay < 0L || firstInit) {
                this.livingmarket$lastRefreshDay = currentDay;
                this.livingmarket$rerollsToday = 0;
                TradeGenerationHelper.tryAttachInitialSpecials(self, this.offers, currentDay);
            }
        }
        ci.cancel();
    }

    @Inject(method = "startTrading", at = @At("HEAD"))
    private void livingmarket$onStartTrading(Player player, CallbackInfo ci) {
        if (!this.level().isClientSide()) {
            Villager self = (Villager) (Object) this;
            VillagerRoutineController.clearRoutineHeldItem(self, this.livingmarket$routineData);
            this.livingmarket$checkDailyRefresh();
        }
    }

    @Inject(method = "restock", at = @At("TAIL"))
    private void livingmarket$onRestock(CallbackInfo ci) {
        if (!this.level().isClientSide()) {
            this.livingmarket$checkDailyRefresh();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void livingmarket$onTick(CallbackInfo ci) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        VillagerProfession profession = this.getVillagerData().getProfession();
        if (!VillageContextHelper.isWorkingProfession(profession)) {
            return;
        }

        // Living Market 3.0: 6-phase village daily routine & real profession workshops
        Villager self = (Villager) (Object) this;
        VillagerRoutineController.tick(self, this.livingmarket$routineData);

        // Lazy daily refresh check every 10 seconds when a player is nearby so morning particles appear automatically
        if ((this.tickCount + this.getId()) % 200 == 0) {
            if (serverLevel.hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(), 32.0D)) {
                this.livingmarket$checkDailyRefresh();
            }
        }

        // Subtle world particles above villagers with active Daily Deal or Rare Barter
        if (MarketConfig.ENABLE_WORLD_PARTICLES.get() && (this.tickCount + this.getId()) % 30 == 0) {
            if (this.offers != null && serverLevel.hasNearbyAlivePlayer(this.getX(), this.getY(), this.getZ(), 24.0D)) {
                boolean hasBarter = this.livingmarket$hasActiveRareBarter();
                boolean hasDaily = this.livingmarket$hasActiveDailyDeal();
                if (hasBarter) {
                    serverLevel.sendParticles(
                            ParticleTypes.WITCH,
                            this.getX(),
                            this.getY() + 2.15D,
                            this.getZ(),
                            2,
                            0.25D,
                            0.15D,
                            0.25D,
                            0.01D
                    );
                    serverLevel.sendParticles(
                            ParticleTypes.END_ROD,
                            this.getX(),
                            this.getY() + 2.25D,
                            this.getZ(),
                            1,
                            0.2D,
                            0.1D,
                            0.2D,
                            0.01D
                    );
                } else if (hasDaily) {
                    serverLevel.sendParticles(
                            ParticleTypes.WAX_ON,
                            this.getX(),
                            this.getY() + 2.15D,
                            this.getZ(),
                            2,
                            0.28D,
                            0.15D,
                            0.28D,
                            0.02D
                    );
                }
            }
        }
    }

    @Override
    public long livingmarket$getLastRefreshDay() {
        return this.livingmarket$lastRefreshDay;
    }

    @Override
    public void livingmarket$setLastRefreshDay(long day) {
        this.livingmarket$lastRefreshDay = day;
    }

    @Override
    public int livingmarket$getRerollsToday() {
        return this.livingmarket$rerollsToday;
    }

    @Override
    public void livingmarket$setRerollsToday(int count) {
        this.livingmarket$rerollsToday = Math.max(0, count);
    }

    @Override
    public int livingmarket$getCurrentRerollCost() {
        return TradeGenerationHelper.calculateRerollCost(this.livingmarket$rerollsToday);
    }

    @Override
    public void livingmarket$checkDailyRefresh() {
        if (this.level().isClientSide()) {
            return;
        }
        VillagerProfession profession = this.getVillagerData().getProfession();
        if (!VillageContextHelper.isWorkingProfession(profession)) {
            return;
        }

        long currentDay = this.level().getDayTime() / 24000L;
        if (this.offers == null) {
            this.getOffers();
            this.livingmarket$lastRefreshDay = currentDay;
            this.livingmarket$rerollsToday = 0;
            return;
        }

        if (this.livingmarket$lastRefreshDay < 0L) {
            this.livingmarket$lastRefreshDay = currentDay;
            this.livingmarket$rerollsToday = 0;
            Villager self = (Villager) (Object) this;
            TradeGenerationHelper.tryAttachInitialSpecials(self, this.offers, currentDay);
            return;
        }

        if (currentDay > this.livingmarket$lastRefreshDay) {
            this.livingmarket$lastRefreshDay = currentDay;
            this.livingmarket$rerollsToday = 0;
            if (MarketConfig.ENABLE_DAILY_REFRESH.get()) {
                this.livingmarket$refreshUnpinnedTrades(false);
            }
        }
    }

    @Override
    public boolean livingmarket$refreshUnpinnedTrades(boolean preserveSpecials) {
        Villager self = (Villager) (Object) this;
        long currentDay = this.level().getDayTime() / 24000L;
        return TradeGenerationHelper.rebuildTrades(self, preserveSpecials, currentDay);
    }

    @Override
    public boolean livingmarket$togglePin(int offerIndex) {
        MerchantOffers currentOffers = this.getOffers();
        if (offerIndex < 0 || offerIndex >= currentOffers.size()) {
            return false;
        }
        MerchantOffer offer = currentOffers.get(offerIndex);
        IMerchantOfferExt ext = (IMerchantOfferExt) offer;
        if (ext.livingmarket$isSpecial()) {
            return false;
        }
        if (ext.livingmarket$isPinned()) {
            ext.livingmarket$setPinned(false);
            return true;
        } else {
            int maxPins = MarketConfig.MAX_PINNED_TRADES.get();
            if (this.livingmarket$getPinnedCount() >= maxPins) {
                return false;
            }
            ext.livingmarket$setPinned(true);
            return true;
        }
    }

    @Override
    public int livingmarket$getPinnedCount() {
        MerchantOffers currentOffers = this.getOffers();
        int count = 0;
        for (MerchantOffer offer : currentOffers) {
            IMerchantOfferExt ext = (IMerchantOfferExt) offer;
            if (!ext.livingmarket$isSpecial() && ext.livingmarket$isPinned()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean livingmarket$hasActiveDailyDeal() {
        if (this.offers == null) {
            return false;
        }
        for (MerchantOffer offer : this.offers) {
            if (((IMerchantOfferExt) offer).livingmarket$isDailyDeal() && !offer.isOutOfStock()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean livingmarket$hasActiveRareBarter() {
        if (this.offers == null) {
            return false;
        }
        for (MerchantOffer offer : this.offers) {
            if (((IMerchantOfferExt) offer).livingmarket$isRareBarter() && !offer.isOutOfStock()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public VillagerRoutineData livingmarket$getRoutineData() {
        return this.livingmarket$routineData;
    }
}
