#pragma once
#include <cstdint>
#include <memory>
namespace bedrock::api {
class Hook {
public:
    Hook();
    ~Hook();
    Hook(Hook&&) noexcept;
    Hook& operator=(Hook&&) noexcept;
    Hook(const Hook&) = delete;
    Hook& operator=(const Hook&) = delete;
    bool install(std::uintptr_t target, void* detour, void** original) noexcept;
    void reset() noexcept;
    [[nodiscard]] bool installed() const noexcept;
private:
    struct Impl;
    std::unique_ptr<Impl> mImpl;
};
}
