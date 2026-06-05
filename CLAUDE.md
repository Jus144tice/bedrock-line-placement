# CLAUDE.md — Bedrock Line Placement

> AI-oriented project guide. Optimized for navigation by symbol anchors (class /
> method / field names), **never line numbers** — line numbers are not scalable and
> go stale. To jump to anything below, `Grep` the quoted symbol name.

## What this mod is

A **client-side, NeoForge, Minecraft 1.21.1** quality-of-life mod that recreates
**Bedrock Edition's straight-line block placement** in Java Edition. While the player
holds the use/place key, placements lock to the first clear cardinal direction and
off-line / non-contiguous attempts are **suppressed**. It is a pure *gatekeeper*:

- It **never places blocks itself** and never adds reach, speed, or any non-vanilla
  packet. It only decides ALLOW vs SUPPRESS for the placement the player is already
  attempting, by hooking the client's own placement method.
- Therefore it is **vanilla-server-safe** and needs no server-side install.
- It is **not** a scaffold / printer / schematic / automation mod.

User-facing behavior, install steps, and config docs live in [README.md](README.md).
Version history lives in [CHANGELOG.md](CHANGELOG.md).

---

## MANDATE — this file is self-updating

**This CLAUDE.md must be kept in sync with the codebase within the SAME session that
changes the code. Do not defer updates to a later session.**

When, in a session, you make a change that affects anything described here, you MUST
update this file before finishing that session. Triggers include (non-exhaustive):

- Adding / removing / renaming a class, method, field, package, or resource that
  appears in the **File & symbol map** below.
- Changing the placement **decision rules**, the **lock state machine**, the **reset
  triggers**, or the **data flow** (e.g. which vanilla method is hooked).
- Adding / removing / renaming a **config option**, or changing its default.
- Changing build tooling/versions (NeoForge, ModDevGradle, Gradle, Java, mappings).

If a change does NOT touch any of the above (e.g. a comment typo, an internal-only
refactor with identical public symbols), this file may be left as-is. When in doubt,
update it. Keep edits surgical — change only the affected entries.

---

## Architecture & data flow

One mixin + one pure policy + one tick watcher. The policy is deliberately free of
Minecraft types so it is unit-testable.

```
right-click held
  → MultiPlayerGameMode#useItemOn          (vanilla client method)
    → MultiPlayerGameModeMixin (HEAD)       → LineLockManager.preUseItemOn(...)
        guards (enabled? key held? BlockItem?) → predict placePos via BlockPlaceContext
        → initial pause gate (FAIL while pausing)
        → LinePolicy.decide(placePos)        → SUPPRESS ⇒ return FAIL (cancel, no packet)
                                              → ALLOW   ⇒ stash placePos, proceed
    → (vanilla sends placement, returns InteractionResult)
    → MultiPlayerGameModeMixin (RETURN)     → LineLockManager.postUseItemOn(result)
        if result.consumesAction()           → LinePolicy.record(placePos)   (anchor / lock / extend frontier)
                                              → arm initial pause on the anchor

every client tick
  → ClientEvents.onClientTickPost           → LineLockManager.clientTick()   (count down pause)
                                              → LineLockManager.reset(reason) on any reset trigger
```

**Key invariants** (do not break without updating tests + this file):

- The mod only ever **cancels**; it never synthesizes a placement. Suppression =
  returning a non-consuming `InteractionResult` from the HEAD hook.
- The pure policy never imports a Minecraft class. Conversions
  (`BlockPos`→`GridPos`, config reads) happen only in `LineLockManager`.
- Suppression only ever happens once a line is **locked** (or during the initial
  pause); when unlocked the policy returns ALLOW so vanilla is untouched.
- Locked lines are **contiguous**: only `frontier + sign` is allowed (see policy).

---

## File & symbol map

Paths are links; the `Symbols` column lists the anchors to grep for.

### Core policy (pure, no Minecraft) — `com.bedrockline.lineplacement.core`

