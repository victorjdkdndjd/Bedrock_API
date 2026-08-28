#include "mod/BedrockApiMod.hpp"

#include "BedrockAPI/Runtime.hpp"
#include "BedrockAPI/Version.hpp"
#include "HookProbe.hpp"
#include "SelfTest.hpp"

namespace bedrock::api {
BedrockApiMod& BedrockApiMod::instance() {
    static BedrockApiMod mod;
    return mod;
}

BedrockApiMod::BedrockApiMod() : mSelf(*ll::mod::NativeMod::current()) {}

bool BedrockApiMod::load() {
    getSelf().getLogger().info("Bedrock API {} loading (PlayerBridge probe)", VersionString);
    getSelf().getLogger().info(
        "Probe policy: resolver first; then LocalPlayer::normalTick + ClientInstance::update observational hooks with sparse getLocalPlayer reads");
    return true;
}

bool BedrockApiMod::enable() {
    const bool minecraftPresent = Runtime::instance().initialize();
    const bool probePassed = selftest::run();

    if (!minecraftPresent) {
        getSelf().getLogger().warn("libminecraftpe.so was not present when the probe ran; API remains fail-closed");
        return true;
    }
    if (!probePassed) {
        getSelf().getLogger().warn("Bedrock API resolver probe did not pass; PlayerBridge remains disabled");
        return true;
    }

    if (!hookprobe::install()) {
        getSelf().getLogger().warn("Bedrock API PlayerBridge probe could not be installed; no mutation fallback is attempted");
    }
    return true;
}

bool BedrockApiMod::disable() {
    hookprobe::uninstall();
    getSelf().getLogger().info("Bedrock API disabled; PlayerBridge probe cleaned up");
    return true;
}

bool BedrockApiMod::unload() {
    hookprobe::uninstall();
    return true;
}
} // namespace bedrock::api
