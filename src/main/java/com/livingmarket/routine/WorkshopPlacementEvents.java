package com.livingmarket.routine;

import com.livingmarket.config.MarketConfig;
import com.livingmarket.logic.VillageContextHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public final class WorkshopPlacementEvents {
    private WorkshopPlacementEvents() {}

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!MarketConfig.ENABLE_PLACEMENT_HINTS.get() || !MarketConfig.ENABLE_WORKSHOP_PROCESSING.get()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        BlockState placed = event.getPlacedBlock();
        if (!(placed.getBlock() instanceof ChestBlock || placed.getBlock() instanceof BarrelBlock)) {
            return;
        }

        BlockPos placedPos = event.getPos();
        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class,
                new AABB(placedPos).inflate(32.0D),
                v -> v.isAlive() && VillageContextHelper.isWorkingProfession(v.getVillagerData().getProfession())
        );

        for (Villager villager : villagers) {
            BlockPos jobSite = WorkshopHandler.getJobSitePos(villager);
            if (jobSite != null && jobSite.distSqr(placedPos) <= 13.0D) {
                VillagerProfession prof = villager.getVillagerData().getProfession();
                String profPath = WorkshopHandler.getProfessionShortName(prof);
                Component profName = Component.translatable("entity.minecraft.villager." + profPath);
                Component recipe = Component.translatable(WorkshopHandler.getRecipeTranslationKey(profPath));
                Component msg = Component.translatable("workshop.livingmarket.placed_hint", profName, recipe)
                        .withStyle(ChatFormatting.GOLD);
                player.displayClientMessage(msg, true);
                return;
            }
        }
    }
}