| File | Symbols | Purpose |
|---|---|---|
| [GridPos.java](src/main/java/com/bedrockline/lineplacement/core/GridPos.java) | `GridPos` (record `x,y,z`), `GridPos.minus(GridPos)` | Minecraft-free integer block position used by the policy. |
| [LockAxis.java](src/main/java/com/bedrockline/lineplacement/core/LockAxis.java) | `LockAxis` (`X,Y,Z`), `LockAxis.of(GridPos)` | The cardinal axis a line locks to; `of` reads the coord on that axis. |
| [Decision.java](src/main/java/com/bedrockline/lineplacement/core/Decision.java) | `Decision` (`ALLOW`, `SUPPRESS`) | Result of a policy query. |
| [LinePolicy.java](src/main/java/com/bedrockline/lineplacement/core/LinePolicy.java) | fields `prev`, `lineRef`, `axis`, `sign`, `frontier`; `decide(GridPos)`, `record(GridPos,boolean)`, `reset()`, `unitCardinal(GridPos,boolean)`, `offAxisMatches(...)`; queries `isLocked()`, `hasAnchor()`, `isActive()`, `frontier()` | **The brain.** State machine Empty→Anchored→Locked. `decide` = ALLOW unless locked, then require on-line AND exactly `frontier+sign` (contiguity). `record` establishes the lock on a unit cardinal step and advances `frontier`. |

### Client glue — `com.bedrockline.lineplacement.client`

| File | Symbols | Purpose |
|---|---|---|
| [LineLockManager.java](src/main/java/com/bedrockline/lineplacement/client/LineLockManager.java) | `INSTANCE` (singleton); `preUseItemOn(LocalPlayer,InteractionHand,BlockHitResult)`, `postUseItemOn(InteractionResult)`, `clientTick()`, `reset(String)`, `isLocked()`; fields `pendingPlacePos`, `pendingAllowVertical`, `pauseTicksRemaining` | Bridges Minecraft↔policy. Predicts placement pos via `BlockPlaceContext.getClickedPos()`, applies guards + the initial-pause gate, calls `decide`/`record`, owns the pause countdown. All `debug(...)` logging is gated by `Config.debugLogging()`. |
| [ClientEvents.java](src/main/java/com/bedrockline/lineplacement/client/ClientEvents.java) | `onClientTickPost(ClientTickEvent.Post)`, `resetTrackers()`; trackers `wasUseDown`, `hadScreen`, `lastSlot`, `lastItem`, `wasSneaking`, `lastDimension` | Per-tick watcher. Calls `clientTick()` then fires `reset(reason)` on: no player, screen opened, use-key released, item/slot change, non-block held item, sneak toggle, dimension change. |

### Mixin (the only vanilla hook) — `com.bedrockline.lineplacement.mixin`

| File | Symbols | Purpose |
|---|---|---|
| [MultiPlayerGameModeMixin.java](src/main/java/com/bedrockline/lineplacement/mixin/MultiPlayerGameModeMixin.java) | `@Mixin(MultiPlayerGameMode.class)`; `blp$decidePlacement` (`@Inject` HEAD, cancellable), `blp$recordPlacement` (`@Inject` RETURN) | Targets `useItemOn(LocalPlayer,InteractionHand,BlockHitResult)`. HEAD asks the manager and cancels with `FAIL` to suppress; RETURN records confirmed placements. **Fragile to mapping changes** — if the method name/signature differs, fix here. |

### Entry point & config — `com.bedrockline.lineplacement`

| File | Symbols | Purpose |
|---|---|---|
| [BedrockLinePlacement.java](src/main/java/com/bedrockline/lineplacement/BedrockLinePlacement.java) | `MODID` (`"bedrocklineplacement"`), `LOGGER`, constructor `BedrockLinePlacement(IEventBus,ModContainer)` | `@Mod(dist = Dist.CLIENT)` entrypoint. Registers the CLIENT config and `ClientEvents` on `NeoForge.EVENT_BUS`. |
| [Config.java](src/main/java/com/bedrockline/lineplacement/Config.java) | `SPEC`; values `ENABLE_LINE_PLACEMENT`, `ENABLE_FOR_BLOCKS_ONLY`, `REQUIRE_CONTINUOUS_USE_KEY`, `ALLOW_VERTICAL_LOCKING`, `FIRST_PLACEMENT_PAUSE_TICKS`, `RESET_ON_ITEM_CHANGE`, `RESET_ON_SNEAK`, `DEBUG_LOGGING`; matching getters | `ModConfigSpec` for `config/bedrocklineplacement-client.toml`. Getters are null-safe (return defaults if queried before load). |

### Resources — `src/main/resources`

| File | Purpose |
|---|---|
| [neoforge.mods.toml](src/main/resources/META-INF/neoforge.mods.toml) | Mod metadata; declares the mixin config and NeoForge/Minecraft (CLIENT) deps. **`version` here must match `mod_version`.** |
| [bedrocklineplacement.mixins.json](src/main/resources/bedrocklineplacement.mixins.json) | Mixin config; lists `MultiPlayerGameModeMixin` under `client`. No refmap (NeoForge runs official mappings). |
| [pack.mcmeta](src/main/resources/pack.mcmeta) | Resource pack metadata (`pack_format` 34 for 1.21.1). |

