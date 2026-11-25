package com.livingmarket.data;

import com.google.gson.JsonObject;
import com.livingmarket.api.IMerchantOfferExt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.SuspiciousStewItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

public class TradeDefinition {
    private static final MobEffect[] STEW_EFFECTS = new MobEffect[]{
            MobEffects.NIGHT_VISION,
            MobEffects.JUMP,
            MobEffects.WEAKNESS,
            MobEffects.BLINDNESS,
            MobEffects.POISON,
            MobEffects.SATURATION
    };

    private static final Enchantment[] TREASURE_BOOK_POOL = new Enchantment[]{
            Enchantments.MENDING,
            Enchantments.FROST_WALKER,
            Enchantments.SOUL_SPEED,
            Enchantments.SWIFT_SNEAK
    };

    public final String item;
    public final int countMin;
    public final int countMax;
    public final int emeraldsMin;
    public final int emeraldsMax;
    public final int maxUses;
    public final int xp;
    public final float priceMultiplier;
    @Nullable
    public final String special;
    @Nullable
    public final String costItem;
    public final int costItemCount;
    @Nullable
    public final String costA;
    public final int costACount;
    @Nullable
    public final String costB;
    public final int costBCount;
    @Nullable
    public final String potion;
    @Nullable
    public final String mapStructureTag;
    @Nullable
    public final String mapDecoration;

    public TradeDefinition(
            String item,
            int countMin,
            int countMax,
            int emeraldsMin,
            int emeraldsMax,
            int maxUses,
            int xp,
            float priceMultiplier,
            @Nullable String special,
            @Nullable String costItem,
            int costItemCount,
            @Nullable String costA,
            int costACount,
            @Nullable String costB,
            int costBCount,
            @Nullable String potion,
            @Nullable String mapStructureTag,
            @Nullable String mapDecoration
    ) {
        this.item = item;
        this.countMin = Math.max(1, countMin);
        this.countMax = Math.max(this.countMin, countMax);
        this.emeraldsMin = Math.max(1, emeraldsMin);
        this.emeraldsMax = Math.max(this.emeraldsMin, emeraldsMax);
        this.maxUses = Math.max(1, maxUses);
        this.xp = Math.max(0, xp);
        this.priceMultiplier = priceMultiplier;
        this.special = special;
        this.costItem = costItem;
        this.costItemCount = Math.max(1, costItemCount);
        this.costA = costA;
        this.costACount = Math.max(1, costACount);
        this.costB = costB;
        this.costBCount = Math.max(1, costBCount);
        this.potion = potion;
        this.mapStructureTag = mapStructureTag;
        this.mapDecoration = mapDecoration;
    }

    public static TradeDefinition fromJson(JsonObject obj) {
        String item = GsonHelper.getAsString(obj, "item", "minecraft:air");
        int count = GsonHelper.getAsInt(obj, "count", 1);
        int countMin = GsonHelper.getAsInt(obj, "count_min", count);
        int countMax = GsonHelper.getAsInt(obj, "count_max", countMin);

        int emeralds = GsonHelper.getAsInt(obj, "emeralds", 1);
        int emeraldsMin = GsonHelper.getAsInt(obj, "emeralds_min", emeralds);
        int emeraldsMax = GsonHelper.getAsInt(obj, "emeralds_max", emeraldsMin);

        int maxUses = GsonHelper.getAsInt(obj, "max_uses", 12);
        int xp = GsonHelper.getAsInt(obj, "xp", 2);
        float priceMultiplier = GsonHelper.getAsFloat(obj, "price_multiplier", 0.05F);
        String special = obj.has("special") ? GsonHelper.getAsString(obj, "special") : null;
        String costItem = obj.has("cost_item") ? GsonHelper.getAsString(obj, "cost_item") : null;
        int costItemCount = GsonHelper.getAsInt(obj, "cost_item_count", 1);

        String costA = obj.has("cost_a") ? GsonHelper.getAsString(obj, "cost_a") : null;
        int costACount = GsonHelper.getAsInt(obj, "cost_a_count", 1);
        String costB = obj.has("cost_b") ? GsonHelper.getAsString(obj, "cost_b") : null;
        int costBCount = GsonHelper.getAsInt(obj, "cost_b_count", 1);
        String potion = obj.has("potion") ? GsonHelper.getAsString(obj, "potion") : null;

        String mapStructureTag = obj.has("map_structure_tag") ? GsonHelper.getAsString(obj, "map_structure_tag") : null;
        String mapDecoration = obj.has("map_decoration") ? GsonHelper.getAsString(obj, "map_decoration") : null;

        return new TradeDefinition(
                item, countMin, countMax, emeraldsMin, emeraldsMax,
                maxUses, xp, priceMultiplier, special,
                costItem, costItemCount,
                costA, costACount, costB, costBCount,
                potion, mapStructureTag, mapDecoration
        );
    }

