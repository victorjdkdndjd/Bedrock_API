#pragma once

#include <cstdint>

namespace bedrock::api::hookprobe {

[[nodiscard]] bool install();
void uninstall() noexcept;
[[nodiscard]] std::uint64_t calls() noexcept;

} // namespace bedrock::api::hookprobe
