# Bedrock Line Placement

A small, **client-side** quality-of-life mod for **Minecraft 1.21.1 (NeoForge)** that
recreates **Bedrock Edition's straight-line block placement** in Java Edition.

When you hold the use/place key and drag to place a row of blocks, the mod locks
your placements to the first clear cardinal direction you move in and **suppresses
sideways drift** — so floors, walls, bridges and rows come out perfectly straight.

---

## What it does

1. You place a block while holding the use/place key.
2. As you keep holding and move, the mod watches the next placement to detect a
   clear cardinal direction (+X / -X / +Z / -Z, or +Y / -Y if vertical locking is on).
3. Once a direction is established, the placement line is **locked**: only blocks
   that stay on that straight line (and progress in the locked direction) are
   allowed. Off-line attempts are silently cancelled.
4. Releasing the use/place key clears the lock, so your next drag can pick a new
   direction.

For every placement attempt it answers **allow** or **suppress**. On top of that, the
optional **Line Reacharound** helper (below) lets an *already-locked* line keep going
even when your crosshair narrowly misses the lead block.

## Line Reacharound

When a line lock is active, the mod can infer the **lead block** (the front of the line)
and continue the line **from its forward face** — even when Java's crosshair targeting is
too picky to hit that face directly. This helps with elevated bridges, long rows, and
other straight-line builds where you stand on the line and walk forward while holding the
use key.

It is deliberately conservative and **not** a scaffold / printer / automation mod:

- It only ever runs **while a line is already locked** and you are **holding the use key**.
  Outside an active line it does nothing — it will not place blocks when you are looking at
  containers, buttons, levers, doors, or building normally.
- It uses the **vanilla placement path** (it synthesizes the same interaction you would make
  by aiming at the lead block's face) and respects **vanilla reach, collision, inventory, and
  server validation**. It never adds reach, sends no custom packets, and never sets blocks in
  the world directly.
- It is **rate-limited to vanilla's normal held-use cadence** — it never places faster than,
  or more than, holding right-click already would.
- If the lead block is missing, unloaded, replaced, or the next cell is occupied or out of
  reach, it simply does nothing and defers to vanilla.

Disable it with `enableLineReacharound = false` to require directly hitting the lead block
face.

## How it differs from scaffolding / printer / schematic mods

This is **not** a building automation mod. The difference is deliberate and important
for server safety:

| | Bedrock Line Placement | Scaffold / Printer / Schematic mods |
|---|---|---|
| Builds without you holding use | **No** | Yes |
| Places faster than vanilla right-click | **No** | Often |
| Adds reach / speed | **No** | Often |
| Sends non-vanilla actions | **No** | Often |
| Works by | *Cancelling* off-line attempts, or *redirecting* a held-use placement to the lead block (Line Reacharound) | *Generating* placements from schematics/automation |
| Needs server-side install | **No** | Sometimes |

The mod only acts while **you** are holding the use key on an active line: it either
*cancels* an off-line attempt, or (with Line Reacharound) *redirects* the placement you
are already making to the lead block's forward face. It never places more than vanilla
would per click/tick, never adds reach, sends no custom packets, and uses only the normal
vanilla placement path — the server stays authoritative.

## How to build

Requirements: **JDK 21** (the Gradle toolchain will fetch one if missing).

From the project root:

```bash
# Windows (PowerShell / cmd)
.\gradlew.bat build

# Linux / macOS
./gradlew build
```

The finished, Modrinth-compatible jar is written to:

```
build/libs/bedrocklineplacement-1.2.0.jar
```

Run the unit tests on their own with:

```bash
.\gradlew.bat test
```

## How to install (Modrinth App, NeoForge profile)

1. In the **Modrinth App**, create or open a profile using **Minecraft 1.21.1** with
   the **NeoForge** loader.
2. Open the profile's content/mods folder (in the Modrinth App: profile → ⋯ →
   *Open folder* → `mods`).
3. Copy `build/libs/bedrocklineplacement-1.2.0.jar` into that `mods` folder.
4. Launch the profile. The mod is **client-only** — you do **not** install it on any
   server, and it works when connecting to vanilla servers.

(The same jar works in any standard NeoForge 1.21.1 instance — just drop it in `mods`.)

## Config options

Config file: `config/bedrocklineplacement-client.toml` (created on first run).

