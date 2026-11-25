package com.livingmarket.mixin;

import com.livingmarket.client.ClientMarketState;
import com.livingmarket.logic.BiomeRegion;
import com.livingmarket.network.MarketNetwork;
import com.livingmarket.network.RerollTradesPacket;
import com.livingmarket.network.TogglePinPacket;
import com.livingmarket.routine.WorkshopHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
    @Shadow
    private int shopItem;

    @Shadow
    int scrollOff;

    @Unique
    private Button livingmarket$pinButton;

    @Unique
    private Button livingmarket$rerollButton;

    @Unique
    private Button livingmarket$workshopButton;

    protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void livingmarket$initButtons(CallbackInfo ci) {
        int btnY = Math.max(2, this.topPos - 22);

        this.livingmarket$pinButton = Button.builder(Component.empty(), btn -> {
            if (ClientMarketState.isEnabledFor(this.menu.containerId)) {
                MerchantOffers offers = this.menu.getOffers();
                if (this.shopItem >= 0 && this.shopItem < offers.size() && !ClientMarketState.isSpecial(this.shopItem)) {
                    MarketNetwork.sendToServer(new TogglePinPacket(this.shopItem));
                }
            }
        }).bounds(this.leftPos, btnY, 122, 20).build();

        this.livingmarket$rerollButton = Button.builder(Component.empty(), btn -> {
            if (ClientMarketState.isEnabledFor(this.menu.containerId)) {
                MarketNetwork.sendToServer(new RerollTradesPacket());
            }
        }).bounds(this.leftPos + 125, btnY, 125, 20).build();

        this.livingmarket$workshopButton = Button.builder(Component.literal("?"), btn -> {
            if (ClientMarketState.isEnabledFor(this.menu.containerId)) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    String profPath = ClientMarketState.getProfessionPath();
                    Component profName = Component.translatable("entity.minecraft.villager." + profPath);
                    Component recipe = Component.translatable(WorkshopHandler.getRecipeTranslationKey(profPath));
                    mc.player.displayClientMessage(
                            Component.translatable("gui.livingmarket.workshop.chat_reminder", profName, recipe)
                                    .withStyle(ChatFormatting.GOLD),
                            false
                    );
                }
            }
        }).bounds(this.leftPos + 253, btnY, 23, 20).build();

        this.addRenderableWidget(this.livingmarket$pinButton);
        this.addRenderableWidget(this.livingmarket$rerollButton);
        this.addRenderableWidget(this.livingmarket$workshopButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void livingmarket$updateButtonStates(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.livingmarket$pinButton == null || this.livingmarket$rerollButton == null || this.livingmarket$workshopButton == null) {
            return;
        }

        boolean enabled = ClientMarketState.isEnabledFor(this.menu.containerId);
        this.livingmarket$pinButton.visible = enabled;
        this.livingmarket$rerollButton.visible = enabled;
        this.livingmarket$workshopButton.visible = enabled;

        if (!enabled) {
            return;
        }

        MerchantOffers offers = this.menu.getOffers();
        if (this.shopItem >= offers.size()) {
            this.shopItem = 0;
        }

        int pinnedCount = ClientMarketState.getPinnedCount();
        int maxPins = ClientMarketState.getMaxPins();
        boolean hasSelection = this.shopItem >= 0 && this.shopItem < offers.size();
        boolean isSelectedSpecial = hasSelection && ClientMarketState.isSpecial(this.shopItem);
        boolean isSelectedPinned = hasSelection && ClientMarketState.isPinned(this.shopItem);

        if (isSelectedSpecial) {
            this.livingmarket$pinButton.setMessage(Component.translatable("gui.livingmarket.pin.special"));
            this.livingmarket$pinButton.active = false;
            this.livingmarket$pinButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.pin.tooltip.special")));
        } else if (isSelectedPinned) {
            this.livingmarket$pinButton.setMessage(Component.translatable("gui.livingmarket.unpin", pinnedCount, maxPins));
            this.livingmarket$pinButton.active = true;
            this.livingmarket$pinButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.unpin.tooltip")));
        } else {
            this.livingmarket$pinButton.setMessage(Component.translatable("gui.livingmarket.pin", pinnedCount, maxPins));
            boolean canPin = hasSelection && pinnedCount < maxPins;
            this.livingmarket$pinButton.active = canPin;
            if (!canPin && hasSelection) {
                this.livingmarket$pinButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.pin.tooltip.limit", pinnedCount, maxPins)));
            } else {
                this.livingmarket$pinButton.setTooltip(Tooltip.create(Component.translatable("gui.livingmarket.pin.tooltip")));
            }
        }

        int cost = ClientMarketState.getRerollCost();
        boolean hasRerollable = ClientMarketState.getNormalUnpinnedCount() > 0;
        boolean hasEnoughEmeralds = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.getAbilities().instabuild && cost > 0) {
            hasEnoughEmeralds = mc.player.getInventory().countItem(Items.EMERALD) >= cost;
        }

        if (cost <= 0) {
            this.livingmarket$rerollButton.setMessage(Component.translatable("gui.livingmarket.reroll.free"));
        } else {
            this.livingmarket$rerollButton.setMessage(Component.translatable("gui.livingmarket.reroll", cost));
        }

        BiomeRegion region = BiomeRegion.byId(ClientMarketState.getBiomeRegionId());
        Component regionName = Component.translatable(region.getTranslationKey());
        int uniqueProfs = ClientMarketState.getUniqueProfessions();
        int barterChance = ClientMarketState.getBarterChancePercent();

        this.livingmarket$rerollButton.active = hasRerollable && hasEnoughEmeralds;
        if (!hasRerollable) {
            this.livingmarket$rerollButton.setTooltip(Tooltip.create(
                    Component.translatable("gui.livingmarket.reroll.tooltip.all_pinned", regionName, uniqueProfs, barterChance)
            ));
        } else if (!hasEnoughEmeralds) {
            this.livingmarket$rerollButton.setTooltip(Tooltip.create(
                    Component.translatable("gui.livingmarket.reroll.tooltip.no_emeralds", cost, regionName, uniqueProfs, barterChance)
            ));
        } else {
            this.livingmarket$rerollButton.setTooltip(Tooltip.create(
                    Component.translatable("gui.livingmarket.reroll.tooltip", regionName, uniqueProfs, barterChance)
            ));
        }

        // Update Workshop '?' info button tooltip
        String profPath = ClientMarketState.getProfessionPath();
        Component phaseComp = Component.translatable(ClientMarketState.getPhaseKey());
        Component recipeComp = Component.translatable(WorkshopHandler.getRecipeTranslationKey(profPath));
        Component statusComp;
        String statusKey = ClientMarketState.getWorkshopStatusKey();
        String missingId = ClientMarketState.getMissingItemId();
        if ("workshop.livingmarket.status.missing_item".equals(statusKey) && !missingId.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(missingId);
            Item missingItem = rl != null ? ForgeRegistries.ITEMS.getValue(rl) : null;
            Component itemName = (missingItem != null && missingItem != Items.AIR)
                    ? missingItem.getDescription()
                    : Component.literal(missingId);
            statusComp = Component.translatable(statusKey, itemName);
        } else {
            statusComp = Component.translatable(statusKey);
        }

        this.livingmarket$workshopButton.setTooltip(Tooltip.create(
                Component.translatable("gui.livingmarket.workshop.tooltip", phaseComp, recipeComp, statusComp)
        ));
    }

    @Inject(method = "renderScroller", at = @At("TAIL"))
    private void livingmarket$renderOfferFrames(GuiGraphics graphics, int posX, int posY, MerchantOffers offers, CallbackInfo ci) {
        if (!ClientMarketState.isEnabledFor(this.menu.containerId)) {
            return;
        }
        int baseX = posX + 5;
        int baseY = posY + 18;

        for (int row = 0; row < 7; row++) {
            int offerIndex = row + this.scrollOff;
            if (offerIndex >= offers.size()) {
                break;
            }
            byte kind = ClientMarketState.getOfferKind(offerIndex);
            int color = switch (kind) {
                case ClientMarketState.KIND_PINNED -> 0xFFFFD700;       // Gold: pinned trade
                case ClientMarketState.KIND_DAILY_DEAL -> 0xFFFF8C00;   // Fiery orange: Daily Deal
                case ClientMarketState.KIND_RARE_BARTER -> 0xFFD060FF;  // Amethyst purple: Rare Barter
                default -> 0;
            };

            if (color != 0) {
                int y = baseY + row * 20;
                graphics.fill(baseX, y, baseX + 3, y + 20, color);
                graphics.fill(baseX, y, baseX + 88, y + 1, color);
                graphics.fill(baseX, y + 19, baseX + 88, y + 20, color);
                graphics.fill(baseX + 87, y, baseX + 88, y + 20, color);
            }
        }
    }
}
