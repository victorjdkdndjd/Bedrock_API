# ChinaBedrock Translator

Android app experimental para importar o APK do Minecraft China (`com.netease.x19`), localizar textos chineses em assets textuais, aplicar um dicionário pt-BR inicial, reconstruir e assinar um APK de teste.

## v0.1.0

- Importa `.apk` pelo seletor do Android.
- Confirma package/version com `PackageManager`.
- Varre `assets/` e recursos textuais (`.json`, `.lang`, `.txt`, `.xml`, `.properties`).
- Traduz somente conteúdo textual seguro; JSON mantém as chaves e traduz valores.
- Preserva bibliotecas `.so` armazenadas e adiciona padding de 4 KiB para manter alinhamento básico.
- Remove assinaturas antigas `META-INF` e assina o APK gerado com uma chave de teste criada no Android Keystore usando APK Signature Scheme v1/v2/v3.
- Exporta o resultado via Storage Access Framework.

## Limitações atuais

`resources.arsc` e XML binário de `res/` ainda não são recompilados. Isso significa que a v0.1 traduz principalmente textos presentes em assets. Textos remotos/servidor também não fazem parte do APK. APK modificado não pode manter a assinatura oficial da NetEase e pode ser rejeitado por verificações de integridade do jogo.
