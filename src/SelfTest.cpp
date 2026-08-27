#include "SelfTest.hpp"
#include "BedrockAPI/Runtime.hpp"
#include "BedrockAPI/Core/Profile.hpp"
#include "mod/BedrockApiMod.hpp"
namespace bedrock::api::selftest {
bool run(){ auto& log=BedrockApiMod::instance().getSelf().getLogger(); auto& r=Runtime::instance().resolver(); bool ok=true;
    log.info("[selftest] exact Minecraft build: {}",r.exactMinecraftBuild()?"yes":"no");
    for(auto id:{core::ModuleId::Minecraft,core::ModuleId::HttpClient,core::ModuleId::MediaDecoders,core::ModuleId::PlayFabMultiplayer,core::ModuleId::PairIpCore,core::ModuleId::Conscrypt,core::ModuleId::CxxShared,core::ModuleId::Fmod}){
        auto* m=r.module(id); log.info("[selftest] module {}: {}{}",core::moduleName(id),m?"loaded":"missing",m&&!m->buildId.empty()?" build-id="+m->buildId:""); if(!m)ok=false;
    }
    for(const auto& spec:core::currentBuildProfile().targets){auto st=r.status(spec.id); log.info("[selftest] target {}: {} @ 0x{:x} ({})",core::targetName(spec.id),st.available?"PASS":"FAIL",st.address,st.reason); if(!st.available)ok=false;}
    log.info("[selftest] result: {}",ok?"PASS":"PARTIAL/FAIL-CLOSED"); return ok; }
}
