/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

/**
 * Pure decision logic for Bedrock-style straight-line block placement.
 *
 * <p>The policy never places blocks. It only answers one question for each
 * vanilla placement attempt: {@link Decision#ALLOW} or {@link Decision#SUPPRESS}.
 * It is intentionally free of Minecraft types so it can be unit tested directly.</p>
 *
 * <h2>State machine</h2>
 * <ol>
 *   <li><b>Empty</b> — no placement recorded yet. Everything is allowed.</li>
 *   <li><b>Anchored</b> — one placement recorded ({@code prev}), but no direction
 *       yet. The next placement that forms a clean, single-step cardinal move
 *       establishes the lock.</li>
 *   <li><b>Locked</b> — an axis ({@link LockAxis}) and direction ({@code sign})
 *       are fixed, anchored at {@code lineRef}. Placement is <b>contiguous</b>:
 *       only the single cell immediately past the current {@code frontier} (the
 *       last block placed along the line) is allowed. If the cursor drifts and
 *       you try to place further down the line, it is suppressed — you must aim
 *       back at the block right after the last one you placed, exactly like
 *       Bedrock.</li>
 * </ol>
 *
 * <p>The lock is established from the vector between two consecutive recorded
 * placements, which must be a unit cardinal step (length 1 on exactly one axis).
 * Diagonal or gapped steps do not lock (vanilla fallback). Once locked, the axis
 * cannot change until {@link #reset()} is called (the client clears the lock when
 * the use key is released, etc.).</p>
 *
 * <p>This class is not thread-safe; it is only ever touched from the client
 * thread.</p>
 */
public final class LinePolicy {

    /** Last recorded placement. Used to derive the locking vector before a lock exists. */
    private GridPos prev;

    /** A fixed point that the locked line passes through. Null until locked. */
    private GridPos lineRef;

    /** The locked axis, or null when not yet locked. */
    private LockAxis axis;

    /** Locked direction along {@link #axis}: +1 or -1. Only meaningful once locked. */
    private int sign;

    /**
     * Coordinate (along {@link #axis}) of the last block placed on the line — the
     * tip of the contiguous run. The only allowed next placement is
     * {@code frontier + sign}. Only meaningful once locked.
     */
    private int frontier;

    // ---- query -----------------------------------------------------------

    public boolean isLocked() {
        return axis != null;
    }

    /** True once at least one placement (the anchor) has been recorded. */
    public boolean hasAnchor() {
        return prev != null;
    }

    /** True if the policy holds any state worth clearing. */
    public boolean isActive() {
        return prev != null || axis != null;
    }

    public LockAxis axis() {
        return axis;
    }

    public int sign() {
        return sign;
    }

    public int frontier() {
        return frontier;
    }

    public GridPos lineRef() {
        return lineRef;
    }

    public GridPos previous() {
        return prev;
    }

    // ---- core decision ---------------------------------------------------

    /**
     * Decides whether a placement at {@code attempted} may proceed. Pure; does
     * not mutate state.
     *
     * <ul>
     *   <li>Not locked → {@link Decision#ALLOW} (vanilla fallback).</li>
     *   <li>Locked → allowed only when {@code attempted} lies on the locked line
     *       (the two non-locked coordinates match {@code lineRef}) <b>and</b> is
     *       exactly the next contiguous cell ({@code frontier + sign}). Anything
     *       further ahead, behind, or off the line is suppressed.</li>
     * </ul>
     */
    public Decision decide(GridPos attempted) {
        if (axis == null) {
            return Decision.ALLOW;
        }
        if (!offAxisMatches(attempted, lineRef, axis)) {
            return Decision.SUPPRESS;
        }
        return (axis.of(attempted) == frontier + sign) ? Decision.ALLOW : Decision.SUPPRESS;
    }

    /**
     * Records a placement that actually happened.
     *
     * <ul>
     *   <li>Before a lock: a unit cardinal step from the previous placement
     *       establishes the line.</li>
     *   <li>After a lock: extends the contiguous frontier by one step (only if the
     *       placement is exactly the expected next cell).</li>
     * </ul>
     *
     * @param pos           the grid position where a block was placed
     * @param allowVertical whether locking onto the Y axis is permitted
     */
    public void record(GridPos pos, boolean allowVertical) {
        if (axis != null) {
            // Locked: advance the contiguous frontier by exactly one step.
            if (offAxisMatches(pos, lineRef, axis) && axis.of(pos) == frontier + sign) {
                frontier = axis.of(pos);
            }
            return;
        }
        if (prev == null) {
            prev = pos;
            return;
        }
        if (pos.equals(prev)) {
            return; // same block (e.g. a re-click) — nothing to learn
        }
        GridPos d = pos.minus(prev);
        LockAxis dir = unitCardinal(d, allowVertical);
        if (dir == null) {
            // Diagonal / gapped / vertical-when-disabled: do not lock yet.
            // Advance the anchor so a later clean unit step can still establish a line.
            prev = pos;
            return;
        }
        axis = dir;
        sign = Integer.signum(dir.of(d));
        lineRef = prev; // the line passes through the earlier of the two blocks
        frontier = dir.of(pos); // the just-placed block is the current tip
    }

    /** Clears all state, returning to the empty state. */
    public void reset() {
        prev = null;
        lineRef = null;
        axis = null;
        sign = 0;
        frontier = 0;
    }

    // ---- helpers ---------------------------------------------------------

    /**
     * Returns the axis of a unit cardinal step (length exactly 1 on a single
     * axis), or null otherwise. When {@code allowVertical} is false, any vertical
     * component disqualifies the step and the Y axis is never returned.
     */
    private static LockAxis unitCardinal(GridPos d, boolean allowVertical) {
        int ax = Math.abs(d.x());
        int ay = Math.abs(d.y());
        int az = Math.abs(d.z());

        if (!allowVertical) {
            if (ay != 0) {
                return null; // vertical movement is not a horizontal unit step
            }
            if (ax + az != 1) {
                return null;
            }
            return ax == 1 ? LockAxis.X : LockAxis.Z;
        }

        if (ax + ay + az != 1) {
            return null; // diagonal or a gap (length > 1) — not a clean single step
        }
        if (ax == 1) {
            return LockAxis.X;
        }
        if (ay == 1) {
            return LockAxis.Y;
        }
        return LockAxis.Z;
    }

    /** True if {@code a} and {@code b} agree on the two coordinates that are not {@code axis}. */
    private static boolean offAxisMatches(GridPos a, GridPos b, LockAxis axis) {
        return switch (axis) {
            case X -> a.y() == b.y() && a.z() == b.z();
            case Y -> a.x() == b.x() && a.z() == b.z();
            case Z -> a.x() == b.x() && a.y() == b.y();
        };
    }
}
