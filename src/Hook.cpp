#include "BedrockAPI/Hook.hpp"
#include <pl/memory/Hook.hpp>
namespace bedrock::api {
struct Hook::Impl { std::unique_ptr<pl::memory::HookHandle> handle; };
Hook::Hook():mImpl(std::make_unique<Impl>()){} Hook::~Hook()=default; Hook::Hook(Hook&&) noexcept=default; Hook& Hook::operator=(Hook&&) noexcept=default;
bool Hook::install(std::uintptr_t target,void* detour,void** original) noexcept { reset(); if(!target||!detour||!original)return false; auto h=std::make_unique<pl::memory::HookHandle>(reinterpret_cast<void*>(target),detour,original,pl::memory::HookPriority::Normal); if(!h->installed())return false; mImpl->handle=std::move(h); return true; }
void Hook::reset() noexcept { if(mImpl)mImpl->handle.reset(); }
bool Hook::installed() const noexcept { return mImpl&&mImpl->handle&&mImpl->handle->installed(); }
}
