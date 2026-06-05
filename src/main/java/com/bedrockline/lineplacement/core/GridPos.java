/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

/**
 * A minimal immutable integer block-grid position.
 *
 * <p>This type is deliberately independent of any Minecraft class so that the
 * {@link LinePolicy} decision logic can be unit tested without bootstrapping the
 * game. The client glue code converts Minecraft's {@code BlockPos} into a
 * {@code GridPos} before handing it to the policy.</p>
 */
public record GridPos(int x, int y, int z) {

    /** Component-wise subtraction: {@code this - other}. */
    public GridPos minus(GridPos other) {
        return new GridPos(x - other.x, y - other.y, z - other.z);
    }
}
