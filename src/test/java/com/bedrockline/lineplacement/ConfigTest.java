/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the config schema. These don't load a {@code .toml} from disk; they assert the spec's
 * declared structure and defaults, plus that the null-safe getters fall back to those documented
 * defaults when queried before any config is loaded (the {@code safeGet} path). The NeoForge runtime
 * is on the test classpath via the moddev {@code unitTest} harness, which is what lets us touch
 * {@code ModConfigSpec} here.
 */
class ConfigTest {

    @Test
    @DisplayName("the spec builds")
    void specBuilds() {
        assertNotNull(Config.SPEC, "Config.SPEC should be built by the static initializer");
    }

    @Test
    @DisplayName("every config value is declared at top level with the expected key")
    void valuePaths() {
        assertEquals(List.of("enableLinePlacement"), Config.ENABLE_LINE_PLACEMENT.getPath());
        assertEquals(List.of("enableForBlocksOnly"), Config.ENABLE_FOR_BLOCKS_ONLY.getPath());
        assertEquals(List.of("requireContinuousUseKey"), Config.REQUIRE_CONTINUOUS_USE_KEY.getPath());
        assertEquals(List.of("allowVerticalLocking"), Config.ALLOW_VERTICAL_LOCKING.getPath());
        assertEquals(List.of("enableLineReacharound"), Config.ENABLE_LINE_REACHAROUND.getPath());
        assertEquals(List.of("firstPlacementPauseTicks"), Config.FIRST_PLACEMENT_PAUSE_TICKS.getPath());
        assertEquals(List.of("resetOnItemChange"), Config.RESET_ON_ITEM_CHANGE.getPath());
        assertEquals(List.of("resetOnSneak"), Config.RESET_ON_SNEAK.getPath());
        assertEquals(List.of("debugLogging"), Config.DEBUG_LOGGING.getPath());
    }

    @Test
    @DisplayName("spec defaults match the documented behavior")
    void specDefaults() {
        assertTrue(Config.ENABLE_LINE_PLACEMENT.getDefault());
        assertTrue(Config.ENABLE_FOR_BLOCKS_ONLY.getDefault());
        assertTrue(Config.REQUIRE_CONTINUOUS_USE_KEY.getDefault());
        assertTrue(Config.ALLOW_VERTICAL_LOCKING.getDefault());
        assertTrue(Config.ENABLE_LINE_REACHAROUND.getDefault());
        assertEquals(6, Config.FIRST_PLACEMENT_PAUSE_TICKS.getDefault());
        assertTrue(Config.RESET_ON_ITEM_CHANGE.getDefault());
        assertFalse(Config.RESET_ON_SNEAK.getDefault());
        assertFalse(Config.DEBUG_LOGGING.getDefault());
    }

    @Test
    @DisplayName("null-safe getters return the documented defaults before any config is loaded")
    void gettersFallBackToDefaultsBeforeLoad() {
        // The config is never loaded in the unit-test harness, so every getter exercises its
        // IllegalStateException fallback and must return the documented default.
        assertTrue(Config.enableLinePlacement());
        assertTrue(Config.enableForBlocksOnly());
        assertTrue(Config.requireContinuousUseKey());
        assertTrue(Config.allowVerticalLocking());
        assertTrue(Config.enableLineReacharound());
        assertEquals(6, Config.firstPlacementPauseTicks());
        assertTrue(Config.resetOnItemChange());
        assertFalse(Config.resetOnSneak());
        assertFalse(Config.debugLogging());
    }

    @Test
    @DisplayName("getters agree with the spec defaults (one source of truth)")
    void gettersMirrorSpecDefaults() {
        assertEquals(Config.ENABLE_LINE_PLACEMENT.getDefault(), Config.enableLinePlacement());
        assertEquals(Config.ENABLE_FOR_BLOCKS_ONLY.getDefault(), Config.enableForBlocksOnly());
        assertEquals(Config.REQUIRE_CONTINUOUS_USE_KEY.getDefault(), Config.requireContinuousUseKey());
        assertEquals(Config.ALLOW_VERTICAL_LOCKING.getDefault(), Config.allowVerticalLocking());
        assertEquals(Config.ENABLE_LINE_REACHAROUND.getDefault(), Config.enableLineReacharound());
        assertEquals(Config.FIRST_PLACEMENT_PAUSE_TICKS.getDefault(), Config.firstPlacementPauseTicks());
        assertEquals(Config.RESET_ON_ITEM_CHANGE.getDefault(), Config.resetOnItemChange());
        assertEquals(Config.RESET_ON_SNEAK.getDefault(), Config.resetOnSneak());
        assertEquals(Config.DEBUG_LOGGING.getDefault(), Config.debugLogging());
    }
}
