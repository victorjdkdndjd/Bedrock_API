#pragma once
#include <cstdint>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>
#include "BedrockAPI/Core/Profile.hpp"

namespace bedrock::api::core {

struct TargetStatus {
    TargetId id{};
    std::uintptr_t address{};
    bool available{};
    bool exactBuild{};
    std::string reason;
};

class Resolver {
public:
    bool initialize();
    [[nodiscard]] std::uintptr_t resolve(TargetId id);
    [[nodiscard]] TargetStatus status(TargetId id);
    [[nodiscard]] const ModuleInfo* module(ModuleId id) const noexcept;
    [[nodiscard]] bool exactMinecraftBuild() const noexcept { return mExactMinecraftBuild; }
    [[nodiscard]] const std::vector<ModuleInfo>& modules() const noexcept { return mModules; }
    void clearCache();

private:
    const TargetSpec* spec(TargetId id) const noexcept;
    std::uintptr_t resolveExport(const ModuleInfo& module, std::string_view symbol, std::string& reason) const;
    std::uintptr_t resolveVerifiedRva(const ModuleInfo& module, const TargetSpec& spec, std::string& reason) const;

    std::vector<ModuleInfo> mModules;
    bool mExactMinecraftBuild{};
    std::unordered_map<std::uint16_t, TargetStatus> mCache;
};

} // namespace bedrock::api::core
