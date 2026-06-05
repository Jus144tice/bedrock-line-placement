/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.mixin;

import com.bedrockline.lineplacement.client.LineLockManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The single vanilla hook for this mod.
 *
 * <p>{@code MultiPlayerGameMode#useItemOn} is the client method that turns a
 * right-click on a block into a {@code ServerboundUseItemOnPacket}. By deciding
 * <em>before</em> that packet is built, we can cancel an off-line placement
 * without ever touching reach, timing, or sending any non-vanilla packet.</p>
 *
 * <p>Two tiny hooks, both fragile to mapping changes — keep them thin:</p>
 * <ul>
 *   <li><b>HEAD</b> (cancellable): ask the policy; if it says suppress, return
 *       {@code FAIL} so vanilla sends nothing. A suppressed block item does
 *       nothing in-air, so this is side-effect free.</li>
 *   <li><b>RETURN</b>: if vanilla actually consumed the action (a block was
 *       placed), record the placement so the policy can establish/extend the
 *       line.</li>
 * </ul>
 *
 * <p>Note: when HEAD cancels, the RETURN injection does not run — and the manager
 * only records a position it explicitly stashed during an allowed HEAD pass, so
 * the two hooks cannot get out of sync.</p>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void blp$decidePlacement(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult result,
            CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult suppressed = LineLockManager.INSTANCE.preUseItemOn(player, hand, result);
        if (suppressed != null) {
            cir.setReturnValue(suppressed);
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void blp$recordPlacement(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult result,
            CallbackInfoReturnable<InteractionResult> cir) {
        LineLockManager.INSTANCE.postUseItemOn(cir.getReturnValue());
    }
}
