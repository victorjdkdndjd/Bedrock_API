#include "BedrockAPI/Game/Minecraft.hpp"
#include "BedrockAPI/Runtime.hpp"
#include "BedrockAPI/Core/Targets.hpp"
namespace bedrock::api::game {
LocalPlayer* getLocalPlayer(ClientInstance* instance) noexcept {
    if(!instance)return nullptr; using Fn=LocalPlayer*(*)(ClientInstance*); auto fn=Runtime::instance().function<Fn>(core::TargetId::ClientInstanceGetLocalPlayer); return fn?fn(instance):nullptr;
}
Block* getBlock(BlockSource* source,const BlockPos& pos) noexcept {
    if(!source)return nullptr; using Fn=Block*(*)(BlockSource*,const BlockPos&); auto fn=Runtime::instance().function<Fn>(core::TargetId::BlockSourceGetBlock); return fn?fn(source,pos):nullptr;
}
}
