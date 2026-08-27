#pragma once
#include "BedrockAPI/Game/Types.hpp"
namespace bedrock::api::game {
[[nodiscard]] LocalPlayer* getLocalPlayer(ClientInstance* instance) noexcept;
[[nodiscard]] Block* getBlock(BlockSource* source, const BlockPos& pos) noexcept;
}
