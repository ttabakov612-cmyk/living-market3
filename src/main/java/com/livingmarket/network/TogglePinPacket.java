package com.livingmarket.network;

import com.livingmarket.api.IVillagerMarketExt;
import com.livingmarket.mixin.MerchantMenuAccessor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TogglePinPacket {
    private final int offerIndex;

    public TogglePinPacket(int offerIndex) {
        this.offerIndex = offerIndex;
    }

    public static void encode(TogglePinPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.offerIndex);
    }

    public static TogglePinPacket decode(FriendlyByteBuf buf) {
        return new TogglePinPacket(buf.readVarInt());
    }

    public static void handle(TogglePinPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof MerchantMenu menu) {
            Merchant trader = ((MerchantMenuAccessor) menu).livingmarket$getTrader();
            if (trader instanceof Villager villager) {
                IVillagerMarketExt ext = (IVillagerMarketExt) villager;
                if (ext.livingmarket$togglePin(msg.offerIndex)) {
                    villager.level().playSound(
                            null,
                            villager.blockPosition(),
                            SoundEvents.UI_BUTTON_CLICK.value(),
                            SoundSource.NEUTRAL,
                            0.6F,
                            1.2F
                    );
                    player.sendMerchantOffers(
                            menu.containerId,
                            villager.getOffers(),
                            villager.getVillagerData().getLevel(),
                            villager.getVillagerXp(),
                            villager.showProgressBar(),
                            villager.canRestock()
                    );
                }
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
