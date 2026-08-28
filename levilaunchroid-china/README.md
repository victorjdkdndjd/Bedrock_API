# LeviLaunchroid China Diagnostic v0.1

Build experimental para testar o Minecraft China (`com.netease.x19`) no LeviLaunchroid.

## O que o workflow faz

1. Clona o `LiteLDev/LeviLaunchroid` oficial.
2. Aplica o patch diagnóstico v0.1.
3. Mantém o caminho normal do Bedrock global intacto.
4. Adiciona um caminho de bibliotecas para o Minecraft China: `libc++_shared`, FMOD, PhysX, `libandroidmainruns` e `libminecraftpe`.
5. Compila um APK Android de debug.
6. Publica o APK como artifact do GitHub Actions.

Este diagnóstico não contorna login, KYC, anti-cheat ou mecanismos de segurança da NetEase.

Branch de teste: `levilaunchroid-china-diagnostic`.
