package com.livingmarket.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MarketConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_PINNED_TRADES;
    public static final ForgeConfigSpec.BooleanValue ENABLE_DAILY_REFRESH;
    public static final ForgeConfigSpec.IntValue DAILY_DEAL_PERCENT;
    public static final ForgeConfigSpec.IntValue REGIONAL_SELL_CHANCE_PERCENT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WORLD_PARTICLES;

    public static final ForgeConfigSpec.IntValue BARTER_CHANCE_LOW;
    public static final ForgeConfigSpec.IntValue BARTER_CHANCE_MID;
    public static final ForgeConfigSpec.IntValue BARTER_CHANCE_HIGH;
    public static final ForgeConfigSpec.IntValue BARTER_CHANCE_FULL;

    public static final ForgeConfigSpec.IntValue BASE_REROLL_COST;
    public static final ForgeConfigSpec.DoubleValue REROLL_COST_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_REROLL_COST;

    // 3.0 Village Life & Workshop settings
    public static final ForgeConfigSpec.BooleanValue ENABLE_VILLAGE_ROUTINE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MORNING_SERVICE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_FIELD_EVENTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_WORKSHOP_PROCESSING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SQUARE_TRADING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_EVENING_DINNER;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PLACEMENT_HINTS;
    public static final ForgeConfigSpec.IntValue MAX_FIELD_ACTIONS_PER_DAY;
    public static final ForgeConfigSpec.IntValue MAX_WORKSHOP_ACTIONS_PER_DAY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("trading");

        MAX_PINNED_TRADES = builder
                .comment("Maximum number of normal trades that can be pinned on a single villager at once.")
                .defineInRange("maxPinnedTrades", 2, 0, 15);

        ENABLE_DAILY_REFRESH = builder
                .comment("If true, unpinned normal trades, Daily Deals, and Rare Barters rotate each Minecraft day.")
                .define("enableDailyRefresh", true);

        DAILY_DEAL_PERCENT = builder
                .comment("Percentage of villagers in a village that receive a biome Daily Deal each morning (rounded, minimum 1 when 3+ villagers are nearby).")
                .defineInRange("dailyDealPercent", 30, 0, 100);

        REGIONAL_SELL_CHANCE_PERCENT = builder
                .comment("Chance (%) for a villager's SELL slot to draw from the current biome's regional specialties.")
                .defineInRange("regionalSellChancePercent", 25, 0, 100);

        ENABLE_WORLD_PARTICLES = builder
                .comment("If true, villagers with an active Daily Deal (gold sparkles) or Rare Barter (purple sparkles) emit subtle particles in the world.")
                .define("enableWorldParticles", true);

        builder.pop();

        builder.push("rare_barter");

        BARTER_CHANCE_LOW = builder
                .comment("Daily Rare Barter chance (%) for level 4-5 villagers when 1-3 distinct professions are within 48 blocks.")
                .defineInRange("chance1to3Professions", 4, 0, 100);

        BARTER_CHANCE_MID = builder
                .comment("Daily Rare Barter chance (%) for level 4-5 villagers when 4-6 distinct professions are within 48 blocks.")
                .defineInRange("chance4to6Professions", 8, 0, 100);

        BARTER_CHANCE_HIGH = builder
                .comment("Daily Rare Barter chance (%) for level 4-5 villagers when 7-10 distinct professions are within 48 blocks.")
                .defineInRange("chance7to10Professions", 14, 0, 100);

        BARTER_CHANCE_FULL = builder
                .comment("Daily Rare Barter chance (%) for level 4-5 villagers when 11+ distinct professions are within 48 blocks.")
                .defineInRange("chance11PlusProfessions", 22, 0, 100);

        builder.pop();

        builder.push("reroll");

        BASE_REROLL_COST = builder
                .comment("Emerald cost of the first manual reroll of the day (0 for free).")
                .defineInRange("baseRerollCost", 1, 0, 64);

        REROLL_COST_MULTIPLIER = builder
                .comment("Multiplier applied to the reroll cost after each reroll within the same day (e.g. 2.0 => 1, 2, 4, 8, 16, 32).")
                .defineInRange("rerollCostMultiplier", 2.0D, 1.0D, 10.0D);

        MAX_REROLL_COST = builder
                .comment("Maximum emerald cost cap for a single manual reroll.")
                .defineInRange("maxRerollCost", 32, 1, 64);

        builder.pop();

        builder.push("village_life");

        ENABLE_VILLAGE_ROUTINE = builder
                .comment("Master toggle for the 6-phase villager daily schedule and real profession work.")
                .define("enableVillageRoutine", true);

        ENABLE_MORNING_SERVICE = builder
                .comment("Phase 1 (06:00-07:30): Villagers gather near the Cleric / brewing stand (or bell) and receive healing.")
                .define("enableMorningService", true);

        ENABLE_FIELD_EVENTS = builder
                .comment("Phase 2 (07:30-10:30): Profession field tasks (fishing, shearing, beehives, golem repair, torch lighting, brick repair, etc.).")
                .define("enableFieldEvents", true);

        ENABLE_WORKSHOP_PROCESSING = builder
                .comment("Phase 3 & 5: Villagers deposit morning haul and process recipes/repairs in chests or barrels within 3 blocks of their job site.")
                .define("enableWorkshopProcessing", true);

        ENABLE_SQUARE_TRADING = builder
                .comment("Phase 4 (13:00-15:00): Villagers gather at the town square bell and visually trade goods with each other.")
                .define("enableSquareTrading", true);

        ENABLE_EVENING_DINNER = builder
                .comment("Phase 6 (17:00-18:00): Villagers head home early, eat their profession meal, and go to sleep safely before nightfall.")
                .define("enableEveningDinner", true);

        ENABLE_PLACEMENT_HINTS = builder
                .comment("Show a subtle Action Bar hint when placing a chest or barrel near an owned villager workstation.")
                .define("enablePlacementHints", true);

        MAX_FIELD_ACTIONS_PER_DAY = builder
                .comment("Maximum real field actions (fish caught, sheep sheared, torches placed, etc.) per villager per day.")
                .defineInRange("maxFieldActionsPerDay", 4, 1, 16);

        MAX_WORKSHOP_ACTIONS_PER_DAY = builder
                .comment("Maximum workshop chest processing/repair cycles per villager per day.")
                .defineInRange("maxWorkshopActionsPerDay", 6, 1, 32);

        builder.pop();

        SPEC = builder.build();
    }

    private MarketConfig() {}
}
