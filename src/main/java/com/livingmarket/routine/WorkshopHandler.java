package com.livingmarket.routine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Predicate;

public final class WorkshopHandler {
    public static final int CHEST_SEARCH_RADIUS = 3;

    public record ContainerRef(BlockPos pos, Container container) {}

    public record WorkshopStatus(String statusKey, @Nullable Item missingItem) {}

    public enum ProcessOutcome {
        PROCESSED,
        MISSING_INGREDIENT,
        CHEST_FULL,
        NOTHING_TO_DO,
        NO_CHEST
    }

    public record ProcessResult(ProcessOutcome outcome, @Nullable Item missingItem, @Nullable BlockPos chestPos) {}

    private WorkshopHandler() {}

    @Nullable
    public static BlockPos getJobSitePos(Villager villager) {
        Optional<GlobalPos> opt = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (opt.isPresent() && opt.get().dimension().equals(villager.level().dimension())) {
            return opt.get().pos();
        }
        return null;
    }

    @Nullable
    public static ContainerRef findWorkshopContainer(Level level, @Nullable BlockPos jobSitePos) {
        if (jobSitePos == null) {
            return null;
        }
        // First check the job site block itself (e.g. Fisherman's Barrel!)
        Container self = getStorageContainer(level, jobSitePos);
        if (self != null) {
            return new ContainerRef(jobSitePos, self);
        }

        // Otherwise search nearest chest or barrel within 3 blocks
        ContainerRef best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -CHEST_SEARCH_RADIUS; dx <= CHEST_SEARCH_RADIUS; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -CHEST_SEARCH_RADIUS; dz <= CHEST_SEARCH_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pos = jobSitePos.offset(dx, dy, dz);
                    Container c = getStorageContainer(level, pos);
                    if (c != null) {
                        double distSq = pos.distSqr(jobSitePos);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = new ContainerRef(pos, c);
                        }
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    public static Container getStorageContainer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            return ChestBlock.getContainer(chestBlock, state, level, pos, true);
        }
        if (state.getBlock() instanceof BarrelBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                return container;
            }
        }
        return null;
    }

    /**
     * Evaluates live workshop status for the MerchantScreen '?' tooltip.
     */
    public static WorkshopStatus evaluateStatus(Villager villager) {
        VillagerProfession prof = villager.getVillagerData().getProfession();
        BlockPos jobSite = getJobSitePos(villager);
        if (jobSite == null) {
            return new WorkshopStatus("workshop.livingmarket.status.no_workstation", null);
        }

        ContainerRef ref = findWorkshopContainer(villager.level(), jobSite);
        if (ref == null) {
            return new WorkshopStatus("workshop.livingmarket.status.no_chest", null);
        }

        Container c = ref.container();
        if (prof == VillagerProfession.FISHERMAN || prof == VillagerProfession.SHEPHERD) {
            if (!hasAnySpace(c)) {
                return new WorkshopStatus("workshop.livingmarket.status.chest_full", null);
            }
            return new WorkshopStatus("workshop.livingmarket.status.ready_storage", null);
        }

        if (prof == VillagerProfession.WEAPONSMITH) {
            boolean hasItem = findSlot(c, WorkshopHandler::isWeaponDamaged) >= 0;
            boolean hasFuel = hasFuel(c);
            if (hasItem && !hasFuel) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.COAL);
            }
            if (!hasItem && hasFuel) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.IRON_SWORD);
            }
            return new WorkshopStatus(hasItem ? "workshop.livingmarket.status.active" : "workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.ARMORER) {
            boolean hasItem = findSlot(c, WorkshopHandler::isArmorDamaged) >= 0;
            boolean hasFuel = hasFuel(c);
            if (hasItem && !hasFuel) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.COAL);
            }
            if (!hasItem && hasFuel) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.IRON_CHESTPLATE);
            }
            return new WorkshopStatus(hasItem ? "workshop.livingmarket.status.active" : "workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.TOOLSMITH) {
            boolean hasTool = findSlot(c, WorkshopHandler::isToolDamaged) >= 0;
            boolean hasOre = countMatching(c, WorkshopHandler::isRawOre) >= 2;
            boolean hasFuel = hasFuel(c);
            if ((hasTool || hasOre) && !hasFuel) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.COAL);
            }
            return new WorkshopStatus((hasTool || hasOre) ? "workshop.livingmarket.status.active" : "workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.BUTCHER) {
            boolean hasMeat = countMatching(c, WorkshopHandler::isRawMeat) >= 2;
            boolean hasFuel = hasFuel(c);
            if (hasMeat && !hasFuel) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.COAL);
            }
            return new WorkshopStatus(hasMeat ? "workshop.livingmarket.status.active" : "workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.LEATHERWORKER) {
            boolean hasFlesh = countItem(c, Items.ROTTEN_FLESH) >= 4;
            boolean hasWater = hasWaterCauldron(villager.level(), jobSite);
            if (hasFlesh && !hasWater) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.WATER_BUCKET);
            }
            return new WorkshopStatus(hasFlesh ? "workshop.livingmarket.status.active" : "workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.FLETCHER) {
            boolean hasStick = countItem(c, Items.STICK) >= 1;
            boolean hasFlint = countItem(c, Items.FLINT) >= 1;
            boolean hasFeather = countItem(c, Items.FEATHER) >= 1;
            if (hasStick || hasFlint || hasFeather) {
                if (!hasStick) return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.STICK);
                if (!hasFlint) return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.FLINT);
                if (!hasFeather) return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.FEATHER);
                return new WorkshopStatus("workshop.livingmarket.status.active", null);
            }
            return new WorkshopStatus("workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.LIBRARIAN) {
            boolean hasPaper = countItem(c, Items.PAPER) >= 3;
            boolean hasLeather = countItem(c, Items.LEATHER) >= 1;
            if (hasPaper && !hasLeather) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.LEATHER);
            }
            if (!hasPaper && hasLeather) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.PAPER);
            }
            return new WorkshopStatus(hasPaper ? "workshop.livingmarket.status.active" : "workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.CARTOGRAPHER) {
            boolean hasTorches = countItem(c, Items.TORCH) >= 1;
            boolean hasPaper = countItem(c, Items.PAPER) >= 8;
            boolean hasCompass = countItem(c, Items.COMPASS) >= 1;
            if (hasPaper && !hasCompass) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.COMPASS);
            }
            if (!hasPaper && hasCompass) {
                return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.PAPER);
            }
            if (hasTorches || (hasPaper && hasCompass)) {
                return new WorkshopStatus("workshop.livingmarket.status.active", null);
            }
            return new WorkshopStatus("workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.CLERIC) {
            boolean hasBottle = countItem(c, Items.GLASS_BOTTLE) >= 1;
            boolean hasWart = countItem(c, Items.NETHER_WART) >= 1;
            boolean hasMelon = countItem(c, Items.GLISTERING_MELON_SLICE) >= 1;
            if (hasBottle || hasWart || hasMelon) {
                if (!hasBottle) return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.GLASS_BOTTLE);
                if (!hasWart) return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.NETHER_WART);
                if (!hasMelon) return new WorkshopStatus("workshop.livingmarket.status.missing_item", Items.GLISTERING_MELON_SLICE);
                return new WorkshopStatus("workshop.livingmarket.status.active", null);
            }
            return new WorkshopStatus("workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.MASON) {
            boolean hasClay = countItem(c, Items.CLAY_BALL) >= 4;
            boolean hasCobble = countItem(c, Items.COBBLESTONE) >= 4;
            return new WorkshopStatus((hasClay || hasCobble) ? "workshop.livingmarket.status.active" : "workshop.livingmarket.status.ready_chest", null);
        }

        if (prof == VillagerProfession.FARMER) {
            boolean hasWheat = countItem(c, Items.WHEAT) >= 3;
            return new WorkshopStatus(hasWheat ? "workshop.livingmarket.status.active" : "workshop.livingmarket.status.ready_storage", null);
        }

        return new WorkshopStatus("workshop.livingmarket.status.ready_chest", null);
    }

    /**
     * Deposits any collected morning haul (fish, wool, honey) into the villager's barrel or workshop chest.
     */
    public static boolean depositHaul(ServerLevel level, Villager villager, VillagerRoutineData data) {
        if (data.getHaul().isEmpty()) {
            return false;
        }
        ContainerRef ref = findWorkshopContainer(level, getJobSitePos(villager));
        if (ref == null) {
            return false;
        }

        boolean depositedAny = false;
        Iterator<ItemStack> it = data.getHaul().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            ItemStack remainder = insertItem(ref.container(), stack);
            if (remainder.getCount() < stack.getCount()) {
                depositedAny = true;
            }
            if (remainder.isEmpty()) {
                it.remove();
            } else {
                stack.setCount(remainder.getCount());
            }
        }

        if (depositedAny) {
            ref.container().setChanged();
            spawnSuccessSparkles(level, ref.pos(), SoundEvents.BARREL_CLOSE);
        }
        return depositedAny;
    }

    /**
     * Runs one real workshop processing step for the villager's profession.
     */
    public static ProcessResult processWorkshop(ServerLevel level, Villager villager) {
        BlockPos jobSite = getJobSitePos(villager);
        if (jobSite == null) {
            return new ProcessResult(ProcessOutcome.NO_CHEST, null, null);
        }
        ContainerRef ref = findWorkshopContainer(level, jobSite);
        if (ref == null) {
            return new ProcessResult(ProcessOutcome.NO_CHEST, null, null);
        }

        Container c = ref.container();
        BlockPos chestPos = ref.pos();
        VillagerProfession prof = villager.getVillagerData().getProfession();

        if (prof == VillagerProfession.WEAPONSMITH) {
            int slot = findSlot(c, WorkshopHandler::isWeaponDamaged);
            if (slot >= 0) {
                if (!consumeFuel(c)) {
                    return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, Items.COAL, chestPos);
                }
                repairSlot(c, slot, 120);
                spawnSuccessSparkles(level, chestPos, SoundEvents.GRINDSTONE_USE);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.ARMORER) {
            int slot = findSlot(c, WorkshopHandler::isArmorDamaged);
            if (slot >= 0) {
                if (!consumeFuel(c)) {
                    return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, Items.COAL, chestPos);
                }
                repairSlot(c, slot, 120);
                spawnSuccessSparkles(level, chestPos, SoundEvents.ANVIL_USE);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.TOOLSMITH) {
            int toolSlot = findSlot(c, WorkshopHandler::isToolDamaged);
            if (toolSlot >= 0) {
                if (!consumeFuel(c)) {
                    return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, Items.COAL, chestPos);
                }
                repairSlot(c, toolSlot, 120);
                spawnSuccessSparkles(level, chestPos, SoundEvents.ANVIL_USE);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            Item rawOre = findRawOreWithCount(c, 2);
            if (rawOre != null) {
                Item ingot = rawOre == Items.RAW_IRON ? Items.IRON_INGOT
                        : (rawOre == Items.RAW_GOLD ? Items.GOLD_INGOT : Items.COPPER_INGOT);
                if (!canFitItem(c, new ItemStack(ingot, 2))) {
                    return new ProcessResult(ProcessOutcome.CHEST_FULL, null, chestPos);
                }
                if (!consumeFuel(c)) {
                    return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, Items.COAL, chestPos);
                }
                consumeItem(c, rawOre, 2);
                insertItem(c, new ItemStack(ingot, 2));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.FIRE_EXTINGUISH);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.BUTCHER) {
            Item rawMeat = findRawMeatWithCount(c, 2);
            if (rawMeat != null) {
                Item cooked = getCookedMeat(rawMeat);
                if (!canFitItem(c, new ItemStack(cooked, 2))) {
                    return new ProcessResult(ProcessOutcome.CHEST_FULL, null, chestPos);
                }
                if (!consumeFuel(c)) {
                    return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, Items.COAL, chestPos);
                }
                consumeItem(c, rawMeat, 2);
                insertItem(c, new ItemStack(cooked, 2));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.CAMPFIRE_CRACKLE);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.LEATHERWORKER) {
            if (countItem(c, Items.ROTTEN_FLESH) >= 4) {
                if (!hasWaterCauldron(level, jobSite)) {
                    return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, Items.WATER_BUCKET, chestPos);
                }
                if (!canFitItem(c, new ItemStack(Items.LEATHER, 1))) {
                    return new ProcessResult(ProcessOutcome.CHEST_FULL, null, chestPos);
                }
                consumeItem(c, Items.ROTTEN_FLESH, 4);
                BlockState cauldronState = level.getBlockState(jobSite);
                LayeredCauldronBlock.lowerFillLevel(cauldronState, level, jobSite);
                insertItem(c, new ItemStack(Items.LEATHER, 1));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.BOTTLE_FILL);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.MASON) {
            if (countItem(c, Items.CLAY_BALL) >= 4 && canFitItem(c, new ItemStack(Items.BRICK, 4))) {
                consumeItem(c, Items.CLAY_BALL, 4);
                insertItem(c, new ItemStack(Items.BRICK, 4));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.UI_STONECUTTER_TAKE_RESULT);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            if (countItem(c, Items.COBBLESTONE) >= 4 && canFitItem(c, new ItemStack(Items.STONE_BRICKS, 4))) {
                consumeItem(c, Items.COBBLESTONE, 4);
                insertItem(c, new ItemStack(Items.STONE_BRICKS, 4));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.UI_STONECUTTER_TAKE_RESULT);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.FLETCHER) {
            boolean hasStick = countItem(c, Items.STICK) >= 1;
            boolean hasFlint = countItem(c, Items.FLINT) >= 1;
            boolean hasFeather = countItem(c, Items.FEATHER) >= 1;
            if (hasStick && hasFlint && hasFeather) {
                if (!canFitItem(c, new ItemStack(Items.ARROW, 6))) {
                    return new ProcessResult(ProcessOutcome.CHEST_FULL, null, chestPos);
                }
                consumeItem(c, Items.STICK, 1);
                consumeItem(c, Items.FLINT, 1);
                consumeItem(c, Items.FEATHER, 1);
                insertItem(c, new ItemStack(Items.ARROW, 6));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.VILLAGER_WORK_FLETCHER);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            } else if (hasStick || hasFlint || hasFeather) {
                Item missing = !hasStick ? Items.STICK : (!hasFlint ? Items.FLINT : Items.FEATHER);
                return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, missing, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.LIBRARIAN) {
            boolean hasPaper = countItem(c, Items.PAPER) >= 3;
            boolean hasLeather = countItem(c, Items.LEATHER) >= 1;
            if (hasPaper && hasLeather) {
                if (!canFitItem(c, new ItemStack(Items.BOOK, 1))) {
                    return new ProcessResult(ProcessOutcome.CHEST_FULL, null, chestPos);
                }
                consumeItem(c, Items.PAPER, 3);
                consumeItem(c, Items.LEATHER, 1);
                insertItem(c, new ItemStack(Items.BOOK, 1));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.BOOK_PAGE_TURN);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            } else if (hasPaper || hasLeather) {
                return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, hasPaper ? Items.LEATHER : Items.PAPER, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.CARTOGRAPHER) {
            boolean hasPaper = countItem(c, Items.PAPER) >= 8;
            boolean hasCompass = countItem(c, Items.COMPASS) >= 1;
            if (hasPaper && hasCompass) {
                if (!canFitItem(c, new ItemStack(Items.MAP, 1))) {
                    return new ProcessResult(ProcessOutcome.CHEST_FULL, null, chestPos);
                }
                consumeItem(c, Items.PAPER, 8);
                consumeItem(c, Items.COMPASS, 1);
                insertItem(c, new ItemStack(Items.MAP, 1));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            } else if (hasPaper || hasCompass) {
                return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, hasPaper ? Items.COMPASS : Items.PAPER, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.CLERIC) {
            boolean hasBottle = countItem(c, Items.GLASS_BOTTLE) >= 1;
            boolean hasWart = countItem(c, Items.NETHER_WART) >= 1;
            boolean hasMelon = countItem(c, Items.GLISTERING_MELON_SLICE) >= 1;
            if (hasBottle && hasWart && hasMelon) {
                ItemStack potion = PotionUtils.setPotion(new ItemStack(Items.POTION, 1), Potions.HEALING);
                if (!canFitItem(c, potion)) {
                    return new ProcessResult(ProcessOutcome.CHEST_FULL, null, chestPos);
                }
                consumeItem(c, Items.GLASS_BOTTLE, 1);
                consumeItem(c, Items.NETHER_WART, 1);
                consumeItem(c, Items.GLISTERING_MELON_SLICE, 1);
                insertItem(c, potion);
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.BREWING_STAND_BREW);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            } else if (hasBottle || hasWart || hasMelon) {
                Item missing = !hasBottle ? Items.GLASS_BOTTLE : (!hasWart ? Items.NETHER_WART : Items.GLISTERING_MELON_SLICE);
                return new ProcessResult(ProcessOutcome.MISSING_INGREDIENT, missing, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        if (prof == VillagerProfession.FARMER) {
            if (countItem(c, Items.WHEAT) >= 3 && canFitItem(c, new ItemStack(Items.BREAD, 1))) {
                consumeItem(c, Items.WHEAT, 3);
                insertItem(c, new ItemStack(Items.BREAD, 1));
                c.setChanged();
                spawnSuccessSparkles(level, chestPos, SoundEvents.VILLAGER_WORK_FARMER);
                return new ProcessResult(ProcessOutcome.PROCESSED, null, chestPos);
            }
            return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
        }

        return new ProcessResult(ProcessOutcome.NOTHING_TO_DO, null, chestPos);
    }

    public static void spawnSuccessSparkles(ServerLevel level, BlockPos pos, SoundEvent sound) {
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5D,
                pos.getY() + 1.15D,
                pos.getZ() + 0.5D,
                6,
                0.35D,
                0.2D,
                0.35D,
                0.02D
        );
        level.playSound(null, pos, sound, SoundSource.NEUTRAL, 0.65F, 1.05F);
    }

    public static String getProfessionShortName(VillagerProfession prof) {
        ResourceLocation key = ForgeRegistries.VILLAGER_PROFESSIONS.getKey(prof);
        return key != null ? key.getPath() : "none";
    }

    public static String getRecipeTranslationKey(String professionPath) {
        return "workshop.livingmarket.recipe." + professionPath;
    }

    public static ItemStack getDinnerFood(VillagerProfession prof) {
        if (prof == VillagerProfession.FISHERMAN) return new ItemStack(Items.COOKED_COD);
        if (prof == VillagerProfession.WEAPONSMITH) return new ItemStack(Items.COOKED_BEEF);
        if (prof == VillagerProfession.ARMORER) return new ItemStack(Items.COOKED_PORKCHOP);
        if (prof == VillagerProfession.TOOLSMITH) return new ItemStack(Items.COOKED_CHICKEN);
        if (prof == VillagerProfession.BUTCHER) return new ItemStack(Items.RABBIT_STEW);
        if (prof == VillagerProfession.FARMER) return new ItemStack(Items.PUMPKIN_PIE);
        if (prof == VillagerProfession.SHEPHERD) return new ItemStack(Items.APPLE);
        if (prof == VillagerProfession.LEATHERWORKER) return new ItemStack(Items.COOKED_RABBIT);
        if (prof == VillagerProfession.MASON) return new ItemStack(Items.BAKED_POTATO);
        if (prof == VillagerProfession.FLETCHER) return new ItemStack(Items.COOKED_CHICKEN);
        if (prof == VillagerProfession.LIBRARIAN) return new ItemStack(Items.COOKIE);
        if (prof == VillagerProfession.CARTOGRAPHER) return new ItemStack(Items.SWEET_BERRIES);
        if (prof == VillagerProfession.CLERIC) return new ItemStack(Items.GOLDEN_APPLE);
        return new ItemStack(Items.BREAD);
    }

    public static ItemStack getProfessionProp(VillagerProfession prof) {
        if (prof == VillagerProfession.FISHERMAN) return new ItemStack(Items.FISHING_ROD);
        if (prof == VillagerProfession.WEAPONSMITH) return new ItemStack(Items.IRON_SWORD);
        if (prof == VillagerProfession.ARMORER) return new ItemStack(Items.SHIELD);
        if (prof == VillagerProfession.TOOLSMITH) return new ItemStack(Items.IRON_PICKAXE);
        if (prof == VillagerProfession.BUTCHER) return new ItemStack(Items.IRON_AXE);
        if (prof == VillagerProfession.FARMER) return new ItemStack(Items.IRON_HOE);
        if (prof == VillagerProfession.SHEPHERD) return new ItemStack(Items.SHEARS);
        if (prof == VillagerProfession.LEATHERWORKER) return new ItemStack(Items.LEATHER);
        if (prof == VillagerProfession.MASON) return new ItemStack(Items.STONE_PICKAXE);
        if (prof == VillagerProfession.FLETCHER) return new ItemStack(Items.BOW);
        if (prof == VillagerProfession.LIBRARIAN) return new ItemStack(Items.WRITABLE_BOOK);
        if (prof == VillagerProfession.CARTOGRAPHER) return new ItemStack(Items.SPYGLASS);
        if (prof == VillagerProfession.CLERIC) return new ItemStack(Items.AMETHYST_SHARD);
        return new ItemStack(Items.EMERALD);
    }

    public static Item getWoolForColor(DyeColor color) {
        return switch (color) {
            case ORANGE -> Items.ORANGE_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
            case LIME -> Items.LIME_WOOL;
            case PINK -> Items.PINK_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case RED -> Items.RED_WOOL;
            case BLACK -> Items.BLACK_WOOL;
            default -> Items.WHITE_WOOL;
        };
    }

    // ---- Container utilities ----

    public static boolean hasAnySpace(Container c) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public static boolean canFitItem(Container c, ItemStack toAdd) {
        int remaining = toAdd.getCount();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameTags(s, toAdd) && s.getCount() < s.getMaxStackSize()) {
                remaining -= (s.getMaxStackSize() - s.getCount());
                if (remaining <= 0) {
                    return true;
                }
            }
        }
        return remaining <= 0;
    }

    public static ItemStack insertItem(Container c, ItemStack toAdd) {
        ItemStack copy = toAdd.copy();
        for (int i = 0; i < c.getContainerSize() && !copy.isEmpty(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameTags(s, copy) && s.getCount() < s.getMaxStackSize()) {
                int move = Math.min(copy.getCount(), s.getMaxStackSize() - s.getCount());
                s.grow(move);
                copy.shrink(move);
            }
        }
        for (int i = 0; i < c.getContainerSize() && !copy.isEmpty(); i++) {
            if (c.getItem(i).isEmpty()) {
                c.setItem(i, copy.copy());
                copy.setCount(0);
            }
        }
        return copy;
    }

    public static int countItem(Container c, Item item) {
        int total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.is(item)) {
                total += s.getCount();
            }
        }
        return total;
    }

    public static int countMatching(Container c, Predicate<ItemStack> pred) {
        int total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && pred.test(s)) {
                total += s.getCount();
            }
        }
        return total;
    }

    public static int findSlot(Container c, Predicate<ItemStack> pred) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && pred.test(s)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean consumeItem(Container c, Item item, int amount) {
        if (countItem(c, item) < amount) {
            return false;
        }
        int rem = amount;
        for (int i = 0; i < c.getContainerSize() && rem > 0; i++) {
            ItemStack s = c.getItem(i);
            if (s.is(item)) {
                int take = Math.min(rem, s.getCount());
                s.shrink(take);
                rem -= take;
            }
        }
        return rem == 0;
    }

    private static boolean hasFuel(Container c) {
        return countItem(c, Items.COAL) >= 1 || countItem(c, Items.CHARCOAL) >= 1;
    }

    private static boolean consumeFuel(Container c) {
        if (consumeItem(c, Items.COAL, 1)) {
            return true;
        }
        return consumeItem(c, Items.CHARCOAL, 1);
    }

    private static void repairSlot(Container c, int slot, int amount) {
        ItemStack stack = c.getItem(slot);
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - amount));
        c.setChanged();
    }

    private static boolean isWeaponDamaged(ItemStack s) {
        return s.isDamageableItem() && s.isDamaged() && (s.getItem() instanceof SwordItem || s.getItem() instanceof AxeItem);
    }

    private static boolean isArmorDamaged(ItemStack s) {
        return s.isDamageableItem() && s.isDamaged() && (s.getItem() instanceof ArmorItem || s.getItem() instanceof ShieldItem);
    }

    private static boolean isToolDamaged(ItemStack s) {
        return s.isDamageableItem() && s.isDamaged() && (s.getItem() instanceof PickaxeItem || s.getItem() instanceof ShovelItem || s.getItem() instanceof HoeItem);
    }

    private static boolean isRawOre(ItemStack s) {
        return s.is(Items.RAW_IRON) || s.is(Items.RAW_COPPER) || s.is(Items.RAW_GOLD);
    }

    @Nullable
    private static Item findRawOreWithCount(Container c, int needed) {
        if (countItem(c, Items.RAW_IRON) >= needed) return Items.RAW_IRON;
        if (countItem(c, Items.RAW_COPPER) >= needed) return Items.RAW_COPPER;
        if (countItem(c, Items.RAW_GOLD) >= needed) return Items.RAW_GOLD;
        return null;
    }

    private static boolean isRawMeat(ItemStack s) {
        return s.is(Items.BEEF) || s.is(Items.PORKCHOP) || s.is(Items.CHICKEN) || s.is(Items.MUTTON) || s.is(Items.RABBIT);
    }

    @Nullable
    private static Item findRawMeatWithCount(Container c, int needed) {
        if (countItem(c, Items.BEEF) >= needed) return Items.BEEF;
        if (countItem(c, Items.PORKCHOP) >= needed) return Items.PORKCHOP;
        if (countItem(c, Items.CHICKEN) >= needed) return Items.CHICKEN;
        if (countItem(c, Items.MUTTON) >= needed) return Items.MUTTON;
        if (countItem(c, Items.RABBIT) >= needed) return Items.RABBIT;
        return null;
    }

    private static Item getCookedMeat(Item raw) {
        if (raw == Items.BEEF) return Items.COOKED_BEEF;
        if (raw == Items.PORKCHOP) return Items.COOKED_PORKCHOP;
        if (raw == Items.CHICKEN) return Items.COOKED_CHICKEN;
        if (raw == Items.MUTTON) return Items.COOKED_MUTTON;
        return Items.COOKED_RABBIT;
    }

    private static boolean hasWaterCauldron(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) > 0;
    }
}
