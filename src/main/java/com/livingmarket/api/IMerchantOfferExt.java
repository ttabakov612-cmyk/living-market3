package com.livingmarket.api;

public interface IMerchantOfferExt {
    String TYPE_BASIC_BUY = "BASIC_BUY";
    String TYPE_RARE_BUY = "RARE_BUY";
    String TYPE_SELL = "SELL";
    String TYPE_DAILY_DEAL = "DAILY_DEAL";
    String TYPE_RARE_BARTER = "RARE_BARTER";

    boolean livingmarket$isPinned();
    void livingmarket$setPinned(boolean pinned);

    int livingmarket$getTradeLevel();
    void livingmarket$setTradeLevel(int level);

    String livingmarket$getTradeType();
    void livingmarket$setTradeType(String type);

    default boolean livingmarket$isDailyDeal() {
        return TYPE_DAILY_DEAL.equals(this.livingmarket$getTradeType());
    }

    default boolean livingmarket$isRareBarter() {
        return TYPE_RARE_BARTER.equals(this.livingmarket$getTradeType());
    }

    default boolean livingmarket$isSpecial() {
        return this.livingmarket$isDailyDeal() || this.livingmarket$isRareBarter();
    }
}
