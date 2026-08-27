#pragma once
#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <vector>
#include "BedrockAPI/Core/Module.hpp"

namespace bedrock::api::core {

struct Signature {
    std::vector<std::uint8_t> bytes;
    std::vector<bool> exact;
    [[nodiscard]] bool empty() const noexcept { return bytes.empty(); }
    [[nodiscard]] std::size_t size() const noexcept { return bytes.size(); }
};

[[nodiscard]] std::optional<Signature> parseSignature(std::string_view text);
[[nodiscard]] bool matchesAt(const Signature& sig, const void* address) noexcept;
[[nodiscard]] std::vector<std::uintptr_t> scanExecutable(const ModuleInfo& module, const Signature& sig, std::size_t maxMatches = 2);

} // namespace bedrock::api::core
