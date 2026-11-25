package com.livingmarket.mixin;

import com.livingmarket.api.IMerchantOfferExt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantOffer.class)
public class MerchantOfferMixin implements IMerchantOfferExt {
    @Unique
    private boolean livingmarket$pinned = false;

    @Unique
    private int livingmarket$tradeLevel = 0;

    @Unique
    private String livingmarket$tradeType = "";

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void livingmarket$readNbt(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("lmPinned")) {
            this.livingmarket$pinned = tag.getBoolean("lmPinned");
        }
        if (tag.contains("lmTradeLevel")) {
            this.livingmarket$tradeLevel = tag.getInt("lmTradeLevel");
        }
        if (tag.contains("lmTradeType")) {
            this.livingmarket$tradeType = tag.getString("lmTradeType");
        }
    }

    @Inject(method = "createTag", at = @At("RETURN"))
    private void livingmarket$writeNbt(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putBoolean("lmPinned", this.livingmarket$pinned);
        tag.putInt("lmTradeLevel", this.livingmarket$tradeLevel);
        tag.putString("lmTradeType", this.livingmarket$tradeType == null ? "" : this.livingmarket$tradeType);
    }

    @Inject(method = "resetUses", at = @At("HEAD"), cancellable = true)
    private void livingmarket$preventSpecialRestock(CallbackInfo ci) {
        if (this.livingmarket$isSpecial()) {
            ci.cancel();
        }
    }

    @Override
    public boolean livingmarket$isPinned() {
        return this.livingmarket$pinned;
    }

    @Override
    public void livingmarket$setPinned(boolean pinned) {
        this.livingmarket$pinned = pinned;
    }

    @Override
    public int livingmarket$getTradeLevel() {
        return this.livingmarket$tradeLevel;
    }

    @Override
    public void livingmarket$setTradeLevel(int level) {
        this.livingmarket$tradeLevel = level;
    }

    @Override
    public String livingmarket$getTradeType() {
        return this.livingmarket$tradeType;
    }

    @Override
    public void livingmarket$setTradeType(String type) {
        this.livingmarket$tradeType = type;
    }
}
