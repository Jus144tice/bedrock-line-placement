/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.client;

import com.bedrockline.lineplacement.BedrockLinePlacement;
import com.bedrockline.lineplacement.Config;
import com.bedrockline.lineplacement.core.Decision;
import com.bedrockline.lineplacement.core.GridPos;
import com.bedrockline.lineplacement.core.LinePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Client-side glue between Minecraft's placement code and the pure
 * {@link LinePolicy}. There is exactly one instance ({@link #INSTANCE}); it is
 * only ever touched from the client thread.
 *
 * <p>The mixin calls {@link #preUseItemOn} before vanilla sends the placement
 * packet and {@link #postUseItemOn} once vanilla has produced a result. The
 * {@link ClientEvents} tick handler calls {@link #reset} on the various reset
 * triggers (key release, item change, screen open, dimension change, ...).</p>
 */
public final class LineLockManager {

    public static final LineLockManager INSTANCE = new LineLockManager();

    private final LinePolicy policy = new LinePolicy();

    /** Predicted placement position stashed between the HEAD and RETURN hooks. */
    private GridPos pendingPlacePos;

    private boolean pendingAllowVertical;

    /**
     * Ticks left in the Bedrock-style "settle" pause after the first block of a
     * line. While this is positive (and we are not yet locked) placements are held
     * back so the player has time to start moving in their intended direction.
     */
    private int pauseTicksRemaining;

    private LineLockManager() {}

    /**
     * Decide whether a {@code useItemOn} call should be suppressed.
     *
     * @return a non-null {@link InteractionResult} to <em>cancel</em> the vanilla
     *         placement with that result, or {@code null} to let vanilla proceed.
     */
    public InteractionResult preUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        pendingPlacePos = null;

        if (!Config.enableLinePlacement()) {
            return null;
        }
        // Only engage while the use key is held, if configured. This also keeps us
        // out of the way of one-off right-click interactions.
        if (Config.requireContinuousUseKey()
                && !Minecraft.getInstance().options.keyUse.isDown()) {
            return null;
        }

        ItemStack stack = player.getItemInHand(hand);
        // We can only reason about block placement, and only predict a position for
        // BlockItems. Anything else falls straight through to vanilla.
        if (!(stack.getItem() instanceof BlockItem)) {
            return null;
        }

        GridPos place;
        try {
            // Replicate vanilla's placement target exactly (handles replaceable
            // blocks, e.g. tall grass / snow layers) without placing anything.
            BlockPlaceContext ctx = new BlockPlaceContext(player, hand, stack, hit);
            BlockPos pos = ctx.getClickedPos();
            place = new GridPos(pos.getX(), pos.getY(), pos.getZ());
        } catch (Throwable t) {
            // Complex/edge placement contexts: never crash, just defer to vanilla.
            debug("placement-context prediction failed, deferring to vanilla: " + t);
            return null;
        }

        // Bedrock-style initial pause: after the anchor, hold placements briefly so
        // the player can get moving before the second block locks the direction.
        if (pauseTicksRemaining > 0 && !policy.isLocked()) {
            debug("initial pause: " + pauseTicksRemaining + " tick(s) remaining");
            return InteractionResult.FAIL;
        }

        Decision decision = policy.decide(place);
        if (decision == Decision.SUPPRESS) {
            debug("suppress off-line placement at " + place + " (axis=" + policy.axis() + " sign=" + policy.sign()
                    + " ref=" + policy.lineRef() + ")");
            return InteractionResult.FAIL;
        }

        // Allowed: remember the target so we can record it if vanilla confirms it.
        pendingPlacePos = place;
        pendingAllowVertical = Config.allowVerticalLocking();
        return null;
    }

    /**
     * Called at the return of {@code useItemOn}. If vanilla actually consumed the
     * action (a block was placed) we record the placement, which may establish
     * the line lock.
     */
    public void postUseItemOn(InteractionResult result) {
        GridPos place = pendingPlacePos;
        pendingPlacePos = null;
        if (place == null || result == null) {
            return;
        }
        if (result.consumesAction()) {
            boolean wasEmpty = !policy.hasAnchor();
            boolean wasLocked = policy.isLocked();
            policy.record(place, pendingAllowVertical);

            if (wasEmpty && policy.hasAnchor()) {
                // This was the anchor (first block of a new line): start the settle pause.
                pauseTicksRemaining = Config.firstPlacementPauseTicks();
                debug("anchor placed at " + place + "; pausing " + pauseTicksRemaining + " tick(s)");
            } else if (!wasLocked && policy.isLocked()) {
                debug("locked line: axis=" + policy.axis() + " sign=" + policy.sign() + " through " + policy.lineRef());
            } else {
                debug("recorded placement " + place + " (locked=" + policy.isLocked() + ")");
            }
        }
    }

    /** Advances the initial-pause countdown. Call once per client tick. */
    public void clientTick() {
        if (pauseTicksRemaining > 0) {
            pauseTicksRemaining--;
        }
    }

    /** Clears the lock. Cheap and idempotent; only logs when there was state. */
    public void reset(String reason) {
        if (policy.isActive()) {
            debug("reset (" + reason + ")");
        }
        policy.reset();
        pendingPlacePos = null;
        pauseTicksRemaining = 0;
    }

    public boolean isLocked() {
        return policy.isLocked();
    }

    private static void debug(String message) {
        if (Config.debugLogging()) {
            BedrockLinePlacement.LOGGER.info("[BLP] {}", message);
        }
    }
}
