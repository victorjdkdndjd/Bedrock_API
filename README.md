# Bedrock_API

Native C++ API / reverse-engineering support layer for Minecraft Bedrock on Android + LeviLauncher.

The project is intentionally **exact-build, fail-closed and test-first**. It does not assume an offset is valid just because it worked on another build. Every native target is tied to a build fingerprint and can be checked again in memory before it is exposed to consumer mods.

## Current analyzed client

- Minecraft string observed: `1.26.20`
- ABI: `arm64-v8a` / AArch64
- Android ELF target: API 26
- NDK recorded in `libminecraftpe.so`: `r28c (13676358)`
- `libminecraftpe.so` Build ID: `b480c79a54f33d6e4f0d63a131673e3daf749911`
- `libminecraftpe.so` SHA-256: `4492ce15ceda3bb4865788a50e8d35b1bbafd45b62ce441440240a693a97d749`

## Design

`Bedrock_API` is split into four layers:

1. **Module registry** — discovers every loaded `.so`, its base address, memory segments and GNU Build ID.
2. **Exact-build resolver** — resolves public exports, verified RVAs and signatures. Minecraft targets are rejected when the Build ID is unknown.
3. **Capability layer** — mods query whether a native feature is actually available before using it.
4. **Device self-test** — validates module presence and target addresses without invoking destructive gameplay paths.

The source intentionally does **not** redistribute Minecraft or third-party binary `.so` files. `research/current-libs.md` contains fingerprints and symbol statistics derived from the supplied binaries so the exact input set can be recognized.

## Supplied libraries covered

- `libminecraftpe.so`
- `libHttpClient.Android.so`
- `libMediaDecoders_Android.so`
- `libPlayFabMultiplayer.so`
- `libpairipcore.so`
- `libconscrypt_jni.so`
- `libc++_shared.so`
- `libfmod.so`

`libminecraftpe.so` also imports `libmaesdk.so`, which was not in the supplied set and is therefore treated as optional/unprofiled for now.

## Validation rule

A feature moves through these stages:

`discovered -> static-verified -> runtime-resolved -> device-observed -> device-verified`

Anything that can modify the world/network/render pipeline stays experimental until the final stage.