| Option | Default | Meaning |
|---|---|---|
| `enableLinePlacement` | `true` | Master switch. When false, vanilla placement is untouched. |
| `enableForBlocksOnly` | `true` | Only constrain placement when the held item is a block. |
| `requireContinuousUseKey` | `true` | Only lock while the use/place key is held; releasing clears the lock. |
| `allowVerticalLocking` | `true` | Allow locking onto the vertical (Y) axis for towers/pillars. |
| `enableLineReacharound` | `true` | **Line Reacharound:** while a line is locked, continue it from the lead block's forward face even when your crosshair narrowly misses it (see above). Vanilla placement/reach/server rules still apply. |
| `firstPlacementPauseTicks` | `6` | Bedrock-style pause (in ticks; 20 = 1s) after the first block before the second is allowed, giving you time to aim. `0` disables. |
| `resetOnItemChange` | `true` | Clear the lock when the selected slot or held item changes. |
| `resetOnSneak` | `false` | Clear the lock when you start/stop sneaking. |
| `debugLogging` | `false` | Log lock/suppress decisions to the client log (troubleshooting). |

## How the lock is decided (brief)

- The **first** placement is the anchor. A short pause (`firstPlacementPauseTicks`)
  follows so you can start moving before the direction is committed.
- The vector to the **next** placement establishes the axis and direction, but only
  if it is a clean single-block cardinal step — a diagonal or gapped step is **not**
  locked (vanilla fallback).
- Once locked, placement is **contiguous**: only the single block immediately past the
  last one you placed is allowed. If your cursor drifts and you aim further down the
  line, those attempts are suppressed — you must come back to the block right after
  the last placed one to keep going (exactly like Bedrock). The axis cannot change
  until the lock is reset.

This logic lives in [`LinePolicy`](src/main/java/com/bedrockline/lineplacement/core/LinePolicy.java)
and is covered by [unit tests](src/test/java/com/bedrockline/lineplacement/core/LinePolicyTest.java).

## Resets

The lock clears when any of these happen:

- The use/place key is released (when `requireContinuousUseKey` is on).
- The selected hotbar slot or held item changes (when `resetOnItemChange` is on).
- A screen/GUI opens (inventory, container, sign, etc.).
- You change dimension or leave the world.
- You start/stop sneaking (only when `resetOnSneak` is on).
- The held item is no longer a block (when `enableForBlocksOnly` is on).

## Manual testing checklist

The decision/geometry logic is unit-tested, but the live placement behaviour must be
verified in a running client. After building, drop the jar into a NeoForge 1.21.1 client
and confirm:

**Line placement / locking**
- [ ] Hold use and drag a row: it stays perfectly straight and **sideways drift does not
      branch the line**.
- [ ] Releasing the use key **resets** the line (the next drag can pick a new direction).
- [ ] Changing the hotbar item **resets** the line (when `resetOnItemChange` is on).

**Line Reacharound**
- [ ] Build a straight **elevated bridge** while standing on top of the line and walking
      forward — blocks place in front of the lead block while you hold right-click.
- [ ] Verify it **respects normal reach distance** (it stops when the lead block gets too
      far away; it does not extend reach).
- [ ] Verify it does **not** place blocks when you look at containers / buttons / levers /
      doors, or when building **outside** an active line lock.
- [ ] Verify it does not place **faster than** holding right-click normally would, and does
      not run after the line resets.
- [ ] Verify it works on a **vanilla server** without installing the mod server-side.

## Known limitations

> **In-game behaviour still needs hands-on testing.** The project compiles and the
> line-lock policy is unit-tested, but the live mixin behaviour — including Line
> Reacharound's world/reach checks and exact placement cadence — should be verified in a
> running client (see the checklist above).

- **Complex / multi-state blocks** (slabs, stairs, doors, trapdoors, beds, etc.) are
  placed by vanilla rules. The mod only reasons about the target *position*, not the
  block's orientation/half, so these will not crash but the straight-line constraint
  is purely positional.
- **Off-hand placement** is handled best-effort (the same hook fires per hand). If you
  hit problems, place from the main hand.
- **Interactions while locked**: the mod only suppresses once a line is *locked*. In
  that state, an off-line right-click (e.g. opening a chest that isn't on the line)
  could be suppressed — release the use key (or it auto-resets) and try again.
- **Replaceable-target prediction** uses vanilla's own `BlockPlaceContext`, so it
  matches vanilla for grass/snow-layer style replacements, but unusual modded
  placement contexts fall back to vanilla rather than guess.
- **Line Reacharound is intentionally conservative.** It only continues a line whose lead
  block the client can still see as solid and in reach; if the lead block is missing,
  unloaded, replaced, or the next cell is occupied, it does nothing and defers to vanilla.
  It tracks the lead from *confirmed* placements only — it never assumes a failed attempt
  succeeded.

## License

[Apache License 2.0](LICENSE).
