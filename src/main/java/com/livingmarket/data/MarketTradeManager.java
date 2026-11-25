package com.livingmarket.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.livingmarket.LivingMarketMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketTradeManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final MarketTradeManager INSTANCE = new MarketTradeManager();

    private Map<ResourceLocation, ProfessionTable> tables = Collections.emptyMap();

    public static class LevelPool {
        public final List<TradeDefinition> basicBuy = new ArrayList<>();
        public final List<TradeDefinition> rareBuy = new ArrayList<>();
        public final List<TradeDefinition> sell = new ArrayList<>();
    }

    public static class ProfessionTable {
        public final Map<Integer, LevelPool> levels = new HashMap<>();
        public final List<TradeDefinition> rareBarters = new ArrayList<>();
    }

    private MarketTradeManager() {
        super(GSON, "market_trades");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ProfessionTable> loaded = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), "market_trade");
                String professionStr = GsonHelper.getAsString(root, "profession");
                ResourceLocation profId = ResourceLocation.tryParse(professionStr);
                if (profId == null) {
                    LivingMarketMod.LOGGER.warn("Invalid profession '{}' in {}", professionStr, fileId);
                    continue;
                }

                boolean replace = GsonHelper.getAsBoolean(root, "replace", false);
                ProfessionTable table = loaded.computeIfAbsent(profId, k -> new ProfessionTable());
                if (replace) {
                    table.levels.clear();
                    table.rareBarters.clear();
                }

                if (root.has("levels")) {
                    JsonObject levelsObj = GsonHelper.getAsJsonObject(root, "levels");
                    for (Map.Entry<String, JsonElement> lvlEntry : levelsObj.entrySet()) {
                        int level;
                        try {
                            level = Integer.parseInt(lvlEntry.getKey());
                        } catch (NumberFormatException ex) {
                            continue;
                        }
                        if (level < 1 || level > 5) {
                            continue;
                        }

                        JsonObject poolObj = GsonHelper.convertToJsonObject(lvlEntry.getValue(), "level_pool");
                        LevelPool pool = table.levels.computeIfAbsent(level, k -> new LevelPool());
                        readList(poolObj, "basic_buy", pool.basicBuy);
                        readList(poolObj, "rare_buy", pool.rareBuy);
                        readList(poolObj, "sell", pool.sell);
                    }
                }

                readList(root, "rare_barters", table.rareBarters);
            } catch (Exception e) {
                LivingMarketMod.LOGGER.error("Failed to parse market trades from {}", fileId, e);
            }
        }

        this.tables = loaded;
        LivingMarketMod.LOGGER.info("Living Market 2.0 loaded trade tables for {} professions.", loaded.size());
    }

    private static void readList(JsonObject parent, String key, List<TradeDefinition> target) {
        if (!parent.has(key)) {
            return;
        }
        JsonArray arr = GsonHelper.getAsJsonArray(parent, key);
        for (JsonElement el : arr) {
            target.add(TradeDefinition.fromJson(GsonHelper.convertToJsonObject(el, key + "_entry")));
        }
    }

    @Nullable
    public LevelPool getPool(VillagerProfession profession, int level) {
        ResourceLocation profId = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(profession);
        if (profId == null) {
            return null;
        }
        ProfessionTable table = tables.get(profId);
        return table != null ? table.levels.get(level) : null;
    }

    public List<TradeDefinition> getRareBarters(VillagerProfession profession) {
        ResourceLocation profId = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(profession);
        if (profId == null) {
            return Collections.emptyList();
        }
        ProfessionTable table = tables.get(profId);
        return table != null ? table.rareBarters : Collections.emptyList();
    }
}
