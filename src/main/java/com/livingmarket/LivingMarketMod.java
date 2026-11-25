package com.livingmarket;

import com.livingmarket.config.MarketConfig;
import com.livingmarket.data.BiomeMarketManager;
import com.livingmarket.data.MarketTradeManager;
import com.livingmarket.network.MarketNetwork;
import com.livingmarket.routine.WorkshopPlacementEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(LivingMarketMod.MODID)
public class LivingMarketMod {
    public static final String MODID = "livingmarket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LivingMarketMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MarketConfig.SPEC);
        modEventBus.addListener(this::onCommonSetup);

        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.register(WorkshopPlacementEvents.class);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(MarketNetwork::register);
    }

    private void onAddReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(MarketTradeManager.INSTANCE);
        event.addListener(BiomeMarketManager.INSTANCE);
    }
}
