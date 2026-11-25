package com.livingmarket.routine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class VillagerRoutineData {
    private long routineDay = -1L;
    private int fieldActionsToday = 0;
    private int workshopActionsToday = 0;
    private boolean ateDinnerToday = false;
    private boolean attendedServiceToday = false;
    private final List<ItemStack> haul = new ArrayList<>();

    // Transient runtime fields (not saved to disk)
    @Nullable
    private DayPhase lastPhase = null;
    @Nullable
    private BlockPos cachedTargetPos = null;
    private int actionCooldown = 0;
    private int hintHoldTicks = 0;
    @Nullable
    private Item missingHintItem = null;
    private boolean holdingRoutineItem = false;

    public void checkNewDay(long currentDay) {
        if (this.routineDay != currentDay) {
            this.routineDay = currentDay;
            this.fieldActionsToday = 0;
            this.workshopActionsToday = 0;
            this.ateDinnerToday = false;
            this.attendedServiceToday = false;
            this.cachedTargetPos = null;
            this.missingHintItem = null;
            this.hintHoldTicks = 0;
        }
    }

    public int getFieldActionsToday() {
        return fieldActionsToday;
    }

    public void incrementFieldActions() {
        this.fieldActionsToday++;
    }

    public int getWorkshopActionsToday() {
        return workshopActionsToday;
    }

    public void incrementWorkshopActions() {
        this.workshopActionsToday++;
    }

    public boolean hasEatenDinnerToday() {
        return ateDinnerToday;
    }

    public void setAteDinnerToday(boolean ate) {
        this.ateDinnerToday = ate;
    }

    public boolean hasAttendedServiceToday() {
        return attendedServiceToday;
    }

    public void setAttendedServiceToday(boolean attended) {
        this.attendedServiceToday = attended;
    }

    public List<ItemStack> getHaul() {
        return haul;
    }

    public void addToHaul(ItemStack stack) {
        if (stack.isEmpty() || haul.size() >= 12) {
            return;
        }
        for (ItemStack existing : haul) {
            if (ItemStack.isSameItemSameTags(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int move = Math.min(space, stack.getCount());
                existing.grow(move);
                stack.shrink(move);
                if (stack.isEmpty()) {
                    return;
                }
            }
        }
        if (!stack.isEmpty()) {
            haul.add(stack.copy());
        }
    }

    @Nullable
    public DayPhase getLastPhase() {
        return lastPhase;
    }

    public void setLastPhase(@Nullable DayPhase phase) {
        if (this.lastPhase != phase) {
            this.lastPhase = phase;
            this.cachedTargetPos = null;
        }
    }

    @Nullable
    public BlockPos getCachedTargetPos() {
        return cachedTargetPos;
    }

    public void setCachedTargetPos(@Nullable BlockPos pos) {
        this.cachedTargetPos = pos;
    }

    public int getActionCooldown() {
        return actionCooldown;
    }

    public void setActionCooldown(int cooldown) {
        this.actionCooldown = Math.max(0, cooldown);
    }

    public void decrementCooldown(int step) {
        if (this.actionCooldown > 0) {
            this.actionCooldown = Math.max(0, this.actionCooldown - step);
        }
    }

    public int getHintHoldTicks() {
        return hintHoldTicks;
    }

    public void setHintHoldTicks(int ticks) {
        this.hintHoldTicks = Math.max(0, ticks);
    }

    public void decrementHintHoldTicks(int step) {
        if (this.hintHoldTicks > 0) {
            this.hintHoldTicks = Math.max(0, this.hintHoldTicks - step);
            if (this.hintHoldTicks == 0) {
                this.missingHintItem = null;
            }
        }
    }

    @Nullable
    public Item getMissingHintItem() {
        return missingHintItem;
    }

    public void setMissingHintItem(@Nullable Item item, int ticks) {
        this.missingHintItem = item;
        this.hintHoldTicks = ticks;
    }

    public boolean isHoldingRoutineItem() {
        return holdingRoutineItem;
    }

    public void setHoldingRoutineItem(boolean holding) {
        this.holdingRoutineItem = holding;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("day", this.routineDay);
        tag.putInt("fieldActions", this.fieldActionsToday);
        tag.putInt("workshopActions", this.workshopActionsToday);
        tag.putBoolean("ateDinner", this.ateDinnerToday);
        tag.putBoolean("attendedService", this.attendedServiceToday);

        ListTag haulList = new ListTag();
        for (ItemStack stack : this.haul) {
            if (!stack.isEmpty()) {
                haulList.add(stack.save(new CompoundTag()));
            }
        }
        tag.put("haul", haulList);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.routineDay = tag.getLong("day");
        this.fieldActionsToday = tag.getInt("fieldActions");
        this.workshopActionsToday = tag.getInt("workshopActions");
        this.ateDinnerToday = tag.getBoolean("ateDinner");
        this.attendedServiceToday = tag.getBoolean("attendedService");
        this.haul.clear();

        if (tag.contains("haul", Tag.TAG_LIST)) {
            ListTag haulList = tag.getList("haul", Tag.TAG_COMPOUND);
            for (int i = 0; i < haulList.size(); i++) {
                ItemStack stack = ItemStack.of(haulList.getCompound(i));
                if (!stack.isEmpty()) {
                    this.haul.add(stack);
                }
            }
        }
    }
}
