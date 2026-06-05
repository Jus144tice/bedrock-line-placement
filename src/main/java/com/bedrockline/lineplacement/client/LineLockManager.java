/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.client;

import com.bedrockline.lineplacement.BedrockLinePlacement;
import com.bedrockline.lineplacement.Config;
import com.bedrockline.lineplacement.core.Decision;
import com.bedrockline.lineplacement.core.GridPos;
import com.bedrockline.lineplacement.core.LineDirection;
import com.bedrockline.lineplacement.core.LinePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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

    /**
     * Ticks between reacharound placements, matching vanilla's held-use placement
     * cadence ({@code Minecraft#rightClickDelay} is set to 4 while the use key is
     * held). Every confirmed placement (normal or reacharound) re-arms this, so
     * reacharound never outpaces vanilla continuous placement.
     */
    private static final int REACHAROUND_COOLDOWN_TICKS = 4;

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

    /**
     * Ticks until Line Reacharound may attempt another placement. Decremented each
     * client tick; re-armed to {@link #REACHAROUND_COOLDOWN_TICKS} on every
     * confirmed placement so the feature stays at or below vanilla's cadence.
     */
    private int reacharoundCooldown;

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

            // Re-arm the reacharound cadence on every confirmed placement (normal or
            // reacharound), so reacharound never places faster than vanilla held-use.
            reacharoundCooldown = REACHAROUND_COOLDOWN_TICKS;

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

    /** Advances the initial-pause and reacharound-cadence countdowns. Call once per client tick. */
    public void clientTick() {
        if (pauseTicksRemaining > 0) {
            pauseTicksRemaining--;
        }
        if (reacharoundCooldown > 0) {
            reacharoundCooldown--;
        }
    }

    /**
     * Line Reacharound: when a line is locked and the player is holding use but
     * their crosshair is not landing the next block on the line, infer the lead
     * block and continue the line from its forward face. Called once per client
     * tick (after the reset checks) from {@link ClientEvents}.
     *
     * <p>This never bypasses vanilla: it synthesizes the exact {@link BlockHitResult}
     * the player would produce by aiming at the lead block's forward face and routes
     * it through the normal {@code useItemOn} path (which re-enters this class's own
     * mixin, so the policy still decides and records). Reach, collision, inventory
     * and server validation are all unchanged. It is strictly cadence-limited to
     * vanilla's held-use rate and only ever attempts a single placement per tick.</p>
     *
     * <p>It does nothing — deferring to vanilla — when reacharound or line placement
     * is disabled, no line is locked, the settle pause is active, the use key is not
     * held, no block item is held, the lead block is missing/unloaded, the next cell
     * is not replaceable, the lead face is out of reach, or the player's own crosshair
     * is already aimed to place the next block.</p>
     */
    public void tryReacharound() {
        if (reacharoundCooldown > 0) {
            return; // cadence-limited (decremented in clientTick)
        }
        if (!Config.enableLinePlacement() || !Config.enableLineReacharound()) {
            return;
        }
        if (!policy.isLocked() || pauseTicksRemaining > 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        // Reacharound is a "keep holding use to continue the line" feature.
        if (!mc.options.keyUse.isDown()) {
            return;
        }

        // Only ever synthesize a *block* placement, for a hand holding a block item.
        InteractionHand hand = blockHand(player);
        if (hand == null) {
            return;
        }

        GridPos leadGrid = policy.leadBlock();
        GridPos nextGrid = policy.reacharoundNext();
        LineDirection dir = policy.direction();
        if (leadGrid == null || nextGrid == null || dir == null) {
            return;
        }

        Level level = player.level();
        BlockPos lead = new BlockPos(leadGrid.x(), leadGrid.y(), leadGrid.z());
        BlockPos next = new BlockPos(nextGrid.x(), nextGrid.y(), nextGrid.z());

        // The world must back the inferred line: a solid lead block to click against and
        // a replaceable cell ahead of it. If the lead is gone (mined / replaced / not
        // loaded), do nothing and let vanilla behaviour take over.
        if (!level.isLoaded(lead) || !level.isLoaded(next)) {
            return;
        }
        if (level.getBlockState(lead).isAir()) {
            debug("reacharound skip: lead block missing at " + leadGrid);
            return;
        }
        if (!level.getBlockState(next).canBeReplaced()) {
            return; // next cell already occupied
        }

        // If the player's own crosshair is already lined up to place the next block,
        // let vanilla do it — never double up.
        if (vanillaWouldPlaceAt(player, hand, nextGrid)) {
            return;
        }

        Direction face = Direction.valueOf(dir.name());
        Vec3i normal = face.getNormal();
        Vec3 faceCenter = Vec3.atCenterOf(lead).add(normal.getX() * 0.5, normal.getY() * 0.5, normal.getZ() * 0.5);

        // Respect vanilla block-interaction reach; never extend it.
        double reach = player.blockInteractionRange();
        if (player.getEyePosition().distanceToSqr(faceCenter) > reach * reach) {
            return;
        }

        // Synthesize the interaction the player would make by aiming at the lead block's
        // forward face. This routes through our useItemOn mixin: the policy sees the
        // next (on-line, contiguous) position, allows it, and records it on RETURN —
        // advancing the lead exactly like a normal placement. Vanilla/the server still
        // decide whether the placement actually happens.
        BlockHitResult hit = new BlockHitResult(faceCenter, face, lead, false);
        InteractionResult result = mc.gameMode.useItemOn(player, hand, hit);
        if (result != null && result.consumesAction()) {
            player.swing(hand); // arm-swing feedback; postUseItemOn already re-armed the cooldown
            debug("reacharound placed at " + nextGrid + " against " + face + " face of " + leadGrid);
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
        reacharoundCooldown = 0;
    }

    public boolean isLocked() {
        return policy.isLocked();
    }

    /** The hand holding a {@link BlockItem} (main preferred), or {@code null} if neither does. */
    private static InteractionHand blockHand(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof BlockItem) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() instanceof BlockItem) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /**
     * True if the player's current crosshair target would itself place a block at
     * {@code target} (using vanilla's own {@link BlockPlaceContext} prediction). When
     * so, reacharound stands down and lets the normal placement path handle it.
     */
    private static boolean vanillaWouldPlaceAt(LocalPlayer player, InteractionHand hand, GridPos target) {
        HitResult hr = Minecraft.getInstance().hitResult;
        if (!(hr instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        try {
            BlockPlaceContext ctx = new BlockPlaceContext(player, hand, player.getItemInHand(hand), blockHit);
            BlockPos pos = ctx.getClickedPos();
            return pos.getX() == target.x() && pos.getY() == target.y() && pos.getZ() == target.z();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void debug(String message) {
        if (Config.debugLogging()) {
            BedrockLinePlacement.LOGGER.info("[BLP] {}", message);
        }
    }
}
