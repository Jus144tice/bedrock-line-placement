/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.client;

import com.bedrockline.lineplacement.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Watches client state once per tick and clears the line lock on any of the
 * documented reset triggers. Keeping all reset logic here (rather than spread
 * across hooks) makes the lifecycle easy to reason about.
 *
 * <p>This intentionally does <b>not</b> place or cancel anything — it only calls
 * {@link LineLockManager#reset(String)}.</p>
 */
public final class ClientEvents {

    private static boolean wasUseDown = false;
    private static boolean hadScreen = false;
    private static int lastSlot = -1;
    private static Item lastItem = null;
    private static boolean wasSneaking = false;
    private static ResourceKey<Level> lastDimension = null;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            // Left the world / disconnected.
            LineLockManager.INSTANCE.reset("no player");
            resetTrackers();
            return;
        }

        // Advance the Bedrock-style settle pause after the first block.
        LineLockManager.INSTANCE.clientTick();

        // A screen opening (inventory, container, sign edit, ...) ends the sequence.
        boolean hasScreen = mc.screen != null;
        if (hasScreen && !hadScreen) {
            LineLockManager.INSTANCE.reset("screen opened");
        }
        hadScreen = hasScreen;

        // Use key released (only relevant when we require the key to be held).
        boolean useDown = mc.options.keyUse.isDown();
        if (Config.requireContinuousUseKey() && wasUseDown && !useDown) {
            LineLockManager.INSTANCE.reset("use key released");
        }
        wasUseDown = useDown;

        // Selected slot or held item changed.
        int slot = player.getInventory().selected;
        Item item = player.getInventory().getSelected().getItem();
        if (Config.resetOnItemChange() && (slot != lastSlot || item != lastItem) && lastItem != null) {
            LineLockManager.INSTANCE.reset("item changed");
        }
        lastSlot = slot;
        lastItem = item;

        // If we only operate on blocks, clear the lock when the held item is not one.
        if (Config.enableForBlocksOnly() && !(item instanceof BlockItem)) {
            LineLockManager.INSTANCE.reset("held item is not a block");
        }

        // Sneak toggled (optional).
        boolean sneaking = player.isShiftKeyDown();
        if (Config.resetOnSneak() && sneaking != wasSneaking) {
            LineLockManager.INSTANCE.reset("sneak toggled");
        }
        wasSneaking = sneaking;

        // Dimension / world change.
        ResourceKey<Level> dimension = player.level().dimension();
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            LineLockManager.INSTANCE.reset("dimension changed");
        }
        lastDimension = dimension;

        // After all reset triggers: if a line is still locked and the player is holding
        // use, let Line Reacharound continue the line from the lead block's forward face
        // (no-op unless the feature is enabled and all its guards pass). Running last
        // means a lock cleared this tick will not reacharound.
        LineLockManager.INSTANCE.tryReacharound();
    }

    private static void resetTrackers() {
        wasUseDown = false;
        hadScreen = false;
        lastSlot = -1;
        lastItem = null;
        wasSneaking = false;
        lastDimension = null;
    }
}
