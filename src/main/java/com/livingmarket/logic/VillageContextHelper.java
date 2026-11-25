package com.livingmarket.logic;

import com.livingmarket.config.MarketConfig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class VillageContextHelper {
    public static final double VILLAGE_SCAN_RADIUS = 48.0D;

    private VillageContextHelper() {}

    public static List<Villager> getNearbyProfessionalVillagers(Villager center) {
        List<Villager> list = center.level().getEntitiesOfClass(
                Villager.class,
                center.getBoundingBox().inflate(VILLAGE_SCAN_RADIUS),
                v -> v.isAlive() && isWorkingProfession(v.getVillagerData().getProfession())
        );
        if (!list.contains(center) && isWorkingProfession(center.getVillagerData().getProfession())) {
            list = new ArrayList<>(list);
            list.add(center);
        }
        return list;
    }

    public static boolean isWorkingProfession(VillagerProfession profession) {
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT;
    }

    public static int getUniqueProfessionCount(Villager center) {
        List<Villager> nearby = getNearbyProfessionalVillagers(center);
        Set<VillagerProfession> unique = new HashSet<>();
        for (Villager v : nearby) {
            unique.add(v.getVillagerData().getProfession());
        }
        return Math.max(1, unique.size());
    }

    public static int getRareBarterChancePercent(int uniqueProfessions) {
        if (uniqueProfessions >= 11) {
            return MarketConfig.BARTER_CHANCE_FULL.get();
        }
        if (uniqueProfessions >= 7) {
            return MarketConfig.BARTER_CHANCE_HIGH.get();
        }
        if (uniqueProfessions >= 4) {
            return MarketConfig.BARTER_CHANCE_MID.get();
        }
        return MarketConfig.BARTER_CHANCE_LOW.get();
    }

    /**
     * Deterministically selects ~30% of villagers in the same village cluster for a Daily Deal each day.
     * For small settlements (3-4 villagers), rounds fairly with a guaranteed minimum of 1 villager.
     */
    public static boolean isSelectedForDailyDeal(Villager villager, long day) {
        int percent = MarketConfig.DAILY_DEAL_PERCENT.get();
        if (percent <= 0) {
            return false;
        }
        if (percent >= 100) {
            return true;
        }

        List<Villager> nearby = new ArrayList<>(getNearbyProfessionalVillagers(villager));
        int total = nearby.size();
        if (total <= 0) {
            return false;
        }

        if (total <= 2) {
            long score = mixHash(day, villager.getUUID());
            return Math.floorMod(score, 100L) < percent;
        }

        int targetCount = Math.max(1, (int) Math.round(total * (percent / 100.0D)));
        targetCount = Math.min(total, targetCount);

        nearby.sort(Comparator.comparingLong((Villager v) -> mixHash(day, v.getUUID()))
                .thenComparing(Villager::getUUID));

        UUID selfId = villager.getUUID();
        for (int i = 0; i < targetCount; i++) {
            if (nearby.get(i).getUUID().equals(selfId)) {
                return true;
            }
        }
        return false;
    }

    private static long mixHash(long day, UUID uuid) {
        long h = day * 0x9E3779B97F4A7C15L;
        h ^= uuid.getMostSignificantBits();
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h ^= uuid.getLeastSignificantBits();
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
