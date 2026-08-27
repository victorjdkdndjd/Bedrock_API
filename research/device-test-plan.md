# Device validation plan

The rule is: **address validation and gameplay-semantic validation are separate tests**.

## Phase 0 — load safety

- API loads without registering a mod-menu module.
- No hook is installed by default.
- All module enumeration is read-only.
- Unknown Minecraft Build ID keeps all private Minecraft targets unavailable.

## Phase 1 — resolver self-test

Expected PASS on the profiled build:

- 8/8 supplied modules visible at runtime.
- Minecraft Build ID exact match.
- 8 private Minecraft targets verify their signature at the profiled RVA (or exactly one fallback signature match).
- 7 external-library sentinel exports resolve inside executable segments.

## Phase 2 — read-only gameplay probes

1. Hook `LocalPlayer::normalTick` only; count ticks, do not mutate state.
2. Resolve `ClientInstance::getLocalPlayer`; check null/non-null transitions across menus/world load.
3. Obtain `BlockSource` through a separately verified route and call `BlockSource::getBlock` on player-adjacent positions.
4. Never retain world/player pointers across dimension or world transitions.

## Phase 3 — observed events

- Observation-only `GameMode::startDestroyBlock` hook.
- Confirm calls in Creative and Survival separately.
- Log position/face only; do not change return values or object fields.
- Confirm hook uninstall/reload cycles.

## Phase 4 — mutation experiments

Every experiment must have:

- one operation per session initially;
- automatic timeout / cleanup;
- world/player pointer invalidation guards;
- comparison against vanilla result (item drop, server collision, relog persistence);
- crash/freeze/ghost-block result recorded as FAIL even if the client visually changed.

Direct `GameMode::destroyBlock`, messenger vtable detours and the previous synthetic start/continue mining path remain blocked until a new synchronized route is proven.

## Required evidence for “device-verified”

- no native crash;
- no freeze;
- correct client visual state;
- correct collision;
- correct item/tool semantics where applicable;
- state survives chunk reload/relog when applicable;
- same result on at least 10 repeated trials.
