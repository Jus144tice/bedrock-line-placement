/*
 * Copyright 2026 Bedrock Line Placement contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.bedrockline.lineplacement.core;

/** The outcome of asking the policy whether a placement attempt may proceed. */
public enum Decision {
    /** Let vanilla placement proceed normally. */
    ALLOW,
    /** Cancel this placement attempt (it would drift off the locked line). */
    SUPPRESS
}
