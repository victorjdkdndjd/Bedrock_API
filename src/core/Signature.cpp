#include "BedrockAPI/Core/Signature.hpp"
#include <charconv>
#include <cctype>
#include <sstream>

namespace bedrock::api::core {
std::optional<Signature> parseSignature(std::string_view text) {
    Signature out; std::size_t i=0;
    while(i<text.size()) {
        while(i<text.size() && std::isspace(static_cast<unsigned char>(text[i]))) ++i;
        if(i>=text.size()) break;
        std::size_t j=i; while(j<text.size() && !std::isspace(static_cast<unsigned char>(text[j]))) ++j;
        auto token=text.substr(i,j-i); i=j;
        if(token=="?" || token=="??") { out.bytes.push_back(0); out.exact.push_back(false); continue; }
        if(token.size()!=2) return std::nullopt;
        unsigned value{}; auto r=std::from_chars(token.data(),token.data()+token.size(),value,16);
        if(r.ec!=std::errc{} || r.ptr!=token.data()+token.size() || value>0xff) return std::nullopt;
        out.bytes.push_back(static_cast<std::uint8_t>(value)); out.exact.push_back(true);
    }
    if(out.bytes.empty()) return std::nullopt; return out;
}
bool matchesAt(const Signature& sig,const void* address) noexcept {
    if(!address || sig.bytes.size()!=sig.exact.size()) return false;
    auto* p=static_cast<const std::uint8_t*>(address);
    for(std::size_t i=0;i<sig.bytes.size();++i) if(sig.exact[i] && p[i]!=sig.bytes[i]) return false;
    return true;
}
std::vector<std::uintptr_t> scanExecutable(const ModuleInfo& module,const Signature& sig,std::size_t maxMatches) {
    std::vector<std::uintptr_t> hits; if(sig.empty()||maxMatches==0)return hits;
    for(const auto& seg:module.segments){ if(!seg.executable || !seg.readable || seg.end<=seg.begin || seg.end-seg.begin<sig.size()) continue;
        auto* begin=reinterpret_cast<const std::uint8_t*>(seg.begin); auto count=(seg.end-seg.begin)-sig.size()+1;
        for(std::size_t i=0;i<count;++i){ if(matchesAt(sig,begin+i)){hits.push_back(seg.begin+i); if(hits.size()>=maxMatches)return hits;} }
    } return hits;
}
}
