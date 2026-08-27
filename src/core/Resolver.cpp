#include "BedrockAPI/Core/Resolver.hpp"
#include "BedrockAPI/Core/Signature.hpp"
#include <dlfcn.h>
#include <algorithm>

namespace bedrock::api::core {
bool Resolver::initialize(){ mModules=enumerateLoadedModules(); mCache.clear(); auto* mc=module(ModuleId::Minecraft); mExactMinecraftBuild=mc && mc->buildId==currentBuildProfile().minecraftBuildId; return mc!=nullptr; }
void Resolver::clearCache(){mCache.clear();}
const ModuleInfo* Resolver::module(ModuleId id) const noexcept { auto it=std::find_if(mModules.begin(),mModules.end(),[&](auto& m){return m.id==id;}); return it==mModules.end()?nullptr:&*it; }
const TargetSpec* Resolver::spec(TargetId id) const noexcept { for(auto& s:currentBuildProfile().targets) if(s.id==id)return &s; return nullptr; }
std::uintptr_t Resolver::resolveExport(const ModuleInfo& m,std::string_view symbol,std::string& reason) const {
    void* h=dlopen(m.path.empty()?m.name.c_str():m.path.c_str(),RTLD_NOW|RTLD_NOLOAD); if(!h){reason="module handle unavailable";return 0;}
    std::string s(symbol); void* p=dlsym(h,s.c_str()); dlclose(h); if(!p){reason="export not found";return 0;}
    auto a=reinterpret_cast<std::uintptr_t>(p); if(!m.containsExecutable(a)){reason="export address is outside executable segments";return 0;} reason="export resolved"; return a;
}
std::uintptr_t Resolver::resolveVerifiedRva(const ModuleInfo& m,const TargetSpec& s,std::string& reason) const {
    if(s.module==ModuleId::Minecraft && !mExactMinecraftBuild){reason="unknown Minecraft Build ID";return 0;}
    auto a=m.base+s.rva; if(!m.containsExecutable(a)){reason="RVA outside executable segment";return 0;}
    auto sig=parseSignature(s.signature); if(!sig){reason="invalid embedded signature";return 0;}
    if(matchesAt(*sig,reinterpret_cast<const void*>(a))){reason="exact-build RVA + signature verified";return a;}
    auto hits=scanExecutable(m,*sig,2); if(hits.size()==1){reason="RVA drifted; unique signature fallback resolved";return hits[0];}
    reason=hits.empty()?"signature mismatch/no fallback match":"signature is not unique"; return 0;
}
TargetStatus Resolver::status(TargetId id){
    auto key=static_cast<std::uint16_t>(id); if(auto it=mCache.find(key);it!=mCache.end())return it->second;
    TargetStatus st; st.id=id; auto* s=spec(id); if(!s){st.reason="target not in profile";mCache[key]=st;return st;} auto* m=module(s->module); if(!m){st.reason="module not loaded";mCache[key]=st;return st;}
    st.exactBuild=s->module!=ModuleId::Minecraft || mExactMinecraftBuild;
    if(s->kind==ResolveKind::Export) st.address=resolveExport(*m,s->exportName,st.reason); else st.address=resolveVerifiedRva(*m,*s,st.reason);
    st.available=st.address!=0; mCache[key]=st; return st;
}
std::uintptr_t Resolver::resolve(TargetId id){return status(id).address;}
}
