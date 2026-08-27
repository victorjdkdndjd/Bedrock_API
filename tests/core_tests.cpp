#include "BedrockAPI/Core/Signature.hpp"
#include "BedrockAPI/Core/Profile.hpp"
#include <array>
#include <cassert>
#include <iostream>
int main(){
    using namespace bedrock::api::core;
    auto s=parseSignature("AA BB ?? 0f"); assert(s && s->size()==4); std::array<unsigned char,4> a{0xAA,0xBB,0x44,0x0F}; assert(matchesAt(*s,a.data())); a[1]=0; assert(!matchesAt(*s,a.data()));
    assert(!parseSignature("GG")); assert(!currentBuildProfile().minecraftBuildId.empty()); assert(currentBuildProfile().targets.size()>=15);
    std::cout<<"Bedrock_API core tests PASS\n"; return 0;
}
