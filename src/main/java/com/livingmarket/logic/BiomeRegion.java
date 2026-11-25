package com.livingmarket.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;
import java.util.Optional;

public enum BiomeRegion {
    PLAINS_FOREST("plains_forest"),
    DESERT("desert"),
    SAVANNA("savanna"),
    TAIGA_MOUNTAINS("taiga_mountains"),
    SNOWY("snowy"),
    JUNGLE_LUSH("jungle_lush"),
    SWAMP("swamp");

    private static final TagKey<Biome> FORGE_DESERT = TagKey.create(Registries.BIOME, new ResourceLocation("forge", "is_desert"));
    private static final TagKey<Biome> FORGE_SNOWY = TagKey.create(Registries.BIOME, new ResourceLocation("forge", "is_snowy"));
    private static final TagKey<Biome> FORGE_SWAMP = TagKey.create(Registries.BIOME, new ResourceLocation("forge", "is_swamp"));
    private static final TagKey<Biome> FORGE_LUSH = TagKey.create(Registries.BIOME, new ResourceLocation("forge", "is_lush"));

    private final String id;

    BiomeRegion(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public String getTranslationKey() {
        return "biome_region.livingmarket." + this.id;
    }

    public static BiomeRegion byId(String id) {
        if (id != null) {
            for (BiomeRegion region : values()) {
                if (region.id.equalsIgnoreCase(id)) {
                    return region;
                }
            }
        }
        return PLAINS_FOREST;
    }

    public static BiomeRegion fromLevelAndPos(Level level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        String path = "";
        Optional<ResourceKey<Biome>> keyOpt = holder.unwrapKey();
        if (keyOpt.isPresent()) {
            path = keyOpt.get().location().getPath().toLowerCase(Locale.ROOT);
        }

        // 1. Swamp & Mangroves
        if (holder.is(FORGE_SWAMP) || path.contains("swamp") || path.contains("mangrove") || path.contains("marsh") || path.contains("bayou")) {
            return SWAMP;
        }

        // 2. Jungle & Lush Caves
        if (holder.is(BiomeTags.IS_JUNGLE) || holder.is(FORGE_LUSH) || path.contains("jungle") || path.contains("bamboo") || path.contains("lush")) {
            return JUNGLE_LUSH;
        }

        // 3. Desert & Badlands
        if (holder.is(FORGE_DESERT) || holder.is(BiomeTags.IS_BADLANDS) || path.contains("desert") || path.contains("badlands") || path.contains("mesa")) {
            return DESERT;
        }

        // 4. Savanna
        if (holder.is(BiomeTags.IS_SAVANNA) || path.contains("savanna")) {
            return SAVANNA;
        }

        // 5. Snowy & Frozen regions (checked before taiga/mountains so snowy taiga & frozen peaks go here)
        Biome biome = holder.value();
        if (holder.is(FORGE_SNOWY)
                || biome.getBaseTemperature() <= 0.05F
                || path.contains("snow")
                || path.contains("frozen")
                || path.contains("ice")
                || path.contains("tundra")
                || path.contains("glacier")) {
            return SNOWY;
        }

        // 6. Taiga & Mountains
        if (holder.is(BiomeTags.IS_TAIGA)
                || holder.is(BiomeTags.IS_MOUNTAIN)
                || path.contains("taiga")
                || path.contains("mountain")
                || path.contains("peak")
                || path.contains("windswept")
                || path.contains("spruce")
                || path.contains("pine")) {
            return TAIGA_MOUNTAINS;
        }

        // 7. Default: Plains & Forests
        return PLAINS_FOREST;
    }
}
