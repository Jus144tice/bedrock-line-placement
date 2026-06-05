/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement;

import com.bedrockline.lineplacement.client.ClientEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint for Bedrock Line Placement.
 *
 * <p>This is a strictly client-side mod ({@code dist = Dist.CLIENT}): it hooks
 * the client's block-placement code via a mixin and only ever <em>suppresses</em>
 * placement attempts. It never sends extra packets, so vanilla servers neither
 * know nor care that it is installed.</p>
 */
@Mod(value = BedrockLinePlacement.MODID, dist = Dist.CLIENT)
public final class BedrockLinePlacement {

    public static final String MODID = "bedrocklineplacement";
    public static final Logger LOGGER = LoggerFactory.getLogger("BedrockLinePlacement");

    public BedrockLinePlacement(IEventBus modEventBus, ModContainer modContainer) {
        // Register the client config (config/bedrocklineplacement-client.toml).
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // Register the per-tick reset watcher on the game event bus.
        NeoForge.EVENT_BUS.register(ClientEvents.class);

        LOGGER.info("Bedrock Line Placement loaded (client-only).");
    }
}
