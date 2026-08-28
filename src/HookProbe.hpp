#pragma once

#include <cstdint>

namespace bedrock::api::hookprobe {

[[nodiscard]] bool install();
void uninstall() noexcept;
[[nodiscard]] std::uint64_t calls() noexcept;
[[nodiscard]] std::uint64_t clientCalls() noexcept;
[[nodiscard]] std::uint64_t matches() noexcept;
[[nodiscard]] std::uint64_t mismatches() noexcept;

} // namespace bedrock::api::hookprobe
