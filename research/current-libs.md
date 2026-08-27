# Current supplied native library inventory

Analyzed input set on 2026-08-27. All eight supplied binaries are ELF64 AArch64 shared objects and are stripped.

| Library | Size | SHA-256 | GNU Build ID | Defined dynamic symbols | Defined functions |
|---|---:|---|---|---:|---:|
| libminecraftpe.so | 313,919,840 | `4492ce15ceda3bb4865788a50e8d35b1bbafd45b62ce441440240a693a97d749` | `b480c79a54f33d6e4f0d63a131673e3daf749911` | 90,545 | 79,228 |
| libHttpClient.Android.so | 333,704 | `9ec709ca2e1c852831608ce23100996ae588215bd457f799061114ad0d700579` | `77e7dedc8207aa0cf915ddd350e58ce38db5f7a4` | 164 | 164 |
| libMediaDecoders_Android.so | 1,517,320 | `408eaa5f1cbb31c48b8c9ed66f08cd48fe2864a69203aac24c32037c5fc4d668` | none | 2,351 | 1,861 |
| libPlayFabMultiplayer.so | 4,344,064 | `aa627c696e1b99683a61d6bbabd60e73f8adccb7af2004c1216d6e154af1df96` | `72696f6c2928e30ea377ebeb867b59b9b61cef94` | 98 | 98 |
| libpairipcore.so | 609,416 | `0d100171c157420e437d5f43549998121bc52da7fe10aaef8a2ce55d35f1d1fd` | none | 3 | 3 |
| libconscrypt_jni.so | 2,103,592 | `a1438ab9aea8fcbd78a57777055ab157c67518475c577a85266063d659ccd85a` | `f1446306c60470b344a50f938f9632ca54d8b4e4` | 3,343 | 2,999 |
| libc++_shared.so | 1,253,544 | `cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c` | `7befe631535aa853c4f4ac1293e49dcea34c9b6e` | 2,340 | 1,585 |
| libfmod.so | 1,179,776 | `cccad0a7e5cd5c975bb40979c418a66ca8448536549ff5ca352826180c605a6e` | `4b8a6d0f35523701689a1db408d649a9` | 1,298 | 1,124 |

## Useful stable export surfaces

- HttpClient exposes `HC*` functions, including `HCGetLibVersion`, request/response APIs and WebSocket routing.
- PlayFab exposes `PFLobby*`, `PFMatchmaking*`, `PFMultiplayer*` and `PFPubSub*` entry points.
- FMOD exposes a large `FMOD5_*` C API including `FMOD5_System_GetVersion`.
- MediaDecoders exports codec/demuxer factories such as `CreateMP4Demuxer`, `CreateVPXDecoder`, `CreateVorbisDecoder`, and `CreateWebMDemuxer`.
- Conscrypt exports `JNI_OnLoad` plus its native TLS/crypto surface.
- PairIP has only three dynamic exports: `ExecuteProgram`, `JNI_OnLoad`, `JNI_OnUnload`.
- libc++ exposes the expected NDK runtime surface such as `__cxa_demangle`.

## Minecraft-specific observation

Despite a large dynamic symbol table, the high-value gameplay classes used by mods (`BlockSource`, `GameMode`, `ClientInstance`, `LocalPlayer`) are not available as ordinary dynamic exports in this client. Exact-build signatures / RVAs are therefore required for those targets.

The binary contains the version string `1.26.20` and was built for Android API 26 with NDK r28c.

## Imported libraries from libminecraftpe.so

`libpairipcore.so`, `libGLESv2.so`, `libPlayFabMultiplayer.so`, `libEGL.so`, `libfmod.so`, `liblog.so`, `libHttpClient.Android.so`, `libmaesdk.so`, `libdl.so`, `libandroid.so`, `libc++_shared.so`, `libm.so`, `libc.so`.
