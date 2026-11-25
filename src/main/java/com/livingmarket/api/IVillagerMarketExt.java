package com.livingmarket.api;

import com.livingmarket.routine.VillagerRoutineData;

public interface IVillagerMarketExt {
    long livingmarket$getLastRefreshDay();
    void livingmarket$setLastRefreshDay(long day);

    int livingmarket$getRerollsToday();
    void livingmarket$setRerollsToday(int count);

    int livingmarket$getCurrentRerollCost();

    void livingmarket$checkDailyRefresh();

    boolean livingmarket$refreshUnpinnedTrades(boolean preserveSpecials);

    boolean livingmarket$togglePin(int offerIndex);

    int livingmarket$getPinnedCount();

    boolean livingmarket$hasActiveDailyDeal();

    boolean livingmarket$hasActiveRareBarter();

    VillagerRoutineData livingmarket$getRoutineData();
}
