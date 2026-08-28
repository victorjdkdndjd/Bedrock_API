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
using ClientUpdateFn = void (*)(void*, bool);
using GetLocalPlayerFn = void* (*)(void*);

Hook gNormalTickHook;
Hook gClientUpdateHook;

std::atomic<std::uint64_t> gTickCalls{0};
std::atomic<std::uint64_t> gClientCalls{0};
std::atomic<std::uint64_t> gPlayerMatches{0};
std::atomic<std::uint64_t> gPlayerMismatches{0};
std::atomic<void*> gLastTickPlayer{nullptr};

NormalTickFn gNormalTickOriginal{};
ClientUpdateFn gClientUpdateOriginal{};
GetLocalPlayerFn gGetLocalPlayer{};

bool shouldSample(std::uint64_t count) noexcept {
    return count == 1 || count == 100 || count == 1000 || (count % 1200) == 0;
}

void normalTickDetour(void* self) {
    const auto count = gTickCalls.fetch_add(1, std::memory_order_relaxed) + 1;
    gLastTickPlayer.store(self, std::memory_order_release);

    if (shouldSample(count)) {
        __android_log_print(
            ANDROID_LOG_INFO,
            "Bedrock API",
            "[PlayerBridge] LocalPlayer::normalTick calls=%" PRIu64 " self=%p",
            count,
            self
        );
    }

    if (const auto original = gNormalTickOriginal) {
        original(self);
    }
}

void clientUpdateDetour(void* self, bool flag) {
    if (const auto original = gClientUpdateOriginal) {
        original(self, flag);
    }

    const auto count = gClientCalls.fetch_add(1, std::memory_order_relaxed) + 1;
    if (!shouldSample(count) || !self || !gGetLocalPlayer) {
        return;
    }

    // Read-only getter call on the live ClientInstance captured by the game's own update path.
    void* fromClient = gGetLocalPlayer(self);
    void* fromTick = gLastTickPlayer.load(std::memory_order_acquire);

    const char* verdict = "WAIT";
    if (fromClient && fromTick) {
        if (fromClient == fromTick) {
            gPlayerMatches.fetch_add(1, std::memory_order_relaxed);
            verdict = "MATCH";
        } else {
            gPlayerMismatches.fetch_add(1, std::memory_order_relaxed);
            verdict = "MISMATCH";
        }
    } else if (!fromClient && !fromTick) {
        verdict = "NO_PLAYER";
    } else {
        verdict = "TRANSITION";
    }

    __android_log_print(
        ANDROID_LOG_INFO,
        "Bedrock API",
        "[PlayerBridge] ClientInstance::update calls=%" PRIu64
        " client=%p getLocalPlayer=%p tickPlayer=%p verdict=%s matches=%" PRIu64
        " mismatches=%" PRIu64,
        count,
        self,
        fromClient,
        fromTick,
        verdict,
        gPlayerMatches.load(std::memory_order_relaxed),
        gPlayerMismatches.load(std::memory_order_relaxed)
    );
}

void clearState() noexcept {
    gNormalTickOriginal = nullptr;
    gClientUpdateOriginal = nullptr;
    gGetLocalPlayer = nullptr;
    gLastTickPlayer.store(nullptr, std::memory_order_release);
}
} // namespace

