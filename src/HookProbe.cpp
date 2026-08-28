#include "HookProbe.hpp"

#include "BedrockAPI/Core/Targets.hpp"
#include "BedrockAPI/Hook.hpp"
#include "BedrockAPI/Runtime.hpp"
#include "mod/BedrockApiMod.hpp"

#include <android/log.h>
#include <atomic>
#include <cinttypes>

namespace bedrock::api::hookprobe {
namespace {
using NormalTickFn = void (*)(void*);

Hook gHook;
std::atomic<std::uint64_t> gCalls{0};
NormalTickFn gOriginal{};

void normalTickDetour(void* self) {
    const auto count = gCalls.fetch_add(1, std::memory_order_relaxed) + 1;

    if (count == 1 || count == 100 || count == 1000 || (count % 1200) == 0) {
        __android_log_print(
            ANDROID_LOG_INFO,
            "Bedrock API",
            "[HookTest] LocalPlayer::normalTick calls=%" PRIu64 " self=%p",
            count,
            self
        );
    }

    if (const auto original = gOriginal) {
        original(self);
    }
}
} // namespace

bool install() {
    auto& log = BedrockApiMod::instance().getSelf().getLogger();
    auto& resolver = Runtime::instance().resolver();

    if (gHook.installed()) {
        log.info("[HookTest] LocalPlayer::normalTick already installed");
        return true;
    }

    if (!resolver.exactMinecraftBuild()) {
        log.error("[HookTest] install blocked: exact Minecraft build gate is closed");
        return false;
    }

    const auto status = resolver.status(core::TargetId::LocalPlayerNormalTick);
    if (!status.available) {
        log.error("[HookTest] install blocked: LocalPlayer::normalTick unresolved ({})", status.reason);
        return false;
    }

    gCalls.store(0, std::memory_order_relaxed);
    gOriginal = nullptr;
    void* original = nullptr;

    if (!gHook.install(
            status.address,
            reinterpret_cast<void*>(&normalTickDetour),
            &original)) {
        log.error("[HookTest] LocalPlayer::normalTick hook install: FAIL");
        return false;
    }

    gOriginal = reinterpret_cast<NormalTickFn>(original);
    if (!gOriginal) {
        gHook.reset();
        log.error("[HookTest] LocalPlayer::normalTick hook install: FAIL (null trampoline)");
        return false;
    }

    log.info("[HookTest] LocalPlayer::normalTick hook install: PASS target=0x{:x} trampoline={}",
             status.address, original);
    log.info("[HookTest] observer only: counts calls, then immediately calls original; no gameplay state is changed");
    return true;
}

void uninstall() noexcept {
    if (!gHook.installed()) {
        gOriginal = nullptr;
        return;
    }

    gHook.reset();
    gOriginal = nullptr;
    BedrockApiMod::instance().getSelf().getLogger().info(
        "[HookTest] LocalPlayer::normalTick hook removed; final calls={}",
        gCalls.load(std::memory_order_relaxed));
}

std::uint64_t calls() noexcept {
    return gCalls.load(std::memory_order_relaxed);
}

} // namespace bedrock::api::hookprobe
