/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link LockAxis#of(GridPos)} — the coordinate selector each axis exposes. */
class LockAxisTest {

    private static final GridPos P = new GridPos(10, 20, 30);

    @Test
    void xSelectsTheXCoordinate() {
        assertEquals(10, LockAxis.X.of(P));
    }

    @Test
    void ySelectsTheYCoordinate() {
        assertEquals(20, LockAxis.Y.of(P));
    }

    @Test
    void zSelectsTheZCoordinate() {
        assertEquals(30, LockAxis.Z.of(P));
    }

    @Test
    void readsNegativeCoordinates() {
        GridPos n = new GridPos(-1, -2, -3);
        assertEquals(-1, LockAxis.X.of(n));
        assertEquals(-2, LockAxis.Y.of(n));
        assertEquals(-3, LockAxis.Z.of(n));
    }
}
