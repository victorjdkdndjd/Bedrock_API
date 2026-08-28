#include "SelfTest.hpp"

#include "BedrockAPI/Core/Profile.hpp"
#include "BedrockAPI/Runtime.hpp"
#include "BedrockAPI/Version.hpp"
#include "mod/BedrockApiMod.hpp"

#include <array>
#include <cstddef>

namespace bedrock::api::selftest {
namespace {
constexpr std::array AuxiliaryModules{
    core::ModuleId::HttpClient,
    core::ModuleId::MediaDecoders,
    core::ModuleId::PlayFabMultiplayer,
    core::ModuleId::PairIpCore,
    core::ModuleId::Conscrypt,
    core::ModuleId::CxxShared,
    core::ModuleId::Fmod,
    core::ModuleId::MaeSdk,
};
}

bool run() {
    auto& log = BedrockApiMod::instance().getSelf().getLogger();
    auto& resolver = Runtime::instance().resolver();
    const auto& profile = core::currentBuildProfile();

    log.info("============================================================");
    log.info("Bedrock_API Probe {} (RESOLVER FIRST / ONE OBSERVATIONAL HOOK AFTER PASS)", VersionString);
    log.info("profile: {}", profile.name);
    log.info("expected Minecraft version hint: {}", profile.minecraftVersionHint);
    log.info("expected Minecraft Build ID: {}", profile.minecraftBuildId);
    log.info("============================================================");

    bool requiredOk = true;
    std::size_t requiredTargets = 0;
    std::size_t requiredPassed = 0;
    std::size_t optionalLoaded = 0;
    std::size_t optionalSentinelsPassed = 0;

    const auto* minecraft = resolver.module(core::ModuleId::Minecraft);
    if (!minecraft) {
        log.error("[REQUIRED] libminecraftpe.so: FAIL (not loaded)");
        requiredOk = false;
    } else {
        log.info("[REQUIRED] libminecraftpe.so: PASS base=0x{:x}", minecraft->base);
        log.info("[REQUIRED] runtime Build ID: {}", minecraft->buildId.empty() ? "<missing>" : minecraft->buildId);
        if (resolver.exactMinecraftBuild()) {
            log.info("[REQUIRED] exact-build gate: PASS");
        } else {
            log.error("[REQUIRED] exact-build gate: FAIL (all Minecraft targets remain closed)");
            requiredOk = false;
        }
    }

    for (auto id : AuxiliaryModules) {
        const auto* module = resolver.module(id);
        if (!module) {
            log.info("[OPTIONAL] module {}: SKIP (not loaded at probe time)", core::moduleName(id));
            continue;
        }
        ++optionalLoaded;
        log.info("[OPTIONAL] module {}: OBSERVED base=0x{:x} build-id={}",
                 core::moduleName(id), module->base,
                 module->buildId.empty() ? "<missing>" : module->buildId);
    }

    for (const auto& spec : profile.targets) {
        const bool required = spec.module == core::ModuleId::Minecraft;
        const auto status = resolver.status(spec.id);

        if (required) {
            ++requiredTargets;
            if (status.available) {
                ++requiredPassed;
                log.info("[REQUIRED] target {}: PASS @ 0x{:x} ({})",
                         core::targetName(spec.id), status.address, status.reason);
            } else {
                requiredOk = false;
                log.error("[REQUIRED] target {}: FAIL ({})",
                          core::targetName(spec.id), status.reason);
            }
            continue;
        }

        if (!resolver.module(spec.module)) {
            log.info("[OPTIONAL] sentinel {}: SKIP (module not loaded)", core::targetName(spec.id));
        } else if (status.available) {
            ++optionalSentinelsPassed;
            log.info("[OPTIONAL] sentinel {}: PASS @ 0x{:x} ({})",
                     core::targetName(spec.id), status.address, status.reason);
        } else {
            log.warn("[OPTIONAL] sentinel {}: FAIL ({})",
                     core::targetName(spec.id), status.reason);
        }
    }

    log.info("------------------------------------------------------------");
    log.info("required Minecraft targets: {}/{} PASS", requiredPassed, requiredTargets);
    log.info("auxiliary modules observed: {}", optionalLoaded);
    log.info("auxiliary sentinels resolved: {}", optionalSentinelsPassed);
    log.info("Resolver result: {}", requiredOk ? "PASS" : "FAIL-CLOSED");
    log.info("No native target was invoked during this resolver phase.");
    log.info("============================================================");

    return requiredOk;
}
} // namespace bedrock::api::selftest
