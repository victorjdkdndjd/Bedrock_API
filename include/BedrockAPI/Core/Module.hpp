#pragma once
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

namespace bedrock::api::core {

enum class ModuleId : std::uint8_t {
    Minecraft,
    HttpClient,
    MediaDecoders,
    PlayFabMultiplayer,
    PairIpCore,
    Conscrypt,
    CxxShared,
    Fmod,
    MaeSdk,
};

struct Segment {
    std::uintptr_t begin{};
    std::uintptr_t end{};
    bool readable{};
    bool writable{};
    bool executable{};
    [[nodiscard]] bool contains(std::uintptr_t p) const noexcept { return p >= begin && p < end; }
};

struct ModuleInfo {
    ModuleId id{};
    std::string name;
    std::string path;
    std::string buildId;
    std::uintptr_t base{};
    std::vector<Segment> segments;

    [[nodiscard]] bool contains(std::uintptr_t p) const noexcept;
    [[nodiscard]] bool containsExecutable(std::uintptr_t p) const noexcept;
};

[[nodiscard]] const char* moduleName(ModuleId id) noexcept;
[[nodiscard]] std::optional<ModuleId> moduleIdFromName(std::string_view name) noexcept;
[[nodiscard]] std::vector<ModuleInfo> enumerateLoadedModules();
[[nodiscard]] std::optional<ModuleInfo> findLoadedModule(ModuleId id);

} // namespace bedrock::api::core
