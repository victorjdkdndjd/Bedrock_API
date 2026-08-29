package com.victor.chinabedrocktranslator

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var progress: ProgressBar
    private lateinit var importButton: Button
    private lateinit var buildButton: Button
    private lateinit var exportButton: Button

    private lateinit var translator: ApkTranslator
    private lateinit var analyzer: SafeApkAnalyzer
    private var importedApk: File? = null
    private var generatedApk: File? = null
    private var analysis: ApkTranslator.Analysis? = null

    private val openApk = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importApk(uri)
    }

    private val createApk = registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        val source = generatedApk
        if (uri != null && source != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                setStatus("APK exportado com sucesso.")
            } catch (t: Throwable) {
                setStatus("Falha ao exportar: ${t.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        translator = ApkTranslator(this)
        analyzer = SafeApkAnalyzer(this)
        buildUi()
    }

    private fun buildUi() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "ChinaBedrock Translator v0.2.0"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Usa pt_BR.lang do próprio Minecraft por chave e fallback para textos exclusivos da edição chinesa."
            textSize = 15f
        })
        importButton = Button(this).apply {
            text = "Importar APK"
            setOnClickListener { openApk.launch(arrayOf("application/vnd.android.package-archive", "application/zip", "*/*")) }
        }
        buildButton = Button(this).apply {
            text = "Gerar APK PT-BR"
            isEnabled = false
            setOnClickListener { buildTranslatedApk() }
        }
        exportButton = Button(this).apply {
            text = "Exportar APK"
            isEnabled = false
            setOnClickListener {
                val version = analysis?.versionName ?: "traduzido"
                createApk.launch("Minecraft-China-PTBR-$version-v0.2.apk")
            }
        }
        progress = ProgressBar(this).apply { visibility = View.GONE }
        status = TextView(this).apply {
            textSize = 16f
            text = "Nenhum APK importado."
        }
        details = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
        }
        root.addView(importButton)
        root.addView(buildButton)
        root.addView(exportButton)
        root.addView(progress)
        root.addView(status)
        root.addView(ScrollView(this).apply { addView(details) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun importApk(uri: Uri) {
        busy(true)
        setStatus("Copiando APK para área de trabalho…")
        details.text = "O APK é grande; a análise é feita em streaming para reduzir o uso de RAM."
        thread {
            try {
                cacheDir.listFiles()?.filter { it.name.startsWith("imported_") }?.forEach { it.delete() }

                val input = File(cacheDir, "imported_${System.currentTimeMillis()}.apk")
                contentResolver.openInputStream(uri)?.use { src ->
                    FileOutputStream(input).buffered(1024 * 1024).use { dst -> src.copyTo(dst, 1024 * 1024) }
                } ?: error("Não foi possível abrir o arquivo")

                runOnUiThread { setStatus("APK copiado. Analisando sem carregar tudo na memória…") }
                val found = analyzer.analyze(input)
                importedApk = input
                analysis = found
                generatedApk = null

                runOnUiThread {
                    buildButton.isEnabled = true
                    exportButton.isEnabled = false
                    setStatus(if (found.packageName == "com.netease.x19") "Minecraft China reconhecido ✅" else "APK importado, mas package não é com.netease.x19 ⚠️")
                    details.text = found.pretty() + "\nAnalisador: streaming/baixo uso de memória\n\nNa v0.2, a tradução oficial é aplicada somente durante GERAR APK PT-BR."
                    busy(false)
                }
            } catch (oom: OutOfMemoryError) {
                runOnUiThread {
                    setStatus("Memória insuficiente durante a análise.")
                    busy(false)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    setStatus("Falha ao importar: ${t.javaClass.simpleName}: ${t.message}")
                    busy(false)
                }
            }
        }
    }

    private fun buildTranslatedApk() {
        val source = importedApk ?: return
        busy(true)
        buildButton.isEnabled = false
        setStatus("Indexando pt_BR.lang, traduzindo zh_CN.lang, reconstruindo e assinando…")
        thread {
            try {
                val unsigned = File(cacheDir, "minecraft_china_ptbr_unsigned.apk")
                val signed = File(cacheDir, "minecraft_china_ptbr_signed.apk")
                unsigned.delete()
                signed.delete()
                val stats = translator.generateSigned(source, unsigned, signed)
                generatedApk = signed
                val totalDetected = analysis?.chineseHits ?: 0
                val translated = stats.replacements
                val approxCoverage = if (totalDetected > 0) (translated * 100.0 / totalDetected).coerceAtMost(100.0) else 0.0
                runOnUiThread {
                    exportButton.isEnabled = true
                    buildButton.isEnabled = true
                    details.append(
                        "\n\n=== GERAÇÃO PT-BR v0.2 ===" +
                            "\nArquivos pt_BR.lang encontrados: ${stats.officialLangFiles}" +
                            "\nEntradas oficiais indexadas: ${stats.officialLangEntries}" +
                            "\nzh_CN.lang alterados: ${stats.zhCnFilesTranslated}" +
                            "\nArquivos alterados no total: ${stats.filesChanged}" +
                            "\nTraduções oficiais por chave: ${stats.officialReplacements}" +
                            "\nFallbacks manuais/NetEase: ${stats.manualReplacements}" +
                            "\nSubstituições totais: ${stats.replacements}" +
                            "\nTrechos chineses ainda detectados nos arquivos processados: ${stats.remainingChineseRuns}" +
                            "\nCobertura aproximada vs. análise inicial: %.1f%%".format(approxCoverage) +
                            "\nAPK assinado: ${signed.length() / (1024 * 1024)} MB" +
                            "\n\nTESTE VISUAL: depois de instalar, observe a tela de download. Se os textos vierem de assets textuais, devem aparecer termos como Som, Versão, Motor e Baixando recursos." +
                            "\n\nIMPORTANTE: resources.arsc ainda não é reescrito e a assinatura continua sendo de teste, não a oficial da NetEase."
                    )
                    setStatus("APK PT-BR v0.2 gerado ✅")
                    busy(false)
                }
            } catch (oom: OutOfMemoryError) {
                runOnUiThread {
                    buildButton.isEnabled = true
                    setStatus("Memória insuficiente durante a reconstrução.")
                    busy(false)
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    buildButton.isEnabled = true
                    setStatus("Falha ao gerar: ${t.javaClass.simpleName}: ${t.message}")
                    busy(false)
                }
            }
        }
    }

    private fun setStatus(message: String) {
        status.text = message
    }

    private fun busy(value: Boolean) {
        progress.visibility = if (value) View.VISIBLE else View.GONE
        importButton.isEnabled = !value
        if (value) exportButton.isEnabled = false
    }
}
