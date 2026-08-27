#pragma once
#include <cstdint>
namespace bedrock::api::game {
struct BlockPos { std::int32_t x{}, y{}, z{}; };
class LocalPlayer;
class ClientInstance;
class BlockSource;
class Block;
class GameMode;
class SurvivalMode;
}
