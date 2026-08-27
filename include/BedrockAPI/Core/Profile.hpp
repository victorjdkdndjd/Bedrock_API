#pragma once
#include <span>
#include <string_view>
#include "BedrockAPI/Core/Targets.hpp"

namespace bedrock::api::core {

struct BuildProfile {
    std::string_view name;
    std::string_view minecraftBuildId;
    std::string_view minecraftSha256;
    std::string_view minecraftVersionHint;
    std::span<const TargetSpec> targets;
};

[[nodiscard]] const BuildProfile& currentBuildProfile() noexcept;

} // namespace bedrock::api::core
