# Consumer example

A mod should query the API instead of copying offsets:

```cpp
#include <BedrockAPI/BedrockAPI.hpp>
using namespace bedrock::api;

auto& api = Runtime::instance();
auto tick = api.address(core::TargetId::LocalPlayerNormalTick);
if (!tick) {
    // Unsupported build: leave the feature disabled.
    return;
}
```

For a read-only helper:

```cpp
auto* player = game::getLocalPlayer(clientInstance);
auto* block = game::getBlock(blockSource, {x, y, z});
```

For hooks, resolve the target first and only install when it is available:

```cpp
Hook hook;
void* original = nullptr;
if (!hook.install(tick, reinterpret_cast<void*>(&myDetour), &original)) {
    // Fail closed.
}
```

Consumer mods should never store hardcoded addresses. Build-specific knowledge belongs in Bedrock_API profiles.