bool install() {
    auto& log = BedrockApiMod::instance().getSelf().getLogger();
    auto& resolver = Runtime::instance().resolver();

    if (gNormalTickHook.installed() || gClientUpdateHook.installed()) {
        log.info("[PlayerBridge] probe hooks already installed");
        return gNormalTickHook.installed() && gClientUpdateHook.installed();
    }

    if (!resolver.exactMinecraftBuild()) {
        log.error("[PlayerBridge] install blocked: exact Minecraft build gate is closed");
        return false;
    }

    const auto tickStatus = resolver.status(core::TargetId::LocalPlayerNormalTick);
    const auto updateStatus = resolver.status(core::TargetId::ClientInstanceUpdate);
    const auto getterStatus = resolver.status(core::TargetId::ClientInstanceGetLocalPlayer);
    const auto blockStatus = resolver.status(core::TargetId::BlockSourceGetBlock);

    if (!tickStatus.available || !updateStatus.available || !getterStatus.available) {
        log.error(
            "[PlayerBridge] install blocked: tick={} update={} getter={}",
            tickStatus.available ? "PASS" : "FAIL",
            updateStatus.available ? "PASS" : "FAIL",
            getterStatus.available ? "PASS" : "FAIL"
        );
        return false;
    }

    gTickCalls.store(0, std::memory_order_relaxed);
    gClientCalls.store(0, std::memory_order_relaxed);
    gPlayerMatches.store(0, std::memory_order_relaxed);
    gPlayerMismatches.store(0, std::memory_order_relaxed);
    gLastTickPlayer.store(nullptr, std::memory_order_release);
    clearState();

    gGetLocalPlayer = reinterpret_cast<GetLocalPlayerFn>(getterStatus.address);

    void* tickOriginal = nullptr;
    if (!gNormalTickHook.install(
            tickStatus.address,
            reinterpret_cast<void*>(&normalTickDetour),
            &tickOriginal) || !tickOriginal) {
        gNormalTickHook.reset();
        clearState();
        log.error("[PlayerBridge] LocalPlayer::normalTick hook install: FAIL");
        return false;
    }
    gNormalTickOriginal = reinterpret_cast<NormalTickFn>(tickOriginal);

    void* updateOriginal = nullptr;
    if (!gClientUpdateHook.install(
            updateStatus.address,
            reinterpret_cast<void*>(&clientUpdateDetour),
            &updateOriginal) || !updateOriginal) {
        gClientUpdateHook.reset();
        gNormalTickHook.reset();
        clearState();
        log.error("[PlayerBridge] ClientInstance::update hook install: FAIL (rolled back normalTick hook)");
        return false;
    }
    gClientUpdateOriginal = reinterpret_cast<ClientUpdateFn>(updateOriginal);

    log.info("[PlayerBridge] LocalPlayer::normalTick hook: PASS target=0x{:x} trampoline={}",
             tickStatus.address, tickOriginal);
    log.info("[PlayerBridge] ClientInstance::update hook: PASS target=0x{:x} trampoline={}",
             updateStatus.address, updateOriginal);
    log.info("[PlayerBridge] ClientInstance::getLocalPlayer read-only getter: ARMED @ 0x{:x}",
             getterStatus.address);
    if (blockStatus.available) {
        log.info("[PlayerBridge] BlockSource::getBlock remains resolver-only for v0.3 @ 0x{:x} (not invoked/hooked)",
                 blockStatus.address);
    }
    log.info("[PlayerBridge] policy: sparse pointer identity checks only; no gameplay state is changed");
    return true;
}

void uninstall() noexcept {
    const bool hadHooks = gClientUpdateHook.installed() || gNormalTickHook.installed();

    // Remove the consumer hook first, then the producer hook.
    gClientUpdateHook.reset();
    gNormalTickHook.reset();

    const auto ticks = gTickCalls.load(std::memory_order_relaxed);
    const auto clients = gClientCalls.load(std::memory_order_relaxed);
    const auto matches = gPlayerMatches.load(std::memory_order_relaxed);
    const auto mismatches = gPlayerMismatches.load(std::memory_order_relaxed);
    clearState();

    if (hadHooks) {
        BedrockApiMod::instance().getSelf().getLogger().info(
            "[PlayerBridge] hooks removed; tickCalls={} clientCalls={} matches={} mismatches={}",
            ticks, clients, matches, mismatches);
    }
}

std::uint64_t calls() noexcept {
    return gTickCalls.load(std::memory_order_relaxed);
}

std::uint64_t clientCalls() noexcept {
    return gClientCalls.load(std::memory_order_relaxed);
}

std::uint64_t matches() noexcept {
    return gPlayerMatches.load(std::memory_order_relaxed);
}

std::uint64_t mismatches() noexcept {
    return gPlayerMismatches.load(std::memory_order_relaxed);
}

} // namespace bedrock::api::hookprobe
