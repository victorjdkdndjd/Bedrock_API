#!/usr/bin/env python3
from pathlib import Path
import hashlib, re, subprocess, sys

EXPECTED = {
"libminecraftpe.so": ("4492ce15ceda3bb4865788a50e8d35b1bbafd45b62ce441440240a693a97d749","b480c79a54f33d6e4f0d63a131673e3daf749911"),
"libHttpClient.Android.so": ("9ec709ca2e1c852831608ce23100996ae588215bd457f799061114ad0d700579","77e7dedc8207aa0cf915ddd350e58ce38db5f7a4"),
"libMediaDecoders_Android.so": ("408eaa5f1cbb31c48b8c9ed66f08cd48fe2864a69203aac24c32037c5fc4d668",None),
"libPlayFabMultiplayer.so": ("aa627c696e1b99683a61d6bbabd60e73f8adccb7af2004c1216d6e154af1df96","72696f6c2928e30ea377ebeb867b59b9b61cef94"),
"libc++_shared.so": ("cd61762848882a16c8244c964a6f396c0caa0b440588a210ce9cc4ab0e6d9f0c","7befe631535aa853c4f4ac1293e49dcea34c9b6e"),
"libconscrypt_jni.so": ("a1438ab9aea8fcbd78a57777055ab157c67518475c577a85266063d659ccd85a","f1446306c60470b344a50f938f9632ca54d8b4e4"),
"libfmod.so": ("cccad0a7e5cd5c975bb40979c418a66ca8448536549ff5ca352826180c605a6e","4b8a6d0f35523701689a1db408d649a9"),
"libpairipcore.so": ("0d100171c157420e437d5f43549998121bc52da7fe10aaef8a2ce55d35f1d1fd",None),
}
SENTINELS={"libHttpClient.Android.so":"HCGetLibVersion","libMediaDecoders_Android.so":"CreateMP4Demuxer","libPlayFabMultiplayer.so":"PFMultiplayerGetErrorMessage","libpairipcore.so":"JNI_OnLoad","libconscrypt_jni.so":"JNI_OnLoad","libc++_shared.so":"__cxa_demangle","libfmod.so":"FMOD5_System_GetVersion"}
TARGETS=[
("LocalPlayer::normalTick",0xA6417E4,"E8 0F 19 FC FD 7B 01 A9 FC 6F 02 A9 FA 67 03 A9 F8 5F 04 A9 F6 57 05 A9 F4 4F 06 A9 FD 43 00 91 FF C3 09 D1 54 D0 3B D5 F3 03 00 AA 88 16 40 F9"),
("ClientInstance::update",0x943ECF4,"FD 7B BA A9 FC 6F 01 A9 FA 67 02 A9 F8 5F 03 A9 F6 57 04 A9 F4 4F 05 A9 FD 03 00 91 FF C3 12 D1 59 D0 3B D5 F3 03 00 AA F4 03 01 2A 28 17 40 F9 A8 83 1F F8"),
("ClientInstance::getLocalPlayer",0x9443404,"FF 43 01 D1 FD 7B 03 A9 F3 23 00 F9 FD C3 00 91 53 D0 3B D5 E8 03 00 AA E0 23 00 91 69 16 40 F9 01 61 08 91 A9 83 1F F8 9D 61 33 95 E0 23 00 91"),
("BlockSource::getBlock",0xF2541EC,"FF 03 01 D1 FD 7B 01 A9 F6 57 02 A9 F4 4F 03 A9 FD 43 00 91 56 D0 3B D5 C8 16 40 F9 E8 07 00 F9 28 04 40 B9 09 84 C0 79 1F 01 09 6B 6B 03 00 54 09 80 C0 79 F3 03 00 AA 1F 01 09 6B EA 02 00 54"),
("GameMode::startDestroyBlock",0xEF72BE4,"FF 83 01 D1 FD 7B 01 A9 F9 13 00 F9 F8 5F 03 A9 F6 57 04 A9 F4 4F 05 A9 FD 43 00 91 59 D0 3B D5 F4 03 00 AA F5 03 03 AA 28 17 40 F9 F6 03 02 2A F3 03 01 AA E8 07 00 F9 00 04 40 F9 E2 13 00 39"),
("SurvivalMode::startDestroyBlock",0xEF77228,"08 20 43 39 68 01 00 34 48 BB 01 F0 08 E1 7C 39 08 01 00 34 FD 7B BF A9 FD 03 00 91 E1 03 1F 2A 05 00 00 94 E0 03 1F 2A FD 7B C1 A8 C0 03 5F D6 63 EE FF 17 FF 03 02 D1 FD 7B 05 A9 F5 33 00 F9"),
("GameMode::_sendTryDestroyBlock",0xEF73054,"FF 83 03 D1 FD 7B 0A A9 F8 5F 0B A9 F6 57 0C A9 F4 4F 0D A9 FD 83 02 91 58 D0 3B D5 F4 03 00 AA F3 03 02 2A 08 17 40 F9 F5 03 01 AA A8 83 1F F8 16 04 40 F9 E0 03 16 AA D8 43 F4 97"),
("GameMode::destroyBlock",0xEF73178,"FF 03 02 D1 FD 7B 04 A9 F8 5F 05 A9 F6 57 06 A9 F4 4F 07 A9 FD 03 01 91 58 D0 3B D5 F3 03 00 AA F4 03 02 2A 08 17 40 F9"),]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def cmd(*args): return subprocess.check_output(args,text=True,stderr=subprocess.DEVNULL)
def build_id(p):
    t=cmd("readelf","-n",str(p)); m=re.search(r"Build ID: ([0-9a-fA-F]+)",t); return m.group(1).lower() if m else None
def exports(p): return cmd("nm","-D","--defined-only",str(p))
def main(d):
    d=Path(d); fail=0
    for name,(want_sha,want_bid) in EXPECTED.items():
        p=d/name
        if not p.exists(): print(f"FAIL missing {name}"); fail+=1; continue
        got_sha=sha(p); got_bid=build_id(p)
        ok=got_sha==want_sha and got_bid==want_bid
        print(("PASS" if ok else "FAIL"),name,"sha",got_sha,"build-id",got_bid)
        fail+=not ok
        if name in SENTINELS:
            ex=exports(p); hit=SENTINELS[name] in ex; print("  ","PASS" if hit else "FAIL","sentinel",SENTINELS[name]); fail+=not hit
    mc=d/"libminecraftpe.so"
    if mc.exists():
        data=mc.read_bytes()
        for label,rva,sig in TARGETS:
            b=bytes.fromhex(sig); same=data[rva:rva+len(b)]==b; count=data.count(b)
            print("  ","PASS" if same and count==1 else "FAIL",label,hex(rva),"matches",count); fail+=not(same and count==1)
    print("RESULT", "PASS" if not fail else f"FAIL ({fail})")
    return 1 if fail else 0
if __name__=="__main__": sys.exit(main(sys.argv[1] if len(sys.argv)>1 else "."))
