package com.livingmarket.routine;

import com.livingmarket.config.MarketConfig;
import com.livingmarket.logic.VillageContextHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public final class VillagerRoutineController {
    private static final int TICK_INTERVAL = 20;

    private VillagerRoutineController() {}

    public static void clearRoutineHeldItem(Villager villager, VillagerRoutineData data) {
        if (data.isHoldingRoutineItem()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            data.setHoldingRoutineItem(false);
        }
    }

    private static void holdRoutineItem(Villager villager, VillagerRoutineData data, ItemStack stack) {
        // Don't fight with vanilla ShowTradesToPlayer if a nearby player is holding an emerald
        Player nearest = villager.level().getNearestPlayer(villager, 6.0D);
        if (nearest != null && (nearest.getMainHandItem().is(Items.EMERALD) || nearest.getOffhandItem().is(Items.EMERALD))) {
            clearRoutineHeldItem(villager, data);
            return;
        }
        villager.setItemSlot(EquipmentSlot.MAINHAND, stack);
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        data.setHoldingRoutineItem(true);
    }

    public static void tick(Villager villager, VillagerRoutineData data) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (!MarketConfig.ENABLE_VILLAGE_ROUTINE.get()) {
            clearRoutineHeldItem(villager, data);
            return;
        }
        if (villager.isBaby() || villager.isSleeping() || villager.isTrading()) {
            clearRoutineHeldItem(villager, data);
            return;
        }
        VillagerProfession prof = villager.getVillagerData().getProfession();
        if (!VillageContextHelper.isWorkingProfession(prof)) {
            return;
        }

        // Only evaluate every 20 ticks (1 second), staggered by entity id for zero lag spikes
        if ((villager.tickCount + villager.getId()) % TICK_INTERVAL != 0) {
            return;
        }

        // Only run when a player is within 48 blocks so distant chunks have zero overhead
        if (!level.hasNearbyAlivePlayer(villager.getX(), villager.getY(), villager.getZ(), 48.0D)) {
            return;
        }

        data.decrementCooldown(TICK_INTERVAL);
        data.decrementHintHoldTicks(TICK_INTERVAL);

        long currentDay = level.getDayTime() / 24000L;
        data.checkNewDay(currentDay);

        // Safety first: yield immediately during raids, attacks, or nearby monsters
        if (isInDanger(level, villager)) {
            clearRoutineHeldItem(villager, data);
            return;
        }

        DayPhase phase = DayPhase.fromDayTime(level.getDayTime());
        if (data.getLastPhase() != phase) {
            clearRoutineHeldItem(villager, data);
            data.setLastPhase(phase);
        }

        boolean badWeather = level.isRaining() || level.isThundering();

        switch (phase) {
            case MORNING_SERVICE -> {
                if (MarketConfig.ENABLE_MORNING_SERVICE.get() && !badWeather) {
                    handleMorningService(level, villager, prof, data);
                }
            }
            case FIELD_EVENT -> {
                if (MarketConfig.ENABLE_FIELD_EVENTS.get() && !badWeather) {
                    handleFieldEvent(level, villager, prof, data);
                }
            }
            case WORKSTATION_MORNING, WORKSTATION_AFTERNOON -> {
                if (MarketConfig.ENABLE_WORKSHOP_PROCESSING.get()) {
                    handleWorkstationPhase(level, villager, prof, data);
                }
            }
            case TOWN_SQUARE -> {
                if (MarketConfig.ENABLE_SQUARE_TRADING.get() && !badWeather) {
                    handleTownSquare(level, villager, prof, data);
                }
            }
            case EVENING_DINNER -> {
                if (MarketConfig.ENABLE_EVENING_DINNER.get()) {
                    handleEveningDinner(level, villager, prof, data);
                }
            }
            case NIGHT_REST -> clearRoutineHeldItem(villager, data);
        }
    }

    private static boolean isInDanger(ServerLevel level, Villager villager) {
        if (level.isRaided(villager.blockPosition())) {
            return true;
        }
        if (villager.getLastHurtByMob() != null && villager.tickCount - villager.getLastHurtByMobTimestamp() < 120) {
            return true;
        }
        List<Monster> threats = level.getEntitiesOfClass(Monster.class, villager.getBoundingBox().inflate(10.0D), Monster::isAlive);
        return !threats.isEmpty();
    }

    // =========================================================================
    // PHASE 1: MORNING SERVICE (06:00 - 07:30)
    // =========================================================================

    private static void handleMorningService(ServerLevel level, Villager villager, VillagerProfession prof, VillagerRoutineData data) {
        BlockPos gatherPos = findServiceLocation(level, villager);
        if (gatherPos == null) {
            return;
        }

        if (prof == VillagerProfession.CLERIC) {
            if (villager.blockPosition().distSqr(gatherPos) > 9.0D) {
                villager.getNavigation().moveTo(gatherPos.getX() + 0.5D, gatherPos.getY(), gatherPos.getZ() + 0.5D, 0.55D);
                return;
            }
            holdRoutineItem(villager, data, new ItemStack(Items.AMETHYST_SHARD));
            if (data.getActionCooldown() <= 0) {
                data.setActionCooldown(160);
                // Heal nearby villagers, golems, and players
                List<Villager> congregation = level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(10.0D), Villager::isAlive);
                for (Villager v : congregation) {
                    if (v.getHealth() < v.getMaxHealth()) {
                        v.heal(6.0F);
                    }
                    v.removeEffect(MobEffects.POISON);
                    v.removeEffect(MobEffects.WEAKNESS);
                }
                List<IronGolem> golems = level.getEntitiesOfClass(IronGolem.class, villager.getBoundingBox().inflate(12.0D), IronGolem::isAlive);
                for (IronGolem g : golems) {
                    if (g.getHealth() < g.getMaxHealth()) {
                        g.heal(10.0F);
                    }
                }
                List<Player> players = level.getEntitiesOfClass(Player.class, villager.getBoundingBox().inflate(10.0D), Player::isAlive);
                for (Player p : players) {
                    if (p.getHealth() < p.getMaxHealth()) {
                        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
                    }
                    p.removeEffect(MobEffects.POISON);
                    p.removeEffect(MobEffects.WEAKNESS);
                }
                level.sendParticles(ParticleTypes.END_ROD, villager.getX(), villager.getY() + 2.2D, villager.getZ(), 8, 1.5D, 0.4D, 1.5D, 0.02D);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.5D, villager.getZ(), 10, 2.5D, 0.5D, 2.5D, 0.02D);
                level.playSound(null, villager.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9F, 1.1F);
            }
        } else {
            double distSq = villager.blockPosition().distSqr(gatherPos);
            if (distSq > 25.0D) {
                villager.getNavigation().moveTo(gatherPos.getX() + 0.5D, gatherPos.getY(), gatherPos.getZ() + 0.5D, 0.55D);
            } else {
                villager.getLookControl().setLookAt(gatherPos.getX() + 0.5D, gatherPos.getY() + 1.2D, gatherPos.getZ() + 0.5D);
                if (!data.hasAttendedServiceToday() && data.getActionCooldown() <= 0) {
                    data.setAttendedServiceToday(true);
                    data.setActionCooldown(200);
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 2.0D, villager.getZ(), 3, 0.25D, 0.2D, 0.25D, 0.01D);
                }
            }
        }
    }

    @Nullable
    private static BlockPos findServiceLocation(ServerLevel level, Villager villager) {
        // 1. Prefer an active Cleric within 48 blocks
        List<Villager> clerics = level.getEntitiesOfClass(
                Villager.class,
                villager.getBoundingBox().inflate(48.0D),
                v -> v.isAlive() && v.getVillagerData().getProfession() == VillagerProfession.CLERIC
        );
        if (!clerics.isEmpty()) {
            Villager cleric = clerics.get(0);
            BlockPos clericSite = WorkshopHandler.getJobSitePos(cleric);
            return clericSite != null ? clericSite : cleric.blockPosition();
        }
        // 2. Fallback to village meeting point (bell)
        return getMeetingPoint(villager);
    }

    // =========================================================================
    // PHASE 2: PROFESSION FIELD EVENTS (07:30 - 10:30)
    // =========================================================================

    private static void handleFieldEvent(ServerLevel level, Villager villager, VillagerProfession prof, VillagerRoutineData data) {
        int maxField = MarketConfig.MAX_FIELD_ACTIONS_PER_DAY.get();

        if (prof == VillagerProfession.FISHERMAN) {
            handleFishermanField(level, villager, data, maxField);
        } else if (prof == VillagerProfession.WEAPONSMITH) {
            handleWeaponsmithField(level, villager, data);
        } else if (prof == VillagerProfession.ARMORER || prof == VillagerProfession.TOOLSMITH) {
            handleSmithGolemField(level, villager, prof, data, maxField);
        } else if (prof == VillagerProfession.SHEPHERD) {
            handleShepherdField(level, villager, data, maxField);
        } else if (prof == VillagerProfession.FARMER) {
            handleFarmerField(level, villager, data, maxField);
        } else if (prof == VillagerProfession.BUTCHER) {
            handleButcherField(level, villager, data, maxField);
        } else if (prof == VillagerProfession.MASON) {
            handleMasonField(level, villager, data, maxField);
        } else if (prof == VillagerProfession.CARTOGRAPHER) {
            handleCartographerField(level, villager, data, maxField);
        } else if (prof == VillagerProfession.FLETCHER) {
            handleFletcherField(level, villager, data);
        } else if (prof == VillagerProfession.LIBRARIAN) {
            handleLibrarianField(level, villager, data);
        } else if (prof == VillagerProfession.LEATHERWORKER) {
            handleLeatherworkerField(level, villager, data);
        } else if (prof == VillagerProfession.CLERIC) {
            handleClericField(level, villager, data, maxField);
        }
    }

    private static void handleFishermanField(ServerLevel level, Villager villager, VillagerRoutineData data, int maxField) {
        BlockPos shore = data.getCachedTargetPos();
        if (shore == null || !isSafeWaterShore(level, shore)) {
            shore = findNearestSafeShore(level, villager.blockPosition(), 18);
            data.setCachedTargetPos(shore);
        }
        if (shore == null) {
            // No lake nearby: tidy gear at barrel
            holdRoutineItem(villager, data, new ItemStack(Items.FISHING_ROD));
            return;
        }

        if (villager.blockPosition().distSqr(shore) > 4.0D) {
            villager.getNavigation().moveTo(shore.getX() + 0.5D, shore.getY(), shore.getZ() + 0.5D, 0.55D);
            holdRoutineItem(villager, data, new ItemStack(Items.FISHING_ROD));
            return;
        }

        BlockPos waterPos = findAdjacentWater(level, shore);
        if (waterPos != null) {
            villager.getLookControl().setLookAt(waterPos.getX() + 0.5D, waterPos.getY() + 0.5D, waterPos.getZ() + 0.5D);
        }
        holdRoutineItem(villager, data, new ItemStack(Items.FISHING_ROD));

        if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(240 + villager.getRandom().nextInt(160));
            BlockPos splashAt = waterPos != null ? waterPos : shore;
            level.sendParticles(ParticleTypes.SPLASH, splashAt.getX() + 0.5D, splashAt.getY() + 1.0D, splashAt.getZ() + 0.5D, 10, 0.3D, 0.1D, 0.3D, 0.05D);
            level.sendParticles(ParticleTypes.FISHING, splashAt.getX() + 0.5D, splashAt.getY() + 1.0D, splashAt.getZ() + 0.5D, 5, 0.25D, 0.1D, 0.25D, 0.02D);
            level.playSound(null, splashAt, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.55F, 1.0F);

            if (data.getFieldActionsToday() < maxField) {
                // Only add to haul if barrel isn't already completely full
                WorkshopHandler.ContainerRef ref = WorkshopHandler.findWorkshopContainer(level, WorkshopHandler.getJobSitePos(villager));
                if (ref == null || WorkshopHandler.hasAnySpace(ref.container())) {
                    ItemStack catchStack = rollFishingCatch(villager.getRandom());
                    data.addToHaul(catchStack);
                    data.incrementFieldActions();
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 2.0D, villager.getZ(), 3, 0.2D, 0.2D, 0.2D, 0.01D);
                }
            }
        }
    }

    private static ItemStack rollFishingCatch(RandomSource rand) {
        int r = rand.nextInt(100);
        if (r < 42) return new ItemStack(Items.COD);
        if (r < 68) return new ItemStack(Items.SALMON);
        if (r < 82) return new ItemStack(Items.KELP, 2);
        if (r < 90) return new ItemStack(Items.LILY_PAD);
        if (r < 95) return new ItemStack(Items.PUFFERFISH);
        if (r < 98) return new ItemStack(Items.TROPICAL_FISH);
        return new ItemStack(Items.NAUTILUS_SHELL);
    }

    private static void handleWeaponsmithField(ServerLevel level, Villager villager, VillagerRoutineData data) {
        BlockPos spot = data.getCachedTargetPos();
        if (spot == null) {
            spot = findSafeLavaViewingSpot(level, villager.blockPosition(), 16);
            if (spot == null) {
                spot = findNearestBlock(level, villager.blockPosition(), 16, s -> s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.CAMPFIRE) || s.is(Blocks.GRINDSTONE));
            }
            data.setCachedTargetPos(spot);
        }
        holdRoutineItem(villager, data, new ItemStack(Items.IRON_SWORD));
        if (spot == null) {
            return;
        }
        if (villager.blockPosition().distSqr(spot) > 6.0D) {
            villager.getNavigation().moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.55D);
            return;
        }
        villager.getLookControl().setLookAt(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D);
        if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(180 + villager.getRandom().nextInt(120));
            level.sendParticles(ParticleTypes.LAVA, villager.getX(), villager.getY() + 1.2D, villager.getZ(), 4, 0.35D, 0.2D, 0.35D, 0.02D);
            level.sendParticles(ParticleTypes.SMOKE, villager.getX(), villager.getY() + 1.3D, villager.getZ(), 5, 0.25D, 0.2D, 0.25D, 0.01D);
            level.playSound(null, villager.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.4F, 1.2F);
        }
    }

    private static void handleSmithGolemField(ServerLevel level, Villager villager, VillagerProfession prof, VillagerRoutineData data, int maxField) {
        // Priority: repair any hurt Iron Golem nearby!
        List<IronGolem> hurtGolems = level.getEntitiesOfClass(
                IronGolem.class,
                villager.getBoundingBox().inflate(28.0D),
                g -> g.isAlive() && g.getHealth() < g.getMaxHealth() - 1.0F
        );
        if (!hurtGolems.isEmpty() && data.getFieldActionsToday() < maxField) {
            IronGolem golem = hurtGolems.get(0);
            holdRoutineItem(villager, data, new ItemStack(Items.IRON_INGOT));
            if (villager.distanceToSqr(golem) > 9.0D) {
                villager.getNavigation().moveTo(golem, 0.6D);
                return;
            }
            villager.getLookControl().setLookAt(golem, 30.0F, 30.0F);
            if (data.getActionCooldown() <= 0) {
                data.setActionCooldown(200);
                golem.heal(25.0F);
                data.incrementFieldActions();
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, golem.getX(), golem.getY() + 1.6D, golem.getZ(), 8, 0.5D, 0.5D, 0.5D, 0.02D);
                level.playSound(null, golem.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.NEUTRAL, 0.9F, 1.0F);
            }
            return;
        }

        // Otherwise ambient forge activity
        holdRoutineItem(villager, data, WorkshopHandler.getProfessionProp(prof));
        BlockPos forge = WorkshopHandler.getJobSitePos(villager);
        if (forge != null && villager.blockPosition().distSqr(forge) > 16.0D) {
            villager.getNavigation().moveTo(forge.getX() + 0.5D, forge.getY(), forge.getZ() + 0.5D, 0.5D);
        } else if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(220);
            level.sendParticles(ParticleTypes.SMOKE, villager.getX(), villager.getY() + 1.3D, villager.getZ(), 4, 0.25D, 0.2D, 0.25D, 0.01D);
            level.playSound(null, villager.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.3F, 1.25F);
        }
    }

    private static void handleShepherdField(ServerLevel level, Villager villager, VillagerRoutineData data, int maxField) {
        holdRoutineItem(villager, data, new ItemStack(Items.SHEARS));
        if (data.getFieldActionsToday() >= maxField) {
            return;
        }
        List<Sheep> sheepList = level.getEntitiesOfClass(
                Sheep.class,
                villager.getBoundingBox().inflate(24.0D),
                s -> s.isAlive() && !s.isBaby() && !s.isSheared()
        );
        if (sheepList.isEmpty()) {
            return;
        }
        Sheep sheep = sheepList.get(0);
        if (villager.distanceToSqr(sheep) > 6.25D) {
            villager.getNavigation().moveTo(sheep, 0.55D);
            return;
        }
        villager.getLookControl().setLookAt(sheep, 30.0F, 30.0F);
        if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(160);
            sheep.setSheared(true);
            Item wool = WorkshopHandler.getWoolForColor(sheep.getColor());
            int count = 1 + villager.getRandom().nextInt(2);
            data.addToHaul(new ItemStack(wool, count));
            data.incrementFieldActions();
            level.playSound(null, sheep.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 0.85F, 1.0F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sheep.getX(), sheep.getY() + 1.0D, sheep.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.01D);
        }
    }

    private static void handleFarmerField(ServerLevel level, Villager villager, VillagerRoutineData data, int maxField) {
        if (data.getFieldActionsToday() >= maxField) {
            return;
        }
        BlockPos hive = data.getCachedTargetPos();
        if (hive == null || !isFullBeehive(level, hive)) {
            hive = findNearestBlock(level, villager.blockPosition(), 20, s -> s.getBlock() instanceof BeehiveBlock && s.getValue(BeehiveBlock.HONEY_LEVEL) >= 5);
            data.setCachedTargetPos(hive);
        }
        if (hive == null) {
            return;
        }
        holdRoutineItem(villager, data, new ItemStack(Items.SHEARS));
        if (villager.blockPosition().distSqr(hive) > 9.0D) {
            villager.getNavigation().moveTo(hive.getX() + 0.5D, hive.getY(), hive.getZ() + 0.5D, 0.55D);
            return;
        }
        villager.getLookControl().setLookAt(hive.getX() + 0.5D, hive.getY() + 0.5D, hive.getZ() + 0.5D);
        if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(180);
            BlockState state = level.getBlockState(hive);
            if (state.getBlock() instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
                level.setBlock(hive, state.setValue(BeehiveBlock.HONEY_LEVEL, 0), 3);
                ItemStack harvest = villager.getRandom().nextBoolean()
                        ? new ItemStack(Items.HONEYCOMB, 2)
                        : new ItemStack(Items.HONEY_BOTTLE, 1);
                data.addToHaul(harvest);
                data.incrementFieldActions();
                data.setCachedTargetPos(null);
                level.playSound(null, hive, SoundEvents.BEEHIVE_SHEAR, SoundSource.NEUTRAL, 0.85F, 1.0F);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, hive.getX() + 0.5D, hive.getY() + 1.0D, hive.getZ() + 0.5D, 5, 0.3D, 0.3D, 0.3D, 0.01D);
            }
        }
    }

    private static void handleButcherField(ServerLevel level, Villager villager, VillagerRoutineData data, int maxField) {
        holdRoutineItem(villager, data, new ItemStack(Items.WHEAT));
        List<Animal> animals = level.getEntitiesOfClass(
                Animal.class,
                villager.getBoundingBox().inflate(22.0D),
                a -> a.isAlive() && !a.isBaby()
        );
        if (animals.isEmpty()) {
            return;
        }
        // Cap pen population at 10 animals to prevent lag
        if (animals.size() >= 10 || data.getFieldActionsToday() >= maxField) {
            Animal target = animals.get(0);
            if (villager.distanceToSqr(target) > 16.0D) {
                villager.getNavigation().moveTo(target, 0.5D);
            }
            return;
        }
        WorkshopHandler.ContainerRef ref = WorkshopHandler.findWorkshopContainer(level, WorkshopHandler.getJobSitePos(villager));
        if (ref == null) {
            return;
        }
        Container c = ref.container();
        for (Animal animal : animals) {
            if (animal.getAge() == 0 && animal.canFallInLove()) {
                Item feedItem = findFeedItemForAnimal(c, animal);
                if (feedItem != null) {
                    holdRoutineItem(villager, data, new ItemStack(feedItem));
                    if (villager.distanceToSqr(animal) > 9.0D) {
                        villager.getNavigation().moveTo(animal, 0.55D);
                        return;
                    }
                    if (data.getActionCooldown() <= 0) {
                        data.setActionCooldown(160);
                        WorkshopHandler.consumeItem(c, feedItem, 1);
                        c.setChanged();
                        animal.setInLove(null);
                        data.incrementFieldActions();
                        level.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + 1.0D, animal.getZ(), 4, 0.3D, 0.3D, 0.3D, 0.02D);
                    }
                    return;
                }
            }
        }
    }

    @Nullable
    private static Item findFeedItemForAnimal(Container c, Animal animal) {
        Item[] candidates = new Item[]{Items.WHEAT, Items.CARROT, Items.POTATO, Items.WHEAT_SEEDS, Items.BEETROOT};
        for (Item item : candidates) {
            if (WorkshopHandler.countItem(c, item) >= 1 && animal.isFood(new ItemStack(item))) {
                return item;
            }
        }
        return null;
    }

    private static void handleMasonField(ServerLevel level, Villager villager, VillagerRoutineData data, int maxField) {
        holdRoutineItem(villager, data, new ItemStack(Items.STONE_PICKAXE));
        if (data.getFieldActionsToday() >= maxField) {
            return;
        }
        BlockPos cracked = data.getCachedTargetPos();
        if (cracked == null || !isCrackedBrick(level.getBlockState(cracked))) {
            cracked = findNearestBlock(level, villager.blockPosition(), 20, VillagerRoutineController::isCrackedBrick);
            data.setCachedTargetPos(cracked);
        }
        if (cracked == null) {
            return;
        }
        if (villager.blockPosition().distSqr(cracked) > 9.0D) {
            villager.getNavigation().moveTo(cracked.getX() + 0.5D, cracked.getY(), cracked.getZ() + 0.5D, 0.55D);
            return;
        }
        villager.getLookControl().setLookAt(cracked.getX() + 0.5D, cracked.getY() + 0.5D, cracked.getZ() + 0.5D);
        if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(180);
            BlockState oldState = level.getBlockState(cracked);
            if (oldState.is(Blocks.CRACKED_STONE_BRICKS)) {
                level.setBlock(cracked, Blocks.STONE_BRICKS.defaultBlockState(), 3);
            } else if (oldState.is(Blocks.CRACKED_DEEPSLATE_BRICKS)) {
                level.setBlock(cracked, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
            }
            data.incrementFieldActions();
            data.setCachedTargetPos(null);
            level.playSound(null, cracked, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.85F, 1.05F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, cracked.getX() + 0.5D, cracked.getY() + 0.8D, cracked.getZ() + 0.5D, 5, 0.3D, 0.3D, 0.3D, 0.02D);
        }
    }

    private static void handleCartographerField(ServerLevel level, Villager villager, VillagerRoutineData data, int maxField) {
        WorkshopHandler.ContainerRef ref = WorkshopHandler.findWorkshopContainer(level, WorkshopHandler.getJobSitePos(villager));
        boolean hasTorches = ref != null && WorkshopHandler.countItem(ref.container(), Items.TORCH) >= 1 && data.getFieldActionsToday() < maxField;

        if (hasTorches) {
            BlockPos darkSpot = data.getCachedTargetPos();
            if (darkSpot == null || !isSafeTorchSpot(level, darkSpot)) {
                darkSpot = findDarkSpotNearVillage(level, villager.blockPosition(), 24);
                data.setCachedTargetPos(darkSpot);
            }
            if (darkSpot != null) {
                holdRoutineItem(villager, data, new ItemStack(Items.TORCH));
                if (villager.blockPosition().distSqr(darkSpot) > 9.0D) {
                    villager.getNavigation().moveTo(darkSpot.getX() + 0.5D, darkSpot.getY(), darkSpot.getZ() + 0.5D, 0.58D);
                    return;
                }
                if (data.getActionCooldown() <= 0 && isSafeTorchSpot(level, darkSpot)) {
                    data.setActionCooldown(180);
                    if (WorkshopHandler.consumeItem(ref.container(), Items.TORCH, 1)) {
                        ref.container().setChanged();
                        level.setBlock(darkSpot, Blocks.TORCH.defaultBlockState(), 3);
                        data.incrementFieldActions();
                        data.setCachedTargetPos(null);
                        level.playSound(null, darkSpot, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.85F, 1.0F);
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, darkSpot.getX() + 0.5D, darkSpot.getY() + 0.7D, darkSpot.getZ() + 0.5D, 4, 0.2D, 0.2D, 0.2D, 0.01D);
                    }
                }
                return;
            }
        }

        // Patrol village perimeter with spyglass & map
        holdRoutineItem(villager, data, villager.getRandom().nextBoolean() ? new ItemStack(Items.SPYGLASS) : new ItemStack(Items.FILLED_MAP));
        BlockPos patrol = data.getCachedTargetPos();
        if (patrol == null || villager.blockPosition().distSqr(patrol) < 9.0D) {
            BlockPos anchor = WorkshopHandler.getJobSitePos(villager);
            if (anchor == null) anchor = villager.blockPosition();
            RandomSource rand = villager.getRandom();
            int dx = rand.nextInt(33) - 16;
            int dz = rand.nextInt(33) - 16;
            patrol = anchor.offset(dx, 0, dz);
            data.setCachedTargetPos(patrol);
            if (data.getActionCooldown() <= 0) {
                data.setActionCooldown(160);
                level.playSound(null, villager.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.5F, 1.0F);
            }
        } else {
            villager.getNavigation().moveTo(patrol.getX() + 0.5D, patrol.getY(), patrol.getZ() + 0.5D, 0.52D);
        }
    }

    private static void handleFletcherField(ServerLevel level, Villager villager, VillagerRoutineData data) {
        holdRoutineItem(villager, data, new ItemStack(Items.BOW));
        BlockPos target = data.getCachedTargetPos();
        if (target == null || !level.getBlockState(target).is(Blocks.TARGET)) {
            target = findNearestBlock(level, villager.blockPosition(), 20, s -> s.is(Blocks.TARGET));
            data.setCachedTargetPos(target);
        }
        if (target == null) {
            return;
        }
        double distSq = villager.blockPosition().distSqr(target);
        if (distSq > 64.0D) {
            villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 0.55D);
            return;
        }
        villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(160 + villager.getRandom().nextInt(100));
            level.playSound(null, villager.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 0.6F, 1.05F);
            level.sendParticles(ParticleTypes.CRIT, target.getX() + 0.5D, target.getY() + 0.6D, target.getZ() + 0.5D, 6, 0.3D, 0.3D, 0.3D, 0.05D);
        }
    }

    private static void handleLibrarianField(ServerLevel level, Villager villager, VillagerRoutineData data) {
        holdRoutineItem(villager, data, new ItemStack(Items.WRITABLE_BOOK));
        BlockPos shelf = data.getCachedTargetPos();
        if (shelf == null || !(level.getBlockState(shelf).is(Blocks.BOOKSHELF) || level.getBlockState(shelf).is(Blocks.CHISELED_BOOKSHELF))) {
            shelf = findNearestBlock(level, villager.blockPosition(), 18, s -> s.is(Blocks.BOOKSHELF) || s.is(Blocks.CHISELED_BOOKSHELF));
            data.setCachedTargetPos(shelf);
        }
        if (shelf == null) {
            return;
        }
        if (villager.blockPosition().distSqr(shelf) > 9.0D) {
            villager.getNavigation().moveTo(shelf.getX() + 0.5D, shelf.getY(), shelf.getZ() + 0.5D, 0.5D);
            return;
        }
        villager.getLookControl().setLookAt(shelf.getX() + 0.5D, shelf.getY() + 0.8D, shelf.getZ() + 0.5D);
        if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(180);
            level.sendParticles(ParticleTypes.ENCHANT, shelf.getX() + 0.5D, shelf.getY() + 1.0D, shelf.getZ() + 0.5D, 8, 0.4D, 0.4D, 0.4D, 0.2D);
            level.playSound(null, shelf, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.55F, 1.0F);
        }
    }

    private static void handleLeatherworkerField(ServerLevel level, Villager villager, VillagerRoutineData data) {
        holdRoutineItem(villager, data, new ItemStack(Items.LEATHER));
        BlockPos jobSite = WorkshopHandler.getJobSitePos(villager);
        if (jobSite == null) {
            return;
        }
        if (villager.blockPosition().distSqr(jobSite) > 6.25D) {
            villager.getNavigation().moveTo(jobSite.getX() + 0.5D, jobSite.getY(), jobSite.getZ() + 0.5D, 0.55D);
            return;
        }
        villager.getLookControl().setLookAt(jobSite.getX() + 0.5D, jobSite.getY() + 0.5D, jobSite.getZ() + 0.5D);
        if (data.getActionCooldown() <= 0) {
            data.setActionCooldown(200);
            level.sendParticles(ParticleTypes.SPLASH, jobSite.getX() + 0.5D, jobSite.getY() + 1.0D, jobSite.getZ() + 0.5D, 6, 0.2D, 0.1D, 0.2D, 0.02D);
            level.playSound(null, jobSite, SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 0.45F, 1.0F);
        }
    }

    private static void handleClericField(ServerLevel level, Villager villager, VillagerRoutineData data, int maxField) {
        holdRoutineItem(villager, data, new ItemStack(Items.AMETHYST_SHARD));
        if (data.getFieldActionsToday() >= maxField) {
            return;
        }
        List<Villager> hurt = level.getEntitiesOfClass(
                Villager.class,
                villager.getBoundingBox().inflate(24.0D),
                v -> v.isAlive() && v != villager && v.getHealth() < v.getMaxHealth() - 1.0F
        );
        if (!hurt.isEmpty()) {
            Villager patient = hurt.get(0);
            if (villager.distanceToSqr(patient) > 9.0D) {
                villager.getNavigation().moveTo(patient, 0.58D);
                return;
            }
            if (data.getActionCooldown() <= 0) {
                data.setActionCooldown(160);
                patient.heal(8.0F);
                data.incrementFieldActions();
                level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + 1.8D, patient.getZ(), 5, 0.3D, 0.3D, 0.3D, 0.02D);
                level.playSound(null, patient.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.7F, 1.2F);
            }
        }
    }

    // =========================================================================
    // PHASE 3 & 5: WORKSTATION & WORKSHOP CHEST PROCESSING
    // =========================================================================

    private static void handleWorkstationPhase(ServerLevel level, Villager villager, VillagerProfession prof, VillagerRoutineData data) {
        BlockPos jobSite = WorkshopHandler.getJobSitePos(villager);
        if (jobSite == null) {
            return;
        }

        // If currently showing a missing-ingredient silent hint, keep holding it and looking at nearby player
        if (data.getHintHoldTicks() > 0 && data.getMissingHintItem() != null) {
            holdRoutineItem(villager, data, new ItemStack(data.getMissingHintItem()));
            Player nearPlayer = level.getNearestPlayer(villager, 8.0D);
            if (nearPlayer != null) {
                villager.getLookControl().setLookAt(nearPlayer, 30.0F, 30.0F);
            }
            return;
        }

        if (villager.blockPosition().distSqr(jobSite) > 12.25D) {
            villager.getNavigation().moveTo(jobSite.getX() + 0.5D, jobSite.getY(), jobSite.getZ() + 0.5D, 0.55D);
            return;
        }

        // 1. Deposit any morning haul first
        if (!data.getHaul().isEmpty()) {
            if (WorkshopHandler.depositHaul(level, villager, data)) {
                data.setActionCooldown(120);
                return;
            }
        }

        // 2. Process workshop chest recipes
        int maxWorkshop = MarketConfig.MAX_WORKSHOP_ACTIONS_PER_DAY.get();
        if (data.getActionCooldown() <= 0 && data.getWorkshopActionsToday() < maxWorkshop) {
            WorkshopHandler.ProcessResult result = WorkshopHandler.processWorkshop(level, villager);
            if (result.outcome() == WorkshopHandler.ProcessOutcome.PROCESSED) {
                data.incrementWorkshopActions();
                data.setActionCooldown(400); // ~20 seconds between real workshop cycles
                holdRoutineItem(villager, data, WorkshopHandler.getProfessionProp(prof));
                return;
            } else if (result.outcome() == WorkshopHandler.ProcessOutcome.MISSING_INGREDIENT && result.missingItem() != null) {
                // Silent in-world hint: hold the missing item, look at nearby player, shake head
                Player nearPlayer = level.getNearestPlayer(villager, 10.0D);
                if (nearPlayer != null) {
                    data.setMissingHintItem(result.missingItem(), 100); // 5 seconds
                    holdRoutineItem(villager, data, new ItemStack(result.missingItem()));
                    villager.getLookControl().setLookAt(nearPlayer, 30.0F, 30.0F);
                    villager.setUnhappyCounter(40);
                    level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.65F, 1.0F);
                }
                data.setActionCooldown(320);
                return;
            } else {
                // Ambient workstation pose when chest is absent or empty
                holdRoutineItem(villager, data, WorkshopHandler.getProfessionProp(prof));
                data.setActionCooldown(260);
            }
        }
    }

    // =========================================================================
    // PHASE 4: TOWN SQUARE & VILLAGER-TO-VILLAGER TRADING (13:00 - 15:00)
    // =========================================================================

    private static void handleTownSquare(ServerLevel level, Villager villager, VillagerProfession prof, VillagerRoutineData data) {
        BlockPos square = getMeetingPoint(villager);
        if (square == null) {
            square = findNearestBlock(level, villager.blockPosition(), 36, s -> s.is(Blocks.BELL));
        }
        if (square == null) {
            return;
        }

        if (villager.blockPosition().distSqr(square) > 36.0D) {
            villager.getNavigation().moveTo(square.getX() + 0.5D, square.getY(), square.getZ() + 0.5D, 0.55D);
            return;
        }

        // Look for another villager with a different profession nearby to exchange goods visually
        List<Villager> neighbors = level.getEntitiesOfClass(
                Villager.class,
                villager.getBoundingBox().inflate(6.0D),
                v -> v.isAlive() && v != villager && v.getVillagerData().getProfession() != prof && VillageContextHelper.isWorkingProfession(v.getVillagerData().getProfession())
        );

        if (!neighbors.isEmpty()) {
            Villager partner = neighbors.get(0);
            villager.getLookControl().setLookAt(partner, 30.0F, 30.0F);
            holdRoutineItem(villager, data, WorkshopHandler.getProfessionProp(prof));
            if (data.getActionCooldown() <= 0) {
                data.setActionCooldown(240 + villager.getRandom().nextInt(160));
                Vec3 mid = villager.position().add(partner.position()).scale(0.5D);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mid.x, mid.y + 1.5D, mid.z, 4, 0.3D, 0.2D, 0.3D, 0.02D);
                level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.5F, 1.0F);
            }
        } else {
            clearRoutineHeldItem(villager, data);
        }
    }

    // =========================================================================
    // PHASE 6: EVENING DINNER AT HOME BEFORE BEDTIME (17:00 - 18:00)
    // =========================================================================

    private static void handleEveningDinner(ServerLevel level, Villager villager, VillagerProfession prof, VillagerRoutineData data) {
        if (data.hasEatenDinnerToday()) {
            clearRoutineHeldItem(villager, data);
            return;
        }

        BlockPos home = getHomePos(villager);
        if (home != null && villager.blockPosition().distSqr(home) > 16.0D) {
            villager.getNavigation().moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, 0.6D);
            long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
            if (timeOfDay < 11450L) {
                return;
            }
        }

        ItemStack meal = WorkshopHandler.getDinnerFood(prof);
        holdRoutineItem(villager, data, meal);
        level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, meal),
                villager.getX(),
                villager.getY() + 1.45D,
                villager.getZ(),
                8,
                0.2D,
                0.15D,
                0.2D,
                0.04D
        );
        level.playSound(null, villager.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.7F, 1.0F);
        if (villager.getHealth() < villager.getMaxHealth()) {
            villager.heal(4.0F);
        }
        data.setAteDinnerToday(true);
    }

    // =========================================================================
    // WORLD & SAFETY HELPERS
    // =========================================================================

    @Nullable
    private static BlockPos getHomePos(Villager villager) {
        Optional<GlobalPos> opt = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (opt.isPresent() && opt.get().dimension().equals(villager.level().dimension())) {
            return opt.get().pos();
        }
        return null;
    }

    @Nullable
    private static BlockPos getMeetingPoint(Villager villager) {
        Optional<GlobalPos> opt = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        if (opt.isPresent() && opt.get().dimension().equals(villager.level().dimension())) {
            return opt.get().pos();
        }
        return null;
    }

    private static boolean isFullBeehive(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BeehiveBlock && state.getValue(BeehiveBlock.HONEY_LEVEL) >= 5;
    }

    private static boolean isCrackedBrick(BlockState state) {
        return state.is(Blocks.CRACKED_STONE_BRICKS) || state.is(Blocks.CRACKED_DEEPSLATE_BRICKS);
    }

    private static boolean isSafeTorchSpot(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.AIR)) {
            return false;
        }
        BlockPos below = pos.below();
        if (!Block.canSupportCenter(level, below, Direction.UP)) {
            return false;
        }
        if (level.getBlockState(below).is(Blocks.FARMLAND)) {
            return false;
        }
        return level.getBrightness(LightLayer.BLOCK, pos) == 0;
    }

    @Nullable
    private static BlockPos findDarkSpotNearVillage(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (isSafeTorchSpot(level, pos)) {
                        double d = pos.distSqr(center);
                        if (d > 16.0D && d < bestDistSq) {
                            bestDistSq = d;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean isSafeWaterShore(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isSolidRender(level, below)) {
            return false;
        }
        return findAdjacentWater(level, pos) != null;
    }

    @Nullable
    private static BlockPos findAdjacentWater(ServerLevel level, BlockPos shorePos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos checkDown = shorePos.relative(dir).below();
            if (level.getBlockState(checkDown).is(Blocks.WATER)) {
                return checkDown;
            }
            BlockPos checkSame = shorePos.relative(dir);
            if (level.getBlockState(checkSame).is(Blocks.WATER)) {
                return checkSame;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findNearestSafeShore(ServerLevel level, BlockPos center, int radius) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (isSafeWaterShore(level, pos)) {
                        double d = pos.distSqr(center);
                        if (d < bestDistSq) {
                            bestDistSq = d;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findSafeLavaViewingSpot(ServerLevel level, BlockPos center, int radius) {
        BlockPos lava = findNearestBlock(level, center, radius, s -> s.is(Blocks.LAVA) || s.is(Blocks.LAVA_CAULDRON));
        if (lava == null) {
            return null;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = lava.relative(dir, 2);
            if (level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()
                    && level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())
                    && !level.getBlockState(candidate.relative(dir.getOpposite())).is(Blocks.LAVA)) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos findNearestBlock(ServerLevel level, BlockPos center, int radius, java.util.function.Predicate<BlockState> matcher) {
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (matcher.test(level.getBlockState(pos))) {
                        double d = pos.distSqr(center);
                        if (d < bestDistSq) {
                            bestDistSq = d;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }
}
