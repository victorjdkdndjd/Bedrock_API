#include "BedrockAPI/Core/Module.hpp"
#include <algorithm>
#include <cstring>
#include <iomanip>
#include <link.h>
#include <sstream>

namespace bedrock::api::core {
namespace {
constexpr std::size_t align4(std::size_t n) noexcept { return (n + 3u) & ~std::size_t(3u); }
std::string hex(const unsigned char* p, std::size_t n) {
    std::ostringstream out; out << std::hex << std::setfill('0');
    for (std::size_t i=0;i<n;++i) out << std::setw(2) << static_cast<unsigned>(p[i]);
    return out.str();
}
std::string buildId(const dl_phdr_info* info) {
    for (ElfW(Half) i=0;i<info->dlpi_phnum;++i) {
        const auto& ph = info->dlpi_phdr[i]; if (ph.p_type != PT_NOTE) continue;
        auto* cur = reinterpret_cast<const unsigned char*>(info->dlpi_addr + ph.p_vaddr);
        auto* end = cur + ph.p_memsz;
        while (cur + sizeof(ElfW(Nhdr)) <= end) {
            auto* note = reinterpret_cast<const ElfW(Nhdr)*>(cur); cur += sizeof(ElfW(Nhdr));
            if (cur + align4(note->n_namesz) + align4(note->n_descsz) > end) break;
            auto* name = reinterpret_cast<const char*>(cur); cur += align4(note->n_namesz);
            auto* desc = cur; cur += align4(note->n_descsz);
            if (note->n_type == NT_GNU_BUILD_ID && note->n_namesz >= 3 && std::memcmp(name,"GNU",3)==0)
                return hex(desc, note->n_descsz);
        }
    }
    return {};
}
struct Context { std::vector<ModuleInfo>* out; };
int callback(dl_phdr_info* info, std::size_t, void* opaque) {
    std::string path = info->dlpi_name ? info->dlpi_name : "";
    auto slash = path.find_last_of('/');
    std::string name = slash == std::string::npos ? path : path.substr(slash+1);
    auto id = moduleIdFromName(name); if (!id) return 0;
    ModuleInfo m; m.id=*id; m.name=name; m.path=path; m.base=static_cast<std::uintptr_t>(info->dlpi_addr); m.buildId=buildId(info);
    for (ElfW(Half) i=0;i<info->dlpi_phnum;++i) {
        const auto& ph=info->dlpi_phdr[i]; if (ph.p_type != PT_LOAD || ph.p_memsz == 0) continue;
        const auto begin=static_cast<std::uintptr_t>(info->dlpi_addr + ph.p_vaddr);
        m.segments.push_back({begin, begin+static_cast<std::uintptr_t>(ph.p_memsz), bool(ph.p_flags&PF_R), bool(ph.p_flags&PF_W), bool(ph.p_flags&PF_X)});
    }
    static_cast<Context*>(opaque)->out->push_back(std::move(m)); return 0;
}
}

bool ModuleInfo::contains(std::uintptr_t p) const noexcept { for (auto& s:segments) if(s.contains(p)) return true; return false; }
bool ModuleInfo::containsExecutable(std::uintptr_t p) const noexcept { for (auto& s:segments) if(s.executable&&s.contains(p)) return true; return false; }
const char* moduleName(ModuleId id) noexcept {
    switch(id){
    case ModuleId::Minecraft:return "libminecraftpe.so"; case ModuleId::HttpClient:return "libHttpClient.Android.so";
    case ModuleId::MediaDecoders:return "libMediaDecoders_Android.so"; case ModuleId::PlayFabMultiplayer:return "libPlayFabMultiplayer.so";
    case ModuleId::PairIpCore:return "libpairipcore.so"; case ModuleId::Conscrypt:return "libconscrypt_jni.so";
    case ModuleId::CxxShared:return "libc++_shared.so"; case ModuleId::Fmod:return "libfmod.so"; case ModuleId::MaeSdk:return "libmaesdk.so";
    } return "unknown";
}
std::optional<ModuleId> moduleIdFromName(std::string_view n) noexcept {
    for (auto id:{ModuleId::Minecraft,ModuleId::HttpClient,ModuleId::MediaDecoders,ModuleId::PlayFabMultiplayer,ModuleId::PairIpCore,ModuleId::Conscrypt,ModuleId::CxxShared,ModuleId::Fmod,ModuleId::MaeSdk})
        if(n==moduleName(id)) return id; return std::nullopt;
}
std::vector<ModuleInfo> enumerateLoadedModules(){ std::vector<ModuleInfo> out; Context ctx{&out}; dl_iterate_phdr(callback,&ctx); return out; }
std::optional<ModuleInfo> findLoadedModule(ModuleId id){ auto v=enumerateLoadedModules(); auto it=std::find_if(v.begin(),v.end(),[&](auto& m){return m.id==id;}); if(it==v.end())return std::nullopt; return *it; }
}
