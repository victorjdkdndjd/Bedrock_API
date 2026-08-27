#include "BedrockAPI/Runtime.hpp"
namespace bedrock::api {
Runtime& Runtime::instance(){static Runtime r; return r;}
bool Runtime::initialize(){return mResolver.initialize();}
}
