from pathlib import Path

p = Path('app/src/main/java/com/victor/chinabedrocktranslator/ApkTranslator.kt')
s = p.read_text()

s = s.replace(
    'fun generateSigned(source: File, unsigned: File, signed: File): RewriteStats {\n        val official = loadOfficialPtBr(source)\n        val stats = rewriteApk(source, unsigned, official)\n        signApk(unsigned, signed)',
    'fun generateSigned(source: File, unsigned: File, signed: File, onProgress: ((TranslationProgress) -> Unit)? = null): RewriteStats {\n        val official = loadOfficialPtBr(source)\n        onProgress?.invoke(TranslationProgress("Índice pt-BR pronto", null, null, null, 0, 0, 0))\n        val stats = rewriteApk(source, unsigned, official, onProgress)\n        onProgress?.invoke(TranslationProgress("Assinando APK", null, null, null, stats.officialReplacements, stats.manualReplacements, stats.filesChanged))\n        signApk(unsigned, signed)'
)

s = s.replace(
    'private fun rewriteApk(source: File, outFile: File, official: OfficialLangIndex): RewriteStats {',
    'private fun rewriteApk(source: File, outFile: File, official: OfficialLangIndex, onProgress: ((TranslationProgress) -> Unit)?): RewriteStats {'
)

s = s.replace(
    'val shouldTranslate = isTextCandidate(name) && original.size in 0..MAX_TEXT_BYTES',
    'val shouldTranslate = isTextCandidate(name) && (original.size < 0 || original.size <= MAX_TEXT_BYTES)'
)

old = '''                            if (translated.text != text) {
                                filesChanged++
                                officialReplacements += translated.officialReplacements
                                manualReplacements += translated.manualReplacements
                                if (translated.zhCnFile) zhCnFilesTranslated++
                            }
                            remainingChinese += countChineseRuns(translated.text)'''
new = '''                            if (translated.text != text) {
                                filesChanged++
                                officialReplacements += translated.officialReplacements
                                manualReplacements += translated.manualReplacements
                                if (translated.zhCnFile) zhCnFilesTranslated++
                                val sample = firstChangedPair(text, translated.text, name)
                                onProgress?.invoke(
                                    TranslationProgress(
                                        stage = if (translated.zhCnFile) "Traduzindo zh_CN.lang" else "Traduzindo arquivo textual",
                                        file = name,
                                        original = sample?.first,
                                        translated = sample?.second,
                                        officialReplacements = officialReplacements,
                                        manualReplacements = manualReplacements,
                                        filesChanged = filesChanged
                                    )
                                )
                            }
                            remainingChinese += countChineseRuns(translated.text)'''
if old not in s:
    raise SystemExit('translation changed block not found')
s = s.replace(old, new, 1)

marker = '    private fun copyEntryMetadata(original: ZipEntry, currentOffset: Long): ZipEntry {'
helper = '''    private fun firstChangedPair(before: String, after: String, name: String): Pair<String, String>? {
        val b = before.lineSequence().iterator()
        val a = after.lineSequence().iterator()
        while (b.hasNext() && a.hasNext()) {
            val left = b.next()
            val right = a.next()
            if (left != right) {
                val leftShown = if (name.endsWith(".lang", true) && left.contains('=')) left.substringAfter('=') else left
                val rightShown = if (name.endsWith(".lang", true) && right.contains('=')) right.substringAfter('=') else right
                return compactPreview(leftShown) to compactPreview(rightShown)
            }
        }
        return null
    }

    private fun compactPreview(value: String): String {
        val clean = value.replace("\\t", " ").trim()
        return if (clean.length <= 140) clean else clean.substring(0, 137) + "..."
    }

'''
if marker not in s:
    raise SystemExit('copyEntryMetadata marker not found')
s = s.replace(marker, helper + marker, 1)

data_marker = '    data class RewriteStats('
data_class = '''    data class TranslationProgress(
        val stage: String,
        val file: String?,
        val original: String?,
        val translated: String?,
        val officialReplacements: Int,
        val manualReplacements: Int,
        val filesChanged: Int
    )

'''
if data_marker not in s:
    raise SystemExit('RewriteStats marker not found')
s = s.replace(data_marker, data_class + data_marker, 1)

p.write_text(s)
print('v0.2.2 live progress patch applied')