### Tests

Tests run via the moddev `unitTest` harness (NeoForge on the test classpath), so a test
*may* touch platform types — but the policy tests deliberately don't. JUnit 5.

| File | Symbols | Purpose |
|---|---|---|
| [LinePolicyTest.java](src/test/java/com/bedrockline/lineplacement/core/LinePolicyTest.java) | helper `place(LinePolicy,GridPos,boolean)`; X/Y/Z + negative-direction locks, contiguity (`requiresContiguousPlacementNoGaps`), gapped/diagonal non-lock, vertical-disabled edge cases, `record()` corner cases (re-click anchor, off-line/gapped record doesn't advance frontier), query accessors, reset | Pure tests for `LinePolicy` (no Minecraft bootstrap). **Update these whenever the decision rules change.** |
| [GridPosTest.java](src/test/java/com/bedrockline/lineplacement/core/GridPosTest.java) | components, `minus`, value equality/hashing | Pure tests for the `GridPos` record. |
| [LockAxisTest.java](src/test/java/com/bedrockline/lineplacement/core/LockAxisTest.java) | `of(GridPos)` per axis, negatives | Pure tests for `LockAxis` coordinate selection. |
| [ConfigTest.java](src/test/java/com/bedrockline/lineplacement/ConfigTest.java) | `specBuilds`, `valuePaths`, `specDefaults`, `gettersFallBackToDefaultsBeforeLoad`, `gettersMirrorSpecDefaults` | Asserts the `ModConfigSpec` structure/defaults and that the null-safe getters fall back to documented defaults before load (no `.toml` read). Needs the `unitTest` harness for `ModConfigSpec`. Mirror any default/key change here. |

---

## Config options (authoritative list)

Defined in `Config`; documented for users in [README.md](README.md). Defaults:

`enableLinePlacement=true`, `enableForBlocksOnly=true`, `requireContinuousUseKey=true`,
`allowVerticalLocking=true`, `firstPlacementPauseTicks=6`, `resetOnItemChange=true`,
`resetOnSneak=false`, `debugLogging=false`.

## Build & test

- Toolchain: **NeoForge 21.1.233**, **ModDevGradle 2.0.141**, **Gradle 9.2.1**,
  **JDK 21** (Parchment `2024.11.17`), **Spotless 6.25.0** (palantir-java-format).
  Versions live in [gradle.properties](gradle.properties) / [build.gradle](build.gradle).
- Build jar: `./gradlew build` (Windows: `.\gradlew.bat build`). Set
  `JAVA_HOME` to a JDK 21 if the default is newer.
- Run only unit tests: `./gradlew test` (auto-formats first; tests run on the moddev
  `unitTest` harness so they can touch NeoForge types like `ModConfigSpec`).
- **Formatting / lint** is Spotless with `palantirJavaFormat` (4-space / 120-col, "prettier
  for Java") plus `removeUnusedImports`/`trimTrailingWhitespace`/`endWithNewline` over
  `src/**/*.java`. It is folded into the build: `compileJava` `dependsOn 'spotlessApply'`,
  so every `build`/`test` formats sources first, and `spotlessCheck` runs as part of `check`
  (and `build`) as the gate that fails on unformatted/lint-dirty code. `./gradlew spotlessApply`
  / `./gradlew spotlessCheck` are the standalone format / dry-run-gate commands.
- Output jar: `build/libs/bedrocklineplacement-<mod_version>.jar`.
- **CI:** [.github/workflows/build.yml](.github/workflows/build.yml) runs on push/PR to `main`
  (and `workflow_dispatch`): JDK 21 → `./gradlew build` (format + compile + test) → uploads the
  jar as the `bedrocklineplacement-jar` artifact. Keep it green.

## Gotchas for future edits

- Changing the placement rules means editing **only** `LinePolicy` (logic) +
  `LinePolicyTest` (proof) — the Minecraft layers should not need to know the rules.
- The HEAD/RETURN hooks stay in sync via `pendingPlacePos`: it is set only on an
  allowed HEAD pass and consumed/cleared in RETURN/`reset`. Preserve that contract.
- Anything touching `useItemOn`'s name/signature, `BlockPlaceContext`,
  `InteractionResult`, or the NeoForge event/config APIs is mapping/version-sensitive
  — verify against the generated sources before assuming a symbol exists.
- **End every change session by committing and pushing** to the GitHub remote (`origin`,
  branch `main`): `git add -A && git commit && git push`. The commit footer must include the
  `Co-Authored-By: Claude` line. This is a standing instruction — treat it as part of finishing,
  not optional follow-up.
