/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

/** The cardinal block-grid axis a placement line can be locked to. */
public enum LockAxis {
    X,
    Y,
    Z;

    /** Returns the coordinate of {@code p} along this axis. */
    public int of(GridPos p) {
        return switch (this) {
            case X -> p.x();
            case Y -> p.y();
            case Z -> p.z();
        };
    }
}
