# Changelog

All notable changes to **Bedrock Line Placement** are documented here.
This project adheres to [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-06-04

### Changed
- **Contiguous lines (no gaps).** Once a line is locked, only the single block
  immediately past the last placed block is allowed. Drifting off and aiming
  further down the line is now suppressed instead of leaving gaps — you must aim
  back at the cell right after the last placed block to continue, matching Bedrock.
- Line establishment now requires a clean single-block cardinal step (a diagonal or
  gapped second placement no longer locks).

### Added
- `firstPlacementPauseTicks` config (default `6`): a Bedrock-style pause after the
  first block before the second is allowed, giving you time to aim the line. `0`
  disables it.

## [1.0.0] - 2026-06-04

### Added
- Initial release for Minecraft 1.21.1 / NeoForge.
- Bedrock-style straight-line block placement:
  - Holding the use/place key locks placements to the first clear cardinal
    direction of movement (+X / -X / +Z / -Z, and +Y / -Y when vertical locking
    is enabled).
  - Sideways/off-line placement attempts are suppressed (cancelled), never
    redirected or auto-placed.
  - Releasing the use key clears the lock.
- Pure, Minecraft-independent `LinePolicy` decision engine with unit tests
  (X / Y / Z lock cases, no-lock fallback, reset behaviour).
- Single client-side mixin into `MultiPlayerGameMode#useItemOn` — no extra
  packets, no reach/speed changes; vanilla-server safe and client-only.
- Per-tick reset handling: use-key release, item/slot change, screen open,
  dimension change, optional sneak toggle, and non-block held items.
- Configurable client config (`bedrocklineplacement-client.toml`) with:
  `enableLinePlacement`, `enableForBlocksOnly`, `requireContinuousUseKey`,
  `allowVerticalLocking`, `resetOnItemChange`, `resetOnSneak`, `debugLogging`.

### Known limitations
- In-game behaviour still needs hands-on testing (see README → Known limitations).
- Complex placement blocks (slabs, stairs, doors, beds, etc.) defer to vanilla.
- Offhand placement is handled on a best-effort basis.
