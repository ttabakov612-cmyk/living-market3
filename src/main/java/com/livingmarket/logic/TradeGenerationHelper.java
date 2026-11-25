package com.livingmarket.logic;

import com.livingmarket.api.IMerchantOfferExt;
import com.livingmarket.config.MarketConfig;
import com.livingmarket.data.BiomeMarketManager;
import com.livingmarket.data.MarketTradeManager;
import com.livingmarket.data.TradeDefinition;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TradeGenerationHelper {
    private TradeGenerationHelper() {}

    /**
     * Populates a single villager level with 3 curated slots:
     * 1 BASIC_BUY + 1 RARE_BUY + 1 SELL (with optional biome specialty flavor).
     */
    public static void populateLevelTrades(Villager villager, MerchantOffers offers, int level) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!VillageContextHelper.isWorkingProfession(profession)) {
            return;
        }

        Set<String> usedKeys = collectUsedKeys(offers);
        boolean hasBasicBuy = false;
        boolean hasRareBuy = false;
        boolean hasSell = false;

        for (MerchantOffer offer : offers) {
            ensureMetadata(offer, level);
            IMerchantOfferExt ext = (IMerchantOfferExt) offer;
            if (ext.livingmarket$getTradeLevel() == level && !ext.livingmarket$isSpecial()) {
                String type = ext.livingmarket$getTradeType();
                if (IMerchantOfferExt.TYPE_BASIC_BUY.equals(type)) {
                    hasBasicBuy = true;
                } else if (IMerchantOfferExt.TYPE_RARE_BUY.equals(type)) {
                    hasRareBuy = true;
                } else if (IMerchantOfferExt.TYPE_SELL.equals(type)) {
                    hasSell = true;
                }
            }
        }

        BiomeRegion region = BiomeRegion.fromLevelAndPos(villager.level(), villager.blockPosition());

        if (!hasBasicBuy) {
            MerchantOffer o = generateSingleNormalSlot(villager, profession, level, IMerchantOfferExt.TYPE_BASIC_BUY, region, usedKeys);
            if (o != null) {
                offers.add(o);
            }
        }
        if (!hasRareBuy) {
            MerchantOffer o = generateSingleNormalSlot(villager, profession, level, IMerchantOfferExt.TYPE_RARE_BUY, region, usedKeys);
            if (o != null) {
                offers.add(o);
            }
        }
        if (!hasSell) {
            MerchantOffer o = generateSingleNormalSlot(villager, profession, level, IMerchantOfferExt.TYPE_SELL, region, usedKeys);
            if (o != null) {
                offers.add(o);
            }
        }
    }

    /**
     * Rebuilds villager offers:
     * - If preserveSpecials == true (manual reroll): keeps current Daily Deal & Rare Barter and all pinned normal trades.
     * - If preserveSpecials == false (new day): drops old specials, rolls fresh Daily Deal & Rare Barter, keeps pinned normal trades.
     */
    public static boolean rebuildTrades(Villager villager, boolean preserveSpecials, long currentDay) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!VillageContextHelper.isWorkingProfession(profession)) {
            return false;
        }

        MerchantOffers currentOffers = villager.getOffers();
        int maxLevel = Math.max(1, Math.min(5, villager.getVillagerData().getLevel()));
        BiomeRegion region = BiomeRegion.fromLevelAndPos(villager.level(), villager.blockPosition());

        MerchantOffer keptRareBarter = null;
        MerchantOffer keptDailyDeal = null;
        Map<Integer, List<MerchantOffer>> pinnedByLevel = new HashMap<>();
        int normalCount = 0;
        int pinnedCount = 0;

        for (int i = 0; i < currentOffers.size(); i++) {
            MerchantOffer offer = currentOffers.get(i);
            int fallbackLevel = Math.min(maxLevel, Math.max(1, (i / 3) + 1));
            ensureMetadata(offer, fallbackLevel);

            IMerchantOfferExt ext = (IMerchantOfferExt) offer;
            if (ext.livingmarket$isRareBarter()) {
                if (preserveSpecials) {
                    keptRareBarter = offer;
                }
                continue;
            }
            if (ext.livingmarket$isDailyDeal()) {
                if (preserveSpecials) {
                    keptDailyDeal = offer;
                }
                continue;
            }

            normalCount++;
            if (ext.livingmarket$isPinned()) {
                int lvl = Math.min(maxLevel, Math.max(1, ext.livingmarket$getTradeLevel()));
                ext.livingmarket$setTradeLevel(lvl);
                pinnedByLevel.computeIfAbsent(lvl, k -> new ArrayList<>()).add(offer);
                pinnedCount++;
            }
        }

        if (preserveSpecials && normalCount > 0 && pinnedCount >= normalCount && normalCount >= maxLevel * 3) {
            return false;
        }

        Set<String> usedKeys = new HashSet<>();
        for (List<MerchantOffer> list : pinnedByLevel.values()) {
            for (MerchantOffer pinned : list) {
                usedKeys.add(getOfferKey(pinned));
            }
        }

        List<MerchantOffer> rebuilt = new ArrayList<>();

        // 1. Rare Barter slot (topmost, level 4-5 only)
        if (preserveSpecials) {
            if (keptRareBarter != null) {
                rebuilt.add(keptRareBarter);
            }
        } else if (maxLevel >= 4) {
            MerchantOffer barter = rollRareBarter(villager, profession);
            if (barter != null) {
                rebuilt.add(barter);
            }
        }

        // 2. Daily Deal slot (top, biome deficit, ~30% of village)
        if (preserveSpecials) {
            if (keptDailyDeal != null) {
                rebuilt.add(keptDailyDeal);
                usedKeys.add(getOfferKey(keptDailyDeal));
            }
        } else {
            MerchantOffer dailyDeal = rollDailyDeal(villager, region, currentDay);
            if (dailyDeal != null) {
                rebuilt.add(dailyDeal);
                usedKeys.add(getOfferKey(dailyDeal));
            }
        }

        // 3. Normal trades: 1 BASIC_BUY + 1 RARE_BUY + 1 SELL per level
        for (int lvl = 1; lvl <= maxLevel; lvl++) {
            List<MerchantOffer> lvlPinned = pinnedByLevel.getOrDefault(lvl, Collections.emptyList());

            MerchantOffer pinnedBasic = null;
            MerchantOffer pinnedRare = null;
            MerchantOffer pinnedSell = null;
            List<MerchantOffer> extraPinned = new ArrayList<>();

            for (MerchantOffer offer : lvlPinned) {
                String type = ((IMerchantOfferExt) offer).livingmarket$getTradeType();
                if (IMerchantOfferExt.TYPE_BASIC_BUY.equals(type) && pinnedBasic == null) {
                    pinnedBasic = offer;
                } else if (IMerchantOfferExt.TYPE_RARE_BUY.equals(type) && pinnedRare == null) {
                    pinnedRare = offer;
                } else if (IMerchantOfferExt.TYPE_SELL.equals(type) && pinnedSell == null) {
                    pinnedSell = offer;
                } else {
                    extraPinned.add(offer);
                }
            }

            if (pinnedBasic != null) {
                rebuilt.add(pinnedBasic);
            } else {
                MerchantOffer gen = generateSingleNormalSlot(villager, profession, lvl, IMerchantOfferExt.TYPE_BASIC_BUY, region, usedKeys);
                if (gen != null) {
                    rebuilt.add(gen);
                }
            }

            if (pinnedRare != null) {
                rebuilt.add(pinnedRare);
            } else {
                MerchantOffer gen = generateSingleNormalSlot(villager, profession, lvl, IMerchantOfferExt.TYPE_RARE_BUY, region, usedKeys);
                if (gen != null) {
                    rebuilt.add(gen);
                }
            }

            if (pinnedSell != null) {
                rebuilt.add(pinnedSell);
            } else {
                MerchantOffer gen = generateSingleNormalSlot(villager, profession, lvl, IMerchantOfferExt.TYPE_SELL, region, usedKeys);
                if (gen != null) {
                    rebuilt.add(gen);
                }
            }

            rebuilt.addAll(extraPinned);
        }

        currentOffers.clear();
        currentOffers.addAll(rebuilt);
        return true;
    }

    /**
     * Rolls initial Daily Deal and Rare Barter for a freshly initialized villager without discarding their newly generated level-1 offers.
     */
    public static void tryAttachInitialSpecials(Villager villager, MerchantOffers offers, long currentDay) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (!VillageContextHelper.isWorkingProfession(profession)) {
            return;
        }
        int level = Math.max(1, Math.min(5, villager.getVillagerData().getLevel()));
        BiomeRegion region = BiomeRegion.fromLevelAndPos(villager.level(), villager.blockPosition());

        boolean hasDaily = false;
        boolean hasBarter = false;
        for (MerchantOffer offer : offers) {
            IMerchantOfferExt ext = (IMerchantOfferExt) offer;
            if (ext.livingmarket$isDailyDeal()) {
                hasDaily = true;
            }
            if (ext.livingmarket$isRareBarter()) {
                hasBarter = true;
            }
        }

        if (!hasDaily) {
            MerchantOffer dailyDeal = rollDailyDeal(villager, region, currentDay);
            if (dailyDeal != null) {
                offers.add(0, dailyDeal);
            }
        }

        if (!hasBarter && level >= 4) {
            MerchantOffer barter = rollRareBarter(villager, profession);
            if (barter != null) {
                offers.add(0, barter);
            }
        }
    }

    @Nullable
    private static MerchantOffer rollDailyDeal(Villager villager, BiomeRegion region, long currentDay) {
        if (!VillageContextHelper.isSelectedForDailyDeal(villager, currentDay)) {
            return null;
        }
        BiomeMarketManager.BiomeMarketPool biomePool = BiomeMarketManager.INSTANCE.getPool(region);
        if (biomePool == null || biomePool.dailyDeals.isEmpty()) {
            return null;
        }

        RandomSource random = villager.getRandom();
        List<TradeDefinition> candidates = new ArrayList<>(biomePool.dailyDeals);
        shuffleList(candidates, random);

        for (TradeDefinition def : candidates) {
            MerchantOffer offer = def.createOffer(villager, random, 0, IMerchantOfferExt.TYPE_DAILY_DEAL);
            if (offer != null) {
                return offer;
            }
        }
        return null;
    }

    @Nullable
    private static MerchantOffer rollRareBarter(Villager villager, VillagerProfession profession) {
        List<TradeDefinition> barters = MarketTradeManager.INSTANCE.getRareBarters(profession);
        if (barters.isEmpty()) {
            return null;
        }

        int uniqueProfessions = VillageContextHelper.getUniqueProfessionCount(villager);
        int chance = VillageContextHelper.getRareBarterChancePercent(uniqueProfessions);
        RandomSource random = villager.getRandom();
        if (chance <= 0 || random.nextInt(100) >= chance) {
            return null;
        }

        List<TradeDefinition> candidates = new ArrayList<>(barters);
        shuffleList(candidates, random);
        for (TradeDefinition def : candidates) {
            MerchantOffer offer = def.createOffer(villager, random, 0, IMerchantOfferExt.TYPE_RARE_BARTER);
            if (offer != null) {
                return offer;
            }
        }
        return null;
    }

    @Nullable
    private static MerchantOffer generateSingleNormalSlot(
            Villager villager,
            VillagerProfession profession,
            int level,
            String tradeType,
            BiomeRegion region,
            Set<String> usedKeys
    ) {
        RandomSource random = villager.getRandom();

        // Regional specialty flavor chance on SELL slot (levels 1-3 so high-tier master sells stay reliable)
        if (IMerchantOfferExt.TYPE_SELL.equals(tradeType) && level <= 3) {
            int regionalChance = MarketConfig.REGIONAL_SELL_CHANCE_PERCENT.get();
            if (regionalChance > 0 && random.nextInt(100) < regionalChance) {
                BiomeMarketManager.BiomeMarketPool biomePool = BiomeMarketManager.INSTANCE.getPool(region);
                if (biomePool != null && !biomePool.regionalSells.isEmpty()) {
                    MerchantOffer regional = pickFromList(villager, biomePool.regionalSells, level, tradeType, usedKeys, random, false);
                    if (regional != null) {
                        return regional;
                    }
                }
            }
        }

        // Primary source: custom JSON pool for profession + level + slot type
        MarketTradeManager.LevelPool pool = MarketTradeManager.INSTANCE.getPool(profession, level);
        if (pool != null) {
            List<TradeDefinition> source = switch (tradeType) {
                case IMerchantOfferExt.TYPE_BASIC_BUY -> pool.basicBuy;
                case IMerchantOfferExt.TYPE_RARE_BUY -> pool.rareBuy;
                default -> pool.sell;
            };
            MerchantOffer picked = pickFromList(villager, source, level, tradeType, usedKeys, random, false);
            if (picked != null) {
                return picked;
            }
            // Fallback within same pool allowing duplicates if pool is small
            picked = pickFromList(villager, source, level, tradeType, usedKeys, random, true);
            if (picked != null) {
                return picked;
            }
        }

        // Modded profession fallback: vanilla/Forge VillagerTrades registry
        boolean isBuy = !IMerchantOfferExt.TYPE_SELL.equals(tradeType);
        return pickFromVanillaRegistry(villager, profession, level, tradeType, isBuy, usedKeys, random);
    }

    @Nullable
    private static MerchantOffer pickFromList(
            Villager villager,
            List<TradeDefinition> defs,
            int level,
            String tradeType,
            Set<String> usedKeys,
            RandomSource random,
            boolean allowDuplicate
    ) {
        if (defs.isEmpty()) {
            return null;
        }
        List<TradeDefinition> candidates = new ArrayList<>(defs);
        shuffleList(candidates, random);

        for (TradeDefinition def : candidates) {
            String key = def.getDeduplicationKey(tradeType);
            if (!allowDuplicate && usedKeys.contains(key)) {
                continue;
            }
            MerchantOffer offer = def.createOffer(villager, random, level, tradeType);
            if (offer != null) {
                usedKeys.add(key);
                usedKeys.add(getOfferKey(offer));
                return offer;
            }
        }
        return null;
    }

    @Nullable
    private static MerchantOffer pickFromVanillaRegistry(
            Villager villager,
            VillagerProfession profession,
            int level,
            String tradeType,
            boolean isBuy,
            Set<String> usedKeys,
            RandomSource random
    ) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> vanillaMap = VillagerTrades.TRADES.get(profession);
        if (vanillaMap == null || vanillaMap.isEmpty()) {
            return null;
        }

        int[] searchOrder = new int[]{level, Math.max(1, level - 1), Math.min(5, level + 1), 1, 2, 3, 4, 5};
        Set<Integer> visited = new HashSet<>();

        for (int candidateLevel : searchOrder) {
            if (!visited.add(candidateLevel)) {
                continue;
            }
            VillagerTrades.ItemListing[] listings = vanillaMap.get(candidateLevel);
            if (listings == null || listings.length == 0) {
                continue;
            }
            List<VillagerTrades.ItemListing> shuffled = new ArrayList<>();
            Collections.addAll(shuffled, listings);
            shuffleList(shuffled, random);

            for (VillagerTrades.ItemListing listing : shuffled) {
                MerchantOffer offer = listing.getOffer(villager, random);
                if (offer == null) {
                    continue;
                }
                if (inferIsBuy(offer) != isBuy) {
                    continue;
                }
                String key = getOfferKey(offer);
                if (usedKeys.contains(key)) {
                    continue;
                }
                IMerchantOfferExt ext = (IMerchantOfferExt) offer;
                ext.livingmarket$setPinned(false);
                ext.livingmarket$setTradeLevel(level);
                ext.livingmarket$setTradeType(tradeType);
                usedKeys.add(key);
                return offer;
            }
        }
        return null;
    }

    public static void ensureMetadata(MerchantOffer offer, int defaultLevel) {
        IMerchantOfferExt ext = (IMerchantOfferExt) offer;
        String type = ext.livingmarket$getTradeType();
        if (type == null || type.isEmpty() || "BUY".equals(type)) {
            if ("BUY".equals(type)) {
                ext.livingmarket$setTradeType(IMerchantOfferExt.TYPE_BASIC_BUY);
            } else {
                ext.livingmarket$setTradeType(inferIsBuy(offer) ? IMerchantOfferExt.TYPE_BASIC_BUY : IMerchantOfferExt.TYPE_SELL);
            }
        }
        if (!ext.livingmarket$isSpecial() && ext.livingmarket$getTradeLevel() <= 0) {
            ext.livingmarket$setTradeLevel(Math.max(1, Math.min(5, defaultLevel)));
        }
    }

    public static boolean inferIsBuy(MerchantOffer offer) {
        return offer.getResult().is(Items.EMERALD) && !offer.getBaseCostA().is(Items.EMERALD);
    }

    private static Set<String> collectUsedKeys(MerchantOffers offers) {
        Set<String> keys = new HashSet<>();
        for (MerchantOffer offer : offers) {
            keys.add(getOfferKey(offer));
        }
        return keys;
    }

    private static String getOfferKey(MerchantOffer offer) {
        boolean isBuy = inferIsBuy(offer);
        ItemStack primary = isBuy ? offer.getBaseCostA() : offer.getResult();
        if (primary.is(Items.ENCHANTED_BOOK)) {
            return (isBuy ? "BUY:" : "SELL:") + "enchanted_book";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(primary.getItem());
        return (isBuy ? "BUY:" : "SELL:") + (id != null ? id.toString() : primary.getItem().toString());
    }

    private static <T> void shuffleList(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    public static int calculateRerollCost(int rerollsToday) {
        int base = MarketConfig.BASE_REROLL_COST.get();
        if (base <= 0) {
            return 0;
        }
        double multiplier = MarketConfig.REROLL_COST_MULTIPLIER.get();
        int cap = MarketConfig.MAX_REROLL_COST.get();
        double raw = base * Math.pow(multiplier, Math.max(0, rerollsToday));
        return (int) Math.min(cap, Math.max(1L, Math.round(raw)));
    }

    public static boolean consumeEmeralds(ServerPlayer player, int amount) {
        if (amount <= 0 || player.getAbilities().instabuild) {
            return true;
        }
        if (player.getInventory().countItem(Items.EMERALD) < amount) {
            return false;
        }

        int remaining = amount;
        for (int i = 0; i < player.getInventory().items.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        for (int i = 0; i < player.getInventory().offhand.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().offhand.get(i);
            if (stack.is(Items.EMERALD)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        return remaining == 0;
    }
}
