#pragma once
#include <cstdint>
#include <string_view>
#include "BedrockAPI/Core/Module.hpp"

namespace bedrock::api::core {

enum class TargetId : std::uint16_t {
    LocalPlayerNormalTick,
    ClientInstanceUpdate,
    ClientInstanceGetLocalPlayer,
    BlockSourceGetBlock,
    GameModeStartDestroyBlock,
    SurvivalModeStartDestroyBlock,
    GameModeSendTryDestroyBlock,
    GameModeDestroyBlock,
    HttpClientGetLibVersion,
    MediaCreateMP4Demuxer,
    PlayFabGetErrorMessage,
    PairIpJniOnLoad,
    ConscryptJniOnLoad,
    CxxDemangle,
    FmodSystemGetVersion,
    MaeSqliteLibVersion,
};

enum class Confidence : std::uint8_t {
    Discovered,
    StaticVerified,
    RuntimeResolved,
    DeviceObserved,
    DeviceVerified,
};

enum class ResolveKind : std::uint8_t { Export, VerifiedRva };

struct TargetSpec {
    TargetId id{};
    ModuleId module{};
    ResolveKind kind{};
    std::uintptr_t rva{};
    std::string_view exportName;
    std::string_view signature;
    Confidence confidence{Confidence::Discovered};
    bool callable{};
    std::string_view note;
};

[[nodiscard]] const char* targetName(TargetId id) noexcept;

} // namespace bedrock::api::core
