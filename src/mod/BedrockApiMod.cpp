#include "mod/BedrockApiMod.hpp"
#include "BedrockAPI/Runtime.hpp"
#include "BedrockAPI/Version.hpp"
#include "SelfTest.hpp"
namespace bedrock::api {
BedrockApiMod& BedrockApiMod::instance(){static BedrockApiMod m;return m;}
BedrockApiMod::BedrockApiMod():mSelf(*ll::mod::NativeMod::current()){}
bool BedrockApiMod::load(){getSelf().getLogger().info("Bedrock API {} loading",VersionString);return true;}
bool BedrockApiMod::enable(){if(!Runtime::instance().initialize()){getSelf().getLogger().warn("libminecraftpe.so not loaded; API remains fail-closed");return true;} selftest::run(); return true;}
bool BedrockApiMod::disable(){return true;} bool BedrockApiMod::unload(){return true;}
}
