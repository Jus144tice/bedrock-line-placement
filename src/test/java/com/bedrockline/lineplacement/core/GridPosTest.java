/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/** Unit tests for the Minecraft-free {@link GridPos} value type used by {@link LinePolicy}. */
class GridPosTest {

    @Test
    void exposesItsComponents() {
        GridPos p = new GridPos(1, -2, 3);
        assertEquals(1, p.x());
        assertEquals(-2, p.y());
        assertEquals(3, p.z());
    }

    @Test
    void minusIsComponentWiseSubtraction() {
        GridPos a = new GridPos(5, 7, 9);
        GridPos b = new GridPos(1, 2, 3);
        assertEquals(new GridPos(4, 5, 6), a.minus(b));
    }

    @Test
    void minusCanProduceNegativeDeltas() {
        GridPos a = new GridPos(0, 64, 0);
        GridPos b = new GridPos(0, 64, 1);
        // The locking vector for a -Z step: delta is (0, 0, -1).
        assertEquals(new GridPos(0, 0, -1), a.minus(b));
    }

    @Test
    void minusOfSelfIsZero() {
        GridPos a = new GridPos(3, 4, 5);
        assertEquals(new GridPos(0, 0, 0), a.minus(a));
    }

    @Test
    void plusIsComponentWiseAddition() {
        GridPos a = new GridPos(1, 2, 3);
        GridPos b = new GridPos(4, 5, 6);
        assertEquals(new GridPos(5, 7, 9), a.plus(b));
    }

    @Test
    void plusWithAUnitStepWalksToTheNextBlock() {
        // How Line Reacharound advances: lead + direction step = next block.
        GridPos lead = new GridPos(10, 64, 0);
        assertEquals(new GridPos(11, 64, 0), lead.plus(new GridPos(1, 0, 0))); // +X / east
        assertEquals(new GridPos(10, 65, 0), lead.plus(new GridPos(0, 1, 0))); // +Y / up
        assertEquals(new GridPos(10, 64, -1), lead.plus(new GridPos(0, 0, -1))); // -Z / north
    }

    @Test
    void plusAndMinusAreInverse() {
        GridPos a = new GridPos(7, -3, 2);
        GridPos d = new GridPos(1, 0, -1);
        assertEquals(a, a.plus(d).minus(d));
    }

    @Test
    void valueEqualityAndHashing() {
        // Records give value semantics, which LinePolicy relies on (e.g. pos.equals(prev)).
        assertEquals(new GridPos(1, 2, 3), new GridPos(1, 2, 3));
        assertEquals(new GridPos(1, 2, 3).hashCode(), new GridPos(1, 2, 3).hashCode());
        assertNotEquals(new GridPos(1, 2, 3), new GridPos(1, 2, 4));
    }
}
