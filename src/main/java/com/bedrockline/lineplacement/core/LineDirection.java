/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

/**
 * The six cardinal block faces a locked line can advance along. Used by the Line
 * Reacharound feature to name the face of the lead block that the next block is
 * placed against, and the unit step from the lead block to that next block.
 *
 * <p>The constant names are deliberately identical to Minecraft's
 * {@code net.minecraft.core.Direction} constants ({@code EAST}, {@code WEST},
 * {@code UP}, {@code DOWN}, {@code SOUTH}, {@code NORTH}) so the client layer can
 * map one to the other by name ({@code Direction.valueOf(dir.name())}) without
 * this pure type importing any Minecraft class.</p>
 *
 * <p>Axis/sign → face mapping (matches vanilla's axis directions):
 * {@code +X→EAST, -X→WEST, +Y→UP, -Y→DOWN, +Z→SOUTH, -Z→NORTH}.</p>
 */
public enum LineDirection {
    EAST(LockAxis.X, 1, new GridPos(1, 0, 0)),
    WEST(LockAxis.X, -1, new GridPos(-1, 0, 0)),
    UP(LockAxis.Y, 1, new GridPos(0, 1, 0)),
    DOWN(LockAxis.Y, -1, new GridPos(0, -1, 0)),
    SOUTH(LockAxis.Z, 1, new GridPos(0, 0, 1)),
    NORTH(LockAxis.Z, -1, new GridPos(0, 0, -1));

    private final LockAxis axis;
    private final int sign;
    private final GridPos step;

    LineDirection(LockAxis axis, int sign, GridPos step) {
        this.axis = axis;
        this.sign = sign;
        this.step = step;
    }

    public LockAxis axis() {
        return axis;
    }

    public int sign() {
        return sign;
    }

    /** Unit step from a block to the next block along this direction. */
    public GridPos step() {
        return step;
    }

    /**
     * The face/direction for a locked axis and direction sign.
     *
     * @throws IllegalArgumentException if {@code sign} is zero (no direction yet).
     */
    public static LineDirection of(LockAxis axis, int sign) {
        int s = Integer.signum(sign);
        for (LineDirection d : values()) {
            if (d.axis == axis && d.sign == s) {
                return d;
            }
        }
        throw new IllegalArgumentException("no LineDirection for axis=" + axis + " sign=" + sign);
    }
}
