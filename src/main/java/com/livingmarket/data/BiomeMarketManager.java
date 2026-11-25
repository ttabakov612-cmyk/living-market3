package com.livingmarket.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.livingmarket.LivingMarketMod;
import com.livingmarket.logic.BiomeRegion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BiomeMarketManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final BiomeMarketManager INSTANCE = new BiomeMarketManager();

    private Map<BiomeRegion, BiomeMarketPool> pools = Collections.emptyMap();

    public static class BiomeMarketPool {
        public final List<TradeDefinition> dailyDeals = new ArrayList<>();
        public final List<TradeDefinition> regionalSells = new ArrayList<>();
    }

    private BiomeMarketManager() {
        super(GSON, "biome_markets");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<BiomeRegion, BiomeMarketPool> loaded = new EnumMap<>(BiomeRegion.class);

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), "biome_market");
                String regionStr = GsonHelper.getAsString(root, "region");
                BiomeRegion region = BiomeRegion.byId(regionStr);

                boolean replace = GsonHelper.getAsBoolean(root, "replace", false);
                BiomeMarketPool pool = loaded.computeIfAbsent(region, k -> new BiomeMarketPool());
                if (replace) {
                    pool.dailyDeals.clear();
                    pool.regionalSells.clear();
                }

                readList(root, "daily_deals", pool.dailyDeals);
                readList(root, "regional_sells", pool.regionalSells);
            } catch (Exception e) {
                LivingMarketMod.LOGGER.error("Failed to parse biome market from {}", fileId, e);
            }
        }

        this.pools = loaded;
        LivingMarketMod.LOGGER.info("Living Market 2.0 loaded biome markets for {} regions.", loaded.size());
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
    public BiomeMarketPool getPool(BiomeRegion region) {
        return pools.get(region);
    }
}
