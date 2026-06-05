/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config for Bedrock Line Placement. Registered as a CLIENT-type config,
 * so it is stored in {@code config/bedrocklineplacement-client.toml}.
 *
 * <p>All getters are null-safe: if they are ever queried before the config is
 * loaded they fall back to the documented defaults instead of throwing.</p>
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_LINE_PLACEMENT = BUILDER.comment(
                    "Master switch. When false the mod does nothing and vanilla placement is untouched.")
            .define("enableLinePlacement", true);

    public static final ModConfigSpec.BooleanValue ENABLE_FOR_BLOCKS_ONLY = BUILDER.comment(
                    "Only constrain placement when the held item is a BlockItem.",
                    "Leave true to avoid interfering with food, tools, buckets, etc.")
            .define("enableForBlocksOnly", true);

    public static final ModConfigSpec.BooleanValue REQUIRE_CONTINUOUS_USE_KEY = BUILDER.comment(
                    "Only lock while the use/place key is actually held down.",
                    "Releasing the key clears the lock so the next sequence starts fresh.")
            .define("requireContinuousUseKey", true);

    public static final ModConfigSpec.BooleanValue ALLOW_VERTICAL_LOCKING = BUILDER.comment(
                    "Allow locking onto the vertical (Y) axis for towers/pillars.")
            .define("allowVerticalLocking", true);

    public static final ModConfigSpec.IntValue FIRST_PLACEMENT_PAUSE_TICKS = BUILDER.comment(
                    "Bedrock-style pause (in ticks, 20 = 1 second) after the FIRST block of a",
                    "line before the second block is allowed. This gives you a moment to start",
                    "moving so the line locks in the direction you intend. Set to 0 to disable.")
            .defineInRange("firstPlacementPauseTicks", 6, 0, 40);

    public static final ModConfigSpec.BooleanValue RESET_ON_ITEM_CHANGE = BUILDER.comment(
                    "Clear the lock when the selected hotbar slot or held item changes.")
            .define("resetOnItemChange", true);

    public static final ModConfigSpec.BooleanValue RESET_ON_SNEAK = BUILDER.comment(
                    "Clear the lock when the player starts or stops sneaking.",
                    "Off by default so sneaking keeps working normally during a line.")
            .define("resetOnSneak", false);

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER.comment(
                    "Log lock/suppress decisions to the client log. For troubleshooting only.")
            .define("debugLogging", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    private static boolean safeGet(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    public static boolean enableLinePlacement() {
        return safeGet(ENABLE_LINE_PLACEMENT, true);
    }

    public static boolean enableForBlocksOnly() {
        return safeGet(ENABLE_FOR_BLOCKS_ONLY, true);
    }

    public static boolean requireContinuousUseKey() {
        return safeGet(REQUIRE_CONTINUOUS_USE_KEY, true);
    }

    public static boolean allowVerticalLocking() {
        return safeGet(ALLOW_VERTICAL_LOCKING, true);
    }

    public static int firstPlacementPauseTicks() {
        try {
            return FIRST_PLACEMENT_PAUSE_TICKS.get();
        } catch (IllegalStateException notLoadedYet) {
            return 6;
        }
    }

    public static boolean resetOnItemChange() {
        return safeGet(RESET_ON_ITEM_CHANGE, true);
    }

    public static boolean resetOnSneak() {
        return safeGet(RESET_ON_SNEAK, false);
    }

    public static boolean debugLogging() {
        return safeGet(DEBUG_LOGGING, false);
    }
}
