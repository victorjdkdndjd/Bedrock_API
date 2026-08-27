#include "mod/BedrockApiMod.hpp"

#include "BedrockAPI/Runtime.hpp"
#include "BedrockAPI/Version.hpp"
#include "SelfTest.hpp"

namespace bedrock::api {
BedrockApiMod& BedrockApiMod::instance() {
    static BedrockApiMod mod;
    return mod;
}

BedrockApiMod::BedrockApiMod() : mSelf(*ll::mod::NativeMod::current()) {}

bool BedrockApiMod::load() {
    getSelf().getLogger().info("Bedrock API {} loading (probe mode)", VersionString);
    getSelf().getLogger().info("Probe policy: read-only resolver checks; no gameplay hooks are installed");
    return true;
}

bool BedrockApiMod::enable() {
    const bool minecraftPresent = Runtime::instance().initialize();
    const bool probePassed = selftest::run();

    if (!minecraftPresent) {
        getSelf().getLogger().warn("libminecraftpe.so was not present when the probe ran; API remains fail-closed");
    }
    if (!probePassed) {
        getSelf().getLogger().warn("Bedrock API probe did not pass required checks; unsafe capabilities remain disabled");
    }
    return true;
}

bool BedrockApiMod::disable() {
    getSelf().getLogger().info("Bedrock API disabled; no probe hooks require cleanup");
    return true;
}

bool BedrockApiMod::unload() {
    return true;
}
} // namespace bedrock::api
