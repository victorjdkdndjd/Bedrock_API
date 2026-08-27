#pragma once
#include <pl/Mod.hpp>
namespace bedrock::api {
class BedrockApiMod {
public:
    static BedrockApiMod& instance();
    BedrockApiMod();
    bool load(); bool enable(); bool disable(); bool unload();
    ll::mod::NativeMod& getSelf() noexcept { return mSelf; }
private: ll::mod::NativeMod& mSelf;
};
}