    public String getDeduplicationKey(String tradeType) {
        boolean isBuy = isBuyType(tradeType);
        if ("enchanted_book".equals(special)) {
            return (isBuy ? "BUY:" : "SELL:") + "enchanted_book";
        }
        if ("treasure_book".equals(special)) {
            return "BARTER:treasure_book";
        }
        if ("treasure_map".equals(special)) {
            return (isBuy ? "BUY:" : "SELL:") + "map:" + (mapDecoration != null ? mapDecoration : "generic");
        }
        return (isBuy ? "BUY:" : "SELL:") + item + (special != null ? ":" + special : "");
    }

    private static boolean isBuyType(String tradeType) {
        return IMerchantOfferExt.TYPE_BASIC_BUY.equals(tradeType)
                || IMerchantOfferExt.TYPE_RARE_BUY.equals(tradeType)
                || IMerchantOfferExt.TYPE_DAILY_DEAL.equals(tradeType);
    }

    @Nullable
    public MerchantOffer createOffer(Villager villager, RandomSource random, int level, String tradeType) {
        MerchantOffer offer = buildRawOffer(villager, random, tradeType);
        if (offer != null) {
            IMerchantOfferExt ext = (IMerchantOfferExt) offer;
            ext.livingmarket$setPinned(false);
            ext.livingmarket$setTradeLevel(level);
            ext.livingmarket$setTradeType(tradeType);
        }
        return offer;
    }

    @Nullable
    private MerchantOffer buildRawOffer(Villager villager, RandomSource random, String tradeType) {
        if (IMerchantOfferExt.TYPE_RARE_BARTER.equals(tradeType) || costA != null) {
            return createBarterOffer(random);
        }
        if ("enchanted_book".equals(special)) {
            return createEnchantedBookOffer(random);
        }
        if ("suspicious_stew".equals(special)) {
            return createSuspiciousStewOffer(random);
        }
        if ("tipped_arrow".equals(special)) {
            return createTippedArrowOffer(random);
        }
        if ("treasure_map".equals(special)) {
            return createTreasureMapOffer(villager, random);
        }

        Item resolvedItem = resolveItem(this.item);
        if (resolvedItem == null) {
            return null;
        }

        int count = Mth.nextInt(random, countMin, countMax);
        int emeralds = Mth.nextInt(random, emeraldsMin, emeraldsMax);

        if ("enchanted_item".equals(special)) {
            int enchantLevel = 5 + random.nextInt(15);
            ItemStack enchanted = EnchantmentHelper.enchantItem(random, new ItemStack(resolvedItem, count), enchantLevel, false);
            int totalEmeralds = Math.min(64, emeralds + Math.max(0, enchantLevel / 3));
            return new MerchantOffer(
                    new ItemStack(Items.EMERALD, totalEmeralds),
                    enchanted,
                    maxUses,
                    xp,
                    0.2F
            );
        }

        if ("firework_flight_3".equals(special)) {
            ItemStack rockets = createFlight3Fireworks(count);
            return new MerchantOffer(
                    new ItemStack(Items.EMERALD, Math.min(64, emeralds)),
                    rockets,
                    maxUses,
                    xp,
                    priceMultiplier
            );
        }

        boolean isBuy = isBuyType(tradeType);
        if (isBuy) {
            return new MerchantOffer(
                    new ItemStack(resolvedItem, Math.min(64, count)),
                    new ItemStack(Items.EMERALD, Math.min(64, emeralds)),
                    maxUses,
                    xp,
                    priceMultiplier
            );
        } else {
            ItemStack secondaryCost = ItemStack.EMPTY;
            if (costItem != null) {
                Item secondary = resolveItem(costItem);
                if (secondary != null) {
                    secondaryCost = new ItemStack(secondary, Math.min(64, costItemCount));
                }
            }
            return new MerchantOffer(
                    new ItemStack(Items.EMERALD, Math.min(64, emeralds)),
                    secondaryCost,
                    new ItemStack(resolvedItem, Math.min(64, count)),
                    maxUses,
                    xp,
                    priceMultiplier
            );
        }
    }

