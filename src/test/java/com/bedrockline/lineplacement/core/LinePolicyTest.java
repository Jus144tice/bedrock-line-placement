/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LinePolicy}. These run without Minecraft because the
 * policy operates purely on {@link GridPos}.
 *
 * <p>Convention used in these tests: a "placement" is both decided and then,
 * if allowed, recorded — mirroring the client glue (decide on the use packet,
 * record on a confirmed placement).</p>
 */
class LinePolicyTest {

    private static final boolean VERTICAL_ON = true;
    private static final boolean VERTICAL_OFF = false;

    /** Helper: decide then record (when allowed), like the real client flow. */
    private static Decision place(LinePolicy p, GridPos pos, boolean allowVertical) {
        Decision d = p.decide(pos);
        if (d == Decision.ALLOW) {
            p.record(pos, allowVertical);
        }
        return d;
    }

    private static GridPos at(int x, int y, int z) {
        return new GridPos(x, y, z);
    }

    // ---- X axis ----------------------------------------------------------

    @Test
    void locksOnPositiveXAndAllowsContinuation() {
        LinePolicy p = new LinePolicy();
        assertEquals(Decision.ALLOW, place(p, at(0, 64, 0), VERTICAL_ON)); // anchor
        assertEquals(Decision.ALLOW, place(p, at(1, 64, 0), VERTICAL_ON)); // establishes +X
        assertTrue(p.isLocked());
        assertEquals(LockAxis.X, p.axis());
        assertEquals(1, p.sign());

        // Further along +X is allowed.
        assertEquals(Decision.ALLOW, place(p, at(2, 64, 0), VERTICAL_ON));
        assertEquals(Decision.ALLOW, place(p, at(3, 64, 0), VERTICAL_ON));
    }

