#pragma once
#include <cstdint>
#include "BedrockAPI/Core/Resolver.hpp"
namespace bedrock::api {
class Runtime {
public:
    static Runtime& instance();
    bool initialize();
    [[nodiscard]] core::Resolver& resolver() noexcept { return mResolver; }
    [[nodiscard]] const core::Resolver& resolver() const noexcept { return mResolver; }
    [[nodiscard]] std::uintptr_t address(core::TargetId id) { return mResolver.resolve(id); }
    template <class Fn> [[nodiscard]] Fn function(core::TargetId id) { return reinterpret_cast<Fn>(address(id)); }
private:
    core::Resolver mResolver;
};
}
