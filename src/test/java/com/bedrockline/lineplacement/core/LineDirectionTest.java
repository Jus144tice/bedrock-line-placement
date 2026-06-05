/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LineDirection} — the pure axis/sign → face + unit-step
 * mapping used by Line Reacharound. The constant names mirror Minecraft's
 * {@code Direction} so the client layer can map by name.
 */
class LineDirectionTest {

    @Test
    void mapsEachAxisAndSignToTheExpectedFace() {
        assertEquals(LineDirection.EAST, LineDirection.of(LockAxis.X, 1));
        assertEquals(LineDirection.WEST, LineDirection.of(LockAxis.X, -1));
        assertEquals(LineDirection.UP, LineDirection.of(LockAxis.Y, 1));
        assertEquals(LineDirection.DOWN, LineDirection.of(LockAxis.Y, -1));
        assertEquals(LineDirection.SOUTH, LineDirection.of(LockAxis.Z, 1));
        assertEquals(LineDirection.NORTH, LineDirection.of(LockAxis.Z, -1));
    }

    @Test
    void normalizesAnyPositiveOrNegativeMagnitude() {
        // of() uses the sign, not the magnitude.
        assertEquals(LineDirection.EAST, LineDirection.of(LockAxis.X, 5));
        assertEquals(LineDirection.NORTH, LineDirection.of(LockAxis.Z, -9));
    }

    @Test
    void unitStepMatchesTheFace() {
        assertEquals(new GridPos(1, 0, 0), LineDirection.EAST.step());
        assertEquals(new GridPos(-1, 0, 0), LineDirection.WEST.step());
        assertEquals(new GridPos(0, 1, 0), LineDirection.UP.step());
        assertEquals(new GridPos(0, -1, 0), LineDirection.DOWN.step());
        assertEquals(new GridPos(0, 0, 1), LineDirection.SOUTH.step());
        assertEquals(new GridPos(0, 0, -1), LineDirection.NORTH.step());
    }

    @Test
    void axisAndSignAccessorsRoundTrip() {
        for (LineDirection d : LineDirection.values()) {
            assertEquals(d, LineDirection.of(d.axis(), d.sign()));
        }
    }

    @Test
    void zeroSignHasNoDirection() {
        // A sign of 0 means "no direction locked yet" — there is no face for it.
        assertThrows(IllegalArgumentException.class, () -> LineDirection.of(LockAxis.X, 0));
    }
}
