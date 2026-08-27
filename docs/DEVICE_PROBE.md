# Bedrock_API Device Probe v0.1

This probe is intentionally read-only. It does not invoke profiled Minecraft functions and does not install gameplay hooks.

## What it validates

1. `libminecraftpe.so` is present in the process.
2. The runtime GNU Build ID matches the exact profiled Minecraft build.
3. Every profiled Minecraft RVA points into executable memory.
4. The bytes at each target match the embedded signature (or resolve through a unique signature fallback).
5. Auxiliary supplied libraries are reported when already loaded.
6. Sentinel exports are resolved only when their owning auxiliary module is loaded.

## Expected exact client

- Minecraft version hint: `1.26.20`
- ABI: `arm64-v8a`
- Build ID: `b480c79a54f33d6e4f0d63a131673e3daf749911`
- SHA-256 of the analyzed on-disk `libminecraftpe.so`: `4492ce15ceda3bb4865788a50e8d35b1bbafd45b62ce441440240a693a97d749`

The runtime probe uses the Build ID and in-memory signatures. It does not hash the complete library file from inside Minecraft.

## Install/test cycle

1. Build or download the `bedrock-api-arm64` CI artifact.
2. Install the generated `bedrock_api-0.1.0-arm64-v8a.levipack` in LeviLauncher.
3. Enable only the Bedrock API probe for the first run when practical.
4. Launch the profiled Minecraft build.
5. Reach the title screen and then enter a local world once.
6. Exit normally.
7. Capture the LeviLauncher/preloader log containing `Bedrock_API Probe`.
8. Repeat the launch/enter-world/exit cycle at least 10 times before promoting any target to `device-verified`.

## PASS criteria for phase 0

The log must contain:

- `[REQUIRED] libminecraftpe.so: PASS`
- `[REQUIRED] exact-build gate: PASS`
- all 8 profiled Minecraft targets as `PASS`
- `required Minecraft targets: 8/8 PASS`
- `Probe result: PASS`

Auxiliary modules may show `SKIP` if Minecraft has not loaded them at probe time. This is not a phase-0 failure.

## Failure behavior

Any Build-ID/signature/address failure leaves that target unavailable. The resolver returns address `0`; the probe does not try to invoke it.

If the game crashes before the probe header appears, treat the loader/build integration itself as failed and collect the native tombstone plus launcher/preloader log.

## Next phase

Only after phase 0 repeatedly passes should phase 1 install one observational hook (`LocalPlayer::normalTick`) that increments a counter and immediately calls the original function without modifying state.
