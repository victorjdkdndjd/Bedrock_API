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
            text = "ChinaBedrock Translator v0.1.1"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Importe o APK do Minecraft China. Esta versão usa análise em streaming para APKs grandes."
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
                createApk.launch("Minecraft-China-PTBR-$version.apk")
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
        details.text = "O APK é grande; a análise pode demorar, mas agora é feita em streaming para reduzir uso de RAM."
        thread {
            try {
                // Do not accumulate multiple multi-GB imports in cache.
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
                    details.text = found.pretty() + "\nAnalisador: streaming/baixo uso de memória (v0.1.1)"
                    busy(false)
                }
            } catch (oom: OutOfMemoryError) {
                runOnUiThread {
                    setStatus("Memória insuficiente durante a análise. A v0.1.1 reduziu bastante o uso; envie esta mensagem se ainda ocorrer.")
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
        setStatus("Traduzindo, reconstruindo e assinando…")
        thread {
            try {
                val unsigned = File(cacheDir, "minecraft_china_ptbr_unsigned.apk")
                val signed = File(cacheDir, "minecraft_china_ptbr_signed.apk")
                unsigned.delete()
                signed.delete()
                val stats = translator.generateSigned(source, unsigned, signed)
                generatedApk = signed
                runOnUiThread {
                    exportButton.isEnabled = true
                    buildButton.isEnabled = true
                    details.append("\n\n=== GERAÇÃO PT-BR ===\nArquivos alterados: ${stats.filesChanged}\nSubstituições: ${stats.replacements}\nAPK assinado: ${signed.length() / (1024 * 1024)} MB\n\nIMPORTANTE: esta é uma assinatura de teste, não a assinatura oficial da NetEase. O APK pode ser recusado por verificações de integridade. Para instalar, normalmente será necessário remover a instalação oficial com o mesmo package primeiro.")
                    setStatus("APK PT-BR de teste gerado ✅")
                    busy(false)
                }
            } catch (oom: OutOfMemoryError) {
                runOnUiThread {
                    buildButton.isEnabled = true
                    setStatus("Memória insuficiente durante a reconstrução. A próxima etapa será tornar a geração também totalmente streaming.")
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