    @Nullable
    private MerchantOffer createBarterOffer(RandomSource random) {
        Item itemA = resolveItem(costA);
        if (itemA == null) {
            return null;
        }
        ItemStack stackA = new ItemStack(itemA, Math.min(64, costACount));
        ItemStack stackB = ItemStack.EMPTY;
        if (costB != null) {
            Item itemB = resolveItem(costB);
            if (itemB != null) {
                stackB = new ItemStack(itemB, Math.min(64, costBCount));
            }
        }

        ItemStack resultStack;
        int count = Mth.nextInt(random, countMin, countMax);

        if ("treasure_book".equals(special)) {
            Enchantment ench = TREASURE_BOOK_POOL[random.nextInt(TREASURE_BOOK_POOL.length)];
            int lvl = ench.getMaxLevel();
            resultStack = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ench, lvl));
        } else if ("firework_flight_3".equals(special)) {
            resultStack = createFlight3Fireworks(count);
        } else if ("tipped_arrow".equals(special)) {
            Potion resolvedPotion = resolvePotion(this.potion, random);
            if (resolvedPotion == null) {
                return null;
            }
            resultStack = PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW, Math.min(64, count)), resolvedPotion);
        } else {
            Item resultItem = resolveItem(this.item);
            if (resultItem == null) {
                return null;
            }
            resultStack = new ItemStack(resultItem, Math.min(64, count));
        }

        return new MerchantOffer(
                stackA,
                stackB,
                resultStack,
                maxUses,
                xp,
                0.0F
        );
    }

    private static ItemStack createFlight3Fireworks(int count) {
        ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET, Math.min(64, count));
        CompoundTag fw = stack.getOrCreateTagElement("Fireworks");
        fw.putByte("Flight", (byte) 3);
        return stack;
    }

    @Nullable
    private static Item resolveItem(@Nullable String idStr) {
        if (idStr == null || idStr.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        return (item == null || item == Items.AIR) ? null : item;
    }

    @Nullable
    private static Potion resolvePotion(@Nullable String potionStr, RandomSource random) {
        if (potionStr != null && !potionStr.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(potionStr);
            if (id != null) {
                Potion exact = BuiltInRegistries.POTION.getOptional(id).orElse(null);
                if (exact != null) {
                    return exact;
                }
            }
        }
        List<Potion> potions = BuiltInRegistries.POTION.stream()
                .filter(p -> !p.getEffects().isEmpty() && PotionBrewing.isBrewablePotion(p))
                .toList();
        return potions.isEmpty() ? null : potions.get(random.nextInt(potions.size()));
    }

    @Nullable
    private MerchantOffer createEnchantedBookOffer(RandomSource random) {
        List<Enchantment> tradeable = BuiltInRegistries.ENCHANTMENT.stream()
                .filter(Enchantment::isTradeable)
                .toList();
        if (tradeable.isEmpty()) {
            return null;
        }
        Enchantment enchantment = tradeable.get(random.nextInt(tradeable.size()));
        int level = Mth.nextInt(random, enchantment.getMinLevel(), enchantment.getMaxLevel());
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
        int emeraldCost = 2 + random.nextInt(5 + level * 10) + 3 * level;
        if (enchantment.isTreasureOnly()) {
            emeraldCost *= 2;
        }
        emeraldCost = Math.min(64, Math.max(1, emeraldCost));
        return new MerchantOffer(
                new ItemStack(Items.EMERALD, emeraldCost),
                new ItemStack(Items.BOOK),
                book,
                maxUses,
                xp,
                0.2F
        );
    }

    private MerchantOffer createSuspiciousStewOffer(RandomSource random) {
        MobEffect effect = STEW_EFFECTS[random.nextInt(STEW_EFFECTS.length)];
        int duration = 100 + random.nextInt(100);
        ItemStack stew = new ItemStack(Items.SUSPICIOUS_STEW, 1);
        SuspiciousStewItem.saveMobEffect(stew, effect, duration);
        int emeralds = Mth.nextInt(random, emeraldsMin, emeraldsMax);
        return new MerchantOffer(
                new ItemStack(Items.EMERALD, Math.min(64, emeralds)),
                stew,
                maxUses,
                xp,
                priceMultiplier
        );
    }

    @Nullable
    private MerchantOffer createTippedArrowOffer(RandomSource random) {
        Potion selectedPotion = resolvePotion(this.potion, random);
        if (selectedPotion == null) {
            return null;
        }
        int count = Mth.nextInt(random, countMin, countMax);
        int emeralds = Mth.nextInt(random, emeraldsMin, emeraldsMax);
        ItemStack result = PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW, count), selectedPotion);
        return new MerchantOffer(
                new ItemStack(Items.EMERALD, Math.min(64, emeralds)),
                new ItemStack(Items.ARROW, count),
                result,
                maxUses,
                xp,
                priceMultiplier
        );
    }

    @Nullable
    private MerchantOffer createTreasureMapOffer(Villager villager, RandomSource random) {
        if (!(villager.level() instanceof ServerLevel serverLevel) || mapStructureTag == null) {
            return null;
        }
        ResourceLocation tagId = ResourceLocation.tryParse(mapStructureTag);
        if (tagId == null) {
            return null;
        }
        TagKey<Structure> structureTag = TagKey.create(Registries.STRUCTURE, tagId);
        BlockPos pos = serverLevel.findNearestMapStructure(structureTag, villager.blockPosition(), 100, true);
        if (pos == null) {
            return null;
        }
        ItemStack mapStack = MapItem.create(serverLevel, pos.getX(), pos.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(serverLevel, mapStack);
        boolean isMansion = "MANSION".equalsIgnoreCase(mapDecoration);
        MapDecoration.Type decType = isMansion ? MapDecoration.Type.MANSION : MapDecoration.Type.MONUMENT;
        MapItemSavedData.addTargetDecoration(mapStack, pos, "+", decType);
        mapStack.setHoverName(Component.translatable(isMansion ? "filled_map.mansion" : "filled_map.monument"));

        int emeralds = Mth.nextInt(random, emeraldsMin, emeraldsMax);
        return new MerchantOffer(
                new ItemStack(Items.EMERALD, Math.min(64, emeralds)),
                new ItemStack(Items.COMPASS),
                mapStack,
                maxUses,
                xp,
                0.2F
        );
    }
}
