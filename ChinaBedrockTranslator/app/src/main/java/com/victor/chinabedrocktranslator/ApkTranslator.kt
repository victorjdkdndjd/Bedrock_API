package com.victor.chinabedrocktranslator

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.security.auth.x500.X500Principal

class ApkTranslator(private val context: Context) {
    private val translations = TranslationDictionary.ptBr

    fun analyze(file: File): Analysis {
        val pkg = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
        val packageName = pkg?.packageName ?: "desconhecido"
        val versionName = pkg?.versionName ?: "desconhecida"
        var textFiles = 0
        var chineseFiles = 0
        var chineseHits = 0
        var resourcesArsc = false
        val examples = LinkedHashMap<String, String>()

        ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                if (entry.isDirectory) continue
                if (entry.name == "resources.arsc") resourcesArsc = true
                if (!isTextCandidate(entry.name) || entry.size > MAX_TEXT_BYTES) continue
                val bytes = readEntryBytes(zin, MAX_TEXT_BYTES.toInt())
                val text = decodeUtf8(bytes) ?: continue
                textFiles++
                var localCount = 0
                val localExamples = ArrayList<String>(8)
                val iterator = chineseRegex.findAll(text).iterator()
                while (iterator.hasNext()) {
                    val value = iterator.next().value
                    localCount++
                    if (localExamples.size < 8) localExamples.add(value)
                }
                if (localCount > 0) {
                    chineseFiles++
                    chineseHits += localCount
                    if (examples.size < 30) examples[entry.name] = localExamples.joinToString(" | ")
                }
            }
        }
        return Analysis(packageName, versionName, file.length(), textFiles, chineseFiles, chineseHits, resourcesArsc, examples)
    }

    fun generateSigned(source: File, unsigned: File, signed: File): RewriteStats {
        val official = loadOfficialPtBr(source)
        val stats = rewriteApk(source, unsigned, official)
        signApk(unsigned, signed)
        return stats.copy(
            officialLangFiles = official.filesLoaded,
            officialLangEntries = official.entriesLoaded
        )
    }

    /**
     * Procura pt_BR.lang dentro do próprio APK e indexa as traduções pela pasta.
     * Assim um zh_CN.lang de um resource pack usa o pt_BR.lang equivalente daquele mesmo pack.
     */
    private fun loadOfficialPtBr(source: File): OfficialLangIndex {
        val byDir = LinkedHashMap<String, Map<String, String>>()
        val global = LinkedHashMap<String, String>()
        var filesLoaded = 0
        var entriesLoaded = 0

        ZipFile(source).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val lower = entry.name.lowercase()
                if (!lower.endsWith("/pt_br.lang") && lower != "pt_br.lang") continue
                if (entry.size !in 0..MAX_TEXT_BYTES) continue

                val bytes = zip.getInputStream(entry).use { input ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_TEXT_BYTES) break
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                }
                val text = decodeUtf8(bytes) ?: continue
                val parsed = parseLang(text)
                if (parsed.isEmpty()) continue
                val dir = entry.name.substringBeforeLast('/', "")
                byDir[dir] = parsed
                parsed.forEach { (key, value) -> global.putIfAbsent(key, value) }
                filesLoaded++
                entriesLoaded += parsed.size
            }
        }
        return OfficialLangIndex(byDir, global, filesLoaded, entriesLoaded)
    }

    private fun parseLang(text: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach
            val key = line.substring(0, idx).trim()
            if (key.isEmpty()) return@forEach
            out[key] = line.substring(idx + 1)
        }
        return out
    }

    private fun rewriteApk(source: File, outFile: File, official: OfficialLangIndex): RewriteStats {
        var filesChanged = 0
        var officialReplacements = 0
        var manualReplacements = 0
        var remainingChinese = 0
        var zhCnFilesTranslated = 0
        val counter = CountingOutputStream(BufferedOutputStream(FileOutputStream(outFile)))

        ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zin ->
            ZipOutputStream(counter).use { zout ->
                while (true) {
                    val original = zin.nextEntry ?: break
                    val name = original.name
                    if (original.isDirectory) {
                        zout.putNextEntry(ZipEntry(name))
                        zout.closeEntry()
                        continue
                    }
                    if (isOldSignature(name)) continue

                    val shouldTranslate = isTextCandidate(name) && original.size in 0..MAX_TEXT_BYTES
                    if (shouldTranslate) {
                        val bytes = readEntryBytes(zin, MAX_TEXT_BYTES.toInt())
                        val text = decodeUtf8(bytes)
                        if (text != null) {
                            val translated = translateText(name, text, official)
                            if (translated.text != text) {
                                filesChanged++
                                officialReplacements += translated.officialReplacements
                                manualReplacements += translated.manualReplacements
                                if (translated.zhCnFile) zhCnFilesTranslated++
                            }
                            remainingChinese += countChineseRuns(translated.text)
                            val entry = ZipEntry(name).apply { time = original.time }
                            zout.putNextEntry(entry)
                            zout.write(translated.text.toByteArray(Charsets.UTF_8))
                            zout.closeEntry()
                            continue
                        }
                        val entry = copyEntryMetadata(original, counter.count)
                        zout.putNextEntry(entry)
                        zout.write(bytes)
                        zout.closeEntry()
                        continue
                    }

                    val entry = copyEntryMetadata(original, counter.count)
                    zout.putNextEntry(entry)
                    zin.copyTo(zout)
                    zout.closeEntry()
                }
            }
        }
        return RewriteStats(
            filesChanged = filesChanged,
            replacements = officialReplacements + manualReplacements,
            officialReplacements = officialReplacements,
            manualReplacements = manualReplacements,
            remainingChineseRuns = remainingChinese,
            zhCnFilesTranslated = zhCnFilesTranslated,
            officialLangFiles = 0,
            officialLangEntries = 0
        )
    }

    private fun copyEntryMetadata(original: ZipEntry, currentOffset: Long): ZipEntry {
        val entry = ZipEntry(original.name)
        entry.time = original.time
        if (original.method == ZipEntry.STORED) {
            entry.method = ZipEntry.STORED
            entry.size = original.size
            entry.compressedSize = original.size
            entry.crc = original.crc
            if (original.name.endsWith(".so")) {
                val nameLen = original.name.toByteArray(Charsets.UTF_8).size
                val base = currentOffset + 30 + nameLen
                var totalExtra = ((4096 - (base % 4096)) % 4096).toInt()
                if (totalExtra in 1..3) totalExtra += 4096
                if (totalExtra >= 4) {
                    val payload = totalExtra - 4
                    val extra = ByteArray(totalExtra)
                    extra[0] = 0xFE.toByte()
                    extra[1] = 0xCA.toByte()
                    extra[2] = (payload and 0xFF).toByte()
                    extra[3] = ((payload ushr 8) and 0xFF).toByte()
                    entry.extra = extra
                }
            }
        }
        return entry
    }

    private fun translateText(name: String, input: String, official: OfficialLangIndex): TranslationResult {
        val lower = name.lowercase()

        // Principal alvo: zh_CN.lang. Mantém chaves e troca somente os valores.
        if (lower.endsWith("/zh_cn.lang") || lower == "zh_cn.lang") {
            val dir = name.substringBeforeLast('/', "")
            val localPtBr = official.byDir[dir]
            var officialCount = 0
            var manualCount = 0
            val out = input.lineSequence().joinToString("\n") { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@joinToString line
                val key = line.substring(0, idx).trim()
                val originalValue = line.substring(idx + 1)
                val officialValue = localPtBr?.get(key) ?: official.global[key]
                if (officialValue != null && placeholdersCompatible(originalValue, officialValue)) {
                    if (officialValue != originalValue) officialCount++
                    line.substring(0, idx + 1) + officialValue
                } else {
                    val manual = translatePlain(originalValue)
                    manualCount += manual.manualReplacements
                    line.substring(0, idx + 1) + manual.text
                }
            }
            return TranslationResult(out, officialCount, manualCount, zhCnFile = true)
        }

        // Não altera outros arquivos de idioma (.lang) para evitar mexer em japonês/zh_TW/etc.
        if (lower.endsWith(".lang")) return TranslationResult(input, 0, 0, zhCnFile = false)

        if (lower.endsWith(".json")) {
            return try {
                val root = JsonParser.parseString(input)
                var manualCount = 0
                fun translateJson(element: JsonElement): JsonElement = when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                        val r = translatePlain(element.asString)
                        manualCount += r.manualReplacements
                        com.google.gson.JsonPrimitive(r.text)
                    }
                    element.isJsonArray -> JsonArray().also { arr -> element.asJsonArray.forEach { arr.add(translateJson(it)) } }
                    element.isJsonObject -> JsonObject().also { obj -> element.asJsonObject.entrySet().forEach { (k, v) -> obj.add(k, translateJson(v)) } }
                    else -> element.deepCopy()
                }
                TranslationResult(Gson().toJson(translateJson(root)), 0, manualCount, zhCnFile = false)
            } catch (_: Throwable) {
                translatePlain(input)
            }
        }

        if (lower.endsWith(".properties")) {
            var manualCount = 0
            val out = input.lineSequence().joinToString("\n") { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) line else {
                    val r = translatePlain(line.substring(idx + 1))
                    manualCount += r.manualReplacements
                    line.substring(0, idx + 1) + r.text
                }
            }
            return TranslationResult(out, 0, manualCount, zhCnFile = false)
        }

        return translatePlain(input)
    }

    private fun translatePlain(input: String): TranslationResult {
        var result = input
        var count = 0
        translations.entries.sortedByDescending { it.key.length }.forEach { (zh, pt) ->
            val occurrences = countOccurrences(result, zh)
            if (occurrences > 0) {
                result = result.replace(zh, pt)
                count += occurrences
            }
        }
        return TranslationResult(result, 0, count, zhCnFile = false)
    }

    private fun placeholdersCompatible(source: String, target: String): Boolean {
        val a = placeholderRegex.findAll(source).map { it.value }.sorted().toList()
        val b = placeholderRegex.findAll(target).map { it.value }.sorted().toList()
        return a == b
    }

    private fun signApk(unsigned: File, signed: File) {
        val alias = "ChinaBedrockTranslatorApkSigner"
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=ChinaBedrock Translator Test,O=Victor Dev,C=BR"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(start.time)
                .setCertificateNotAfter(end.time)
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        val privateKey = ks.getKey(alias, null) as PrivateKey
        val certs = ks.getCertificateChain(alias).map { it as X509Certificate }
        val signerConfig = ApkSigner.SignerConfig.Builder(alias, privateKey, certs).build()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsigned)
            .setOutputApk(signed)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    private fun isTextCandidate(name: String): Boolean {
        val lower = name.lowercase()
        if (!(lower.startsWith("assets/") || lower.startsWith("res/raw/") || lower.startsWith("res/values/"))) return false
        return lower.endsWith(".json") || lower.endsWith(".lang") || lower.endsWith(".txt") || lower.endsWith(".xml") || lower.endsWith(".properties") || lower.endsWith(".csv")
    }

    private fun isOldSignature(name: String): Boolean {
        val n = name.uppercase()
        return n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC") || n.endsWith(".SF") || n == "META-INF/MANIFEST.MF")
    }

    private fun readEntryBytes(input: ZipInputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > max) error("Arquivo textual excede limite de ${max / (1024 * 1024)} MB")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        return try {
            val text = bytes.toString(Charsets.UTF_8)
            if (text.contains('\uFFFD')) null else text
        } catch (_: Throwable) {
            null
        }
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var start = 0
        while (true) {
            val idx = text.indexOf(needle, start)
            if (idx < 0) return count
            count++
            start = idx + needle.length
        }
    }

    private fun countChineseRuns(text: String): Int {
        var count = 0
        val it = chineseRegex.findAll(text).iterator()
        while (it.hasNext()) {
            it.next()
            count++
        }
        return count
    }

    data class Analysis(
        val packageName: String,
        val versionName: String,
        val fileSize: Long,
        val textFiles: Int,
        val chineseFiles: Int,
        val chineseHits: Int,
        val resourcesArsc: Boolean,
        val examples: Map<String, String>
    ) {
        fun pretty(): String = buildString {
            appendLine("Package: $packageName")
            appendLine("Versão: $versionName")
            appendLine("Tamanho: ${fileSize / (1024 * 1024)} MB")
            appendLine("Arquivos textuais analisados: $textFiles")
            appendLine("Arquivos com chinês: $chineseFiles")
            appendLine("Trechos chineses detectados: $chineseHits")
            appendLine("resources.arsc compilado: ${if (resourcesArsc) "sim — ainda não é alterado pela v0.2" else "não detectado"}")
            if (examples.isNotEmpty()) {
                appendLine("\nExemplos:")
                examples.forEach { (file, value) -> appendLine("• $file\n  $value") }
            }
        }
    }

    data class RewriteStats(
        val filesChanged: Int,
        val replacements: Int,
        val officialReplacements: Int,
        val manualReplacements: Int,
        val remainingChineseRuns: Int,
        val zhCnFilesTranslated: Int,
        val officialLangFiles: Int,
        val officialLangEntries: Int
    )

    private data class TranslationResult(
        val text: String,
        val officialReplacements: Int,
        val manualReplacements: Int,
        val zhCnFile: Boolean
    )

    private data class OfficialLangIndex(
        val byDir: Map<String, Map<String, String>>,
        val global: Map<String, String>,
        val filesLoaded: Int,
        val entriesLoaded: Int
    )

    companion object {
        private const val MAX_TEXT_BYTES = 5L * 1024 * 1024
        private val chineseRegex = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF]{2,}")
        private val placeholderRegex = Regex("%(?:%|\\d+\\$)?[a-zA-Z]")
    }
}

private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var count: Long = 0
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
}
