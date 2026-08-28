package com.victor.chinabedrocktranslator

import android.content.Context
import android.content.pm.PackageManager
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.LinkedHashMap
import java.util.zip.ZipInputStream

/**
 * Low-memory APK scanner used for very large Minecraft China packages.
 * It never builds a List<MatchResult> and never keeps a full text asset in RAM.
 */
class SafeApkAnalyzer(private val context: Context) {

    fun analyze(file: File): ApkTranslator.Analysis {
        val pkg = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_META_DATA)
        val packageName = pkg?.packageName ?: "desconhecido"
        val versionName = pkg?.versionName ?: "desconhecida"

        var textFiles = 0
        var chineseFiles = 0
        var chineseHits = 0
        var resourcesArsc = false
        val examples = LinkedHashMap<String, String>()

        ZipInputStream(BufferedInputStream(FileInputStream(file), 256 * 1024)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                if (entry.isDirectory) continue
                if (entry.name == "resources.arsc") resourcesArsc = true
                if (!isTextCandidate(entry.name)) continue

                // Avoid spending minutes on giant blobs mislabeled as text.
                if (entry.size > MAX_DECLARED_TEXT_BYTES) continue

                val scan = scanEntry(zin)
                if (!scan.looksTextual) continue

                textFiles++
                if (scan.hits > 0) {
                    chineseFiles++
                    chineseHits += scan.hits
                    if (examples.size < MAX_EXAMPLE_FILES && scan.examples.isNotEmpty()) {
                        examples[entry.name] = scan.examples.joinToString(" | ")
                    }
                }
            }
        }

        return ApkTranslator.Analysis(
            packageName,
            versionName,
            file.length(),
            textFiles,
            chineseFiles,
            chineseHits,
            resourcesArsc,
            examples
        )
    }

    private fun scanEntry(zin: ZipInputStream): ScanResult {
        val reader = InputStreamReader(zin, Charsets.UTF_8)
        val buffer = CharArray(16 * 1024)
        var scannedChars = 0L
        var hits = 0
        var sawNul = false
        val run = StringBuilder(64)
        val examples = ArrayList<String>(MAX_EXAMPLES_PER_FILE)

        fun finishRun() {
            if (run.length >= 2) {
                hits++
                if (examples.size < MAX_EXAMPLES_PER_FILE) examples.add(run.toString())
            }
            run.setLength(0)
        }

        while (scannedChars < MAX_SCAN_CHARS) {
            val wanted = minOf(buffer.size.toLong(), MAX_SCAN_CHARS - scannedChars).toInt()
            val n = reader.read(buffer, 0, wanted)
            if (n <= 0) break
            scannedChars += n

            for (i in 0 until n) {
                val ch = buffer[i]
                if (ch == '\u0000') sawNul = true
                if (isChinese(ch)) {
                    if (run.length < MAX_EXAMPLE_CHARS) run.append(ch)
                } else {
                    finishRun()
                }
            }
        }
        finishRun()

        // nextEntry() will close/skip any unread remainder of this ZIP entry.
        return ScanResult(!sawNul, hits, examples)
    }

    private fun isChinese(ch: Char): Boolean {
        val c = ch.code
        return c in 0x3400..0x4DBF || c in 0x4E00..0x9FFF
    }

    private fun isTextCandidate(name: String): Boolean {
        val lower = name.lowercase()
        if (!(lower.startsWith("assets/") || lower.startsWith("res/raw/") || lower.startsWith("res/values/"))) return false
        return lower.endsWith(".json") || lower.endsWith(".lang") || lower.endsWith(".txt") ||
            lower.endsWith(".xml") || lower.endsWith(".properties") || lower.endsWith(".csv")
    }

    private data class ScanResult(
        val looksTextual: Boolean,
        val hits: Int,
        val examples: List<String>
    )

    companion object {
        private const val MAX_DECLARED_TEXT_BYTES = 64L * 1024 * 1024
        private const val MAX_SCAN_CHARS = 16L * 1024 * 1024
        private const val MAX_EXAMPLE_FILES = 30
        private const val MAX_EXAMPLES_PER_FILE = 8
        private const val MAX_EXAMPLE_CHARS = 160
    }
}