    @Test
    void suppressesSidewaysDriftOnXLock() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(1, 64, 0), VERTICAL_ON); // lock +X

        // Drift in Z (off the line) is suppressed.
        assertEquals(Decision.SUPPRESS, place(p, at(2, 64, 1), VERTICAL_ON));
        // Drift in Y (off the line) is suppressed.
        assertEquals(Decision.SUPPRESS, place(p, at(2, 65, 0), VERTICAL_ON));
        // Still on the line afterwards: allowed.
        assertEquals(Decision.ALLOW, place(p, at(2, 64, 0), VERTICAL_ON));
    }

    @Test
    void suppressesWrongDirectionOnXLock() {
        LinePolicy p = new LinePolicy();
        place(p, at(5, 64, 0), VERTICAL_ON);
        place(p, at(6, 64, 0), VERTICAL_ON); // lock +X, lineRef at x=5

        // x=5 is lineRef itself (delta 0) and x<5 goes backwards: both suppressed.
        assertEquals(Decision.SUPPRESS, p.decide(at(5, 64, 0)));
        assertEquals(Decision.SUPPRESS, p.decide(at(4, 64, 0)));
    }

    @Test
    void locksOnNegativeX() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(-1, 64, 0), VERTICAL_ON); // establishes -X
        assertEquals(LockAxis.X, p.axis());
        assertEquals(-1, p.sign());
        assertEquals(Decision.ALLOW, p.decide(at(-2, 64, 0)));
        assertEquals(Decision.SUPPRESS, p.decide(at(1, 64, 0)));
    }

    @Test
    void requiresContiguousPlacementNoGaps() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(1, 64, 0), VERTICAL_ON); // lock +X, frontier at x=1

        // Skipping ahead (a gap) is suppressed: cursor drifted off then aimed
        // further down the line.
        assertEquals(Decision.SUPPRESS, p.decide(at(3, 64, 0)));
        assertEquals(Decision.SUPPRESS, p.decide(at(2 + 5, 64, 0)));

        // Only the single next cell (frontier + 1 = x=2) is allowed.
        assertEquals(Decision.ALLOW, place(p, at(2, 64, 0), VERTICAL_ON));
        // Frontier advanced; now x=3 is the next allowed cell, x=4 is still a gap.
        assertEquals(Decision.SUPPRESS, p.decide(at(4, 64, 0)));
        assertEquals(Decision.ALLOW, place(p, at(3, 64, 0), VERTICAL_ON));
    }

    // ---- Z axis ----------------------------------------------------------

    @Test
    void locksOnZAndSuppressesDrift() {
        LinePolicy p = new LinePolicy();
        place(p, at(10, 70, 10), VERTICAL_ON);
        place(p, at(10, 70, 11), VERTICAL_ON); // establishes +Z
        assertEquals(LockAxis.Z, p.axis());
        assertEquals(1, p.sign());

        assertEquals(Decision.ALLOW, p.decide(at(10, 70, 12)));
        assertEquals(Decision.SUPPRESS, p.decide(at(11, 70, 12))); // X drift
        assertEquals(Decision.SUPPRESS, p.decide(at(10, 71, 12))); // Y drift
        assertEquals(Decision.SUPPRESS, p.decide(at(10, 70, 9))); // wrong direction
    }

    // ---- Y axis ----------------------------------------------------------

    @Test
    void locksOnYWhenVerticalEnabled() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(0, 65, 0), VERTICAL_ON); // establishes +Y
        assertEquals(LockAxis.Y, p.axis());
        assertEquals(1, p.sign());
        assertEquals(Decision.ALLOW, p.decide(at(0, 66, 0)));
        assertEquals(Decision.SUPPRESS, p.decide(at(1, 66, 0))); // off the vertical line
        assertEquals(Decision.SUPPRESS, p.decide(at(0, 63, 0))); // wrong direction
    }

    @Test
    void doesNotLockOnYWhenVerticalDisabled() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_OFF);
        // Pure vertical step: cannot lock (Y ignored, X/Z both zero).
        assertEquals(Decision.ALLOW, place(p, at(0, 65, 0), VERTICAL_OFF));
        assertFalse(p.isLocked());
        // A subsequent horizontal step still establishes a horizontal lock.
        assertEquals(Decision.ALLOW, place(p, at(1, 65, 0), VERTICAL_OFF));
        assertEquals(LockAxis.X, p.axis());
    }

    // ---- no-lock fallback ------------------------------------------------

    @Test
    void diagonalSecondStepDoesNotLock() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        // Equal X and Z movement: ambiguous diagonal, must not lock.
        assertEquals(Decision.ALLOW, place(p, at(1, 64, 1), VERTICAL_ON));
        assertFalse(p.isLocked());
        // After the anchor advances, a clean step locks normally.
        assertEquals(Decision.ALLOW, place(p, at(2, 64, 1), VERTICAL_ON));
        assertTrue(p.isLocked());
        assertEquals(LockAxis.X, p.axis());
    }

    @Test
    void gappedSecondStepDoesNotLock() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        // A 2-block jump on a single axis is a gap, not a clean unit step: no lock.
        assertEquals(Decision.ALLOW, place(p, at(2, 64, 0), VERTICAL_ON));
        assertFalse(p.isLocked());
        // The next adjacent step locks normally.
        assertEquals(Decision.ALLOW, place(p, at(3, 64, 0), VERTICAL_ON));
        assertTrue(p.isLocked());
        assertEquals(LockAxis.X, p.axis());
    }

    @Test
    void firstPlacementAlwaysAllowed() {
        LinePolicy p = new LinePolicy();
        assertEquals(Decision.ALLOW, p.decide(at(123, 45, 678)));
        assertFalse(p.isLocked());
    }

    // ---- reset behaviour -------------------------------------------------

    @Test
    void resetClearsLockAndAllowsNewAxis() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(1, 64, 0), VERTICAL_ON); // lock +X
        assertTrue(p.isLocked());

        p.reset();
        assertFalse(p.isLocked());
        assertFalse(p.isActive());
        assertNull(p.axis());

        // A brand new sequence can lock onto a different axis.
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(0, 64, 1), VERTICAL_ON); // lock +Z
        assertEquals(LockAxis.Z, p.axis());
    }

    @Test
    void lockedAxisDoesNotSwitchMidSequence() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(1, 64, 0), VERTICAL_ON); // lock +X

        // Attempts implying a Z line are suppressed, not re-locked.
        assertEquals(Decision.SUPPRESS, place(p, at(1, 64, 1), VERTICAL_ON));
        assertEquals(LockAxis.X, p.axis()); // unchanged
        assertEquals(1, p.sign());
    }

    // ---- negative-direction locks ----------------------------------------

    @Test
    void locksOnNegativeZ() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(0, 64, -1), VERTICAL_ON); // establishes -Z
        assertEquals(LockAxis.Z, p.axis());
        assertEquals(-1, p.sign());
        assertEquals(Decision.ALLOW, p.decide(at(0, 64, -2)));
        assertEquals(Decision.SUPPRESS, p.decide(at(0, 64, 1))); // wrong direction
    }

    @Test
    void locksOnNegativeYWhenVerticalEnabled() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(0, 63, 0), VERTICAL_ON); // establishes -Y (digging a pillar downward)
        assertEquals(LockAxis.Y, p.axis());
        assertEquals(-1, p.sign());
        assertEquals(Decision.ALLOW, p.decide(at(0, 62, 0)));
        assertEquals(Decision.SUPPRESS, p.decide(at(0, 65, 0))); // wrong direction
    }

    // ---- vertical-disabled edge cases ------------------------------------

    @Test
    void diagonalDoesNotLockWhenVerticalDisabled() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_OFF);
        // A horizontal X+Z diagonal is ambiguous: no lock even with vertical off.
        assertEquals(Decision.ALLOW, place(p, at(1, 64, 1), VERTICAL_OFF));
        assertFalse(p.isLocked());
    }

    @Test
    void verticalComponentDisqualifiesStepWhenVerticalDisabled() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_OFF);
        // An X+Y diagonal carries a vertical component, which is rejected outright
        // when vertical locking is disabled — even though X moved one step.
        assertEquals(Decision.ALLOW, place(p, at(1, 65, 0), VERTICAL_OFF));
        assertFalse(p.isLocked());
        // The anchor advanced, so a clean horizontal step still locks afterwards.
        assertEquals(Decision.ALLOW, place(p, at(2, 65, 0), VERTICAL_OFF));
        assertEquals(LockAxis.X, p.axis());
    }

    @Test
    void gappedVerticalStepDoesNotLock() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        // A 2-block vertical jump is a gap, not a unit step: no lock even with vertical on.
        assertEquals(Decision.ALLOW, place(p, at(0, 66, 0), VERTICAL_ON));
        assertFalse(p.isLocked());
    }

    // ---- record() corner cases (called directly, bypassing decide) -------

    @Test
    void reClickingTheAnchorKeepsItAnchoredNotLocked() {
        LinePolicy p = new LinePolicy();
        place(p, at(5, 64, 5), VERTICAL_ON); // anchor
        assertTrue(p.hasAnchor());
        assertFalse(p.isLocked());

        // Recording the exact same block again (e.g. a re-click) teaches nothing:
        // still anchored at the same spot, still no direction.
        p.record(at(5, 64, 5), VERTICAL_ON);
        assertFalse(p.isLocked());
        assertEquals(at(5, 64, 5), p.previous());
    }

    @Test
    void recordOffLineDoesNotAdvanceFrontier() {
        LinePolicy p = new LinePolicy();
        place(p, at(0, 64, 0), VERTICAL_ON);
        place(p, at(1, 64, 0), VERTICAL_ON); // lock +X, frontier at x=1
        assertEquals(1, p.frontier());

        // A record that is off the locked line must not move the frontier. (decide()
        // would have suppressed this; record() guards independently.)
        p.record(at(2, 65, 0), VERTICAL_ON); // off-line (Y differs)
        assertEquals(1, p.frontier());

        // An on-line but non-contiguous (gapped) record is likewise ignored.
        p.record(at(3, 64, 0), VERTICAL_ON); // skips x=2
        assertEquals(1, p.frontier());

        // The correct next cell advances it.
        p.record(at(2, 64, 0), VERTICAL_ON);
        assertEquals(2, p.frontier());
    }

    // ---- query accessors -------------------------------------------------

    @Test
    void anchorIsActiveButNotLockedBeforeDirectionIsKnown() {
        LinePolicy p = new LinePolicy();
        assertFalse(p.hasAnchor());
        assertFalse(p.isActive());
        assertNull(p.previous());
        assertNull(p.lineRef());

        place(p, at(7, 64, 7), VERTICAL_ON); // just an anchor
        assertTrue(p.hasAnchor());
        assertTrue(p.isActive());
        assertFalse(p.isLocked());
        assertNull(p.lineRef()); // no line until a second step locks it
        assertEquals(at(7, 64, 7), p.previous());
    }

    @Test
    void lockExposesLineRefAndFrontier() {
        LinePolicy p = new LinePolicy();
        place(p, at(2, 64, 0), VERTICAL_ON);
        place(p, at(3, 64, 0), VERTICAL_ON); // lock +X

        // lineRef is the earlier of the two locking blocks; frontier is the latest tip.
        assertEquals(at(2, 64, 0), p.lineRef());
        assertEquals(3, p.frontier());

        place(p, at(4, 64, 0), VERTICAL_ON); // extend
        assertEquals(at(2, 64, 0), p.lineRef()); // lineRef is fixed
        assertEquals(4, p.frontier()); // frontier follows the tip
    }
}
