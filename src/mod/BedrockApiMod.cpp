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
    getSelf().getLogger().info("Bedrock API {} loading (first-hook probe)", VersionString);
    getSelf().getLogger().info("Probe policy: resolver checks first; only LocalPlayer::normalTick may be hooked after full PASS");
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
        getSelf().getLogger().warn("Bedrock API resolver probe did not pass; first-hook test remains disabled");
        return true;
    }

    if (!hookprobe::install()) {
        getSelf().getLogger().warn("Bedrock API first-hook probe could not be installed; gameplay remains unmodified");
    }
    return true;
}

bool BedrockApiMod::disable() {
    hookprobe::uninstall();
    getSelf().getLogger().info("Bedrock API disabled; first-hook probe cleaned up");
    return true;
}

bool BedrockApiMod::unload() {
    hookprobe::uninstall();
    return true;
}
} // namespace bedrock::api
