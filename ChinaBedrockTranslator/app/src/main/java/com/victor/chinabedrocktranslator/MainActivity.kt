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
        buildUi()
    }

    private fun buildUi() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "ChinaBedrock Translator"
            textSize = 26f
        })
        root.addView(TextView(this).apply {
            text = "Importe o APK do Minecraft China, analise textos e gere uma cópia pt-BR de teste."
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
        setStatus("Copiando e analisando APK…")
        thread {
            try {
                val input = File(cacheDir, "imported_${System.currentTimeMillis()}.apk")
                contentResolver.openInputStream(uri)?.use { src ->
                    FileOutputStream(input).use { dst -> src.copyTo(dst) }
                } ?: error("Não foi possível abrir o arquivo")

                val found = translator.analyze(input)
                importedApk = input
                analysis = found
                generatedApk = null

                runOnUiThread {
                    buildButton.isEnabled = true
                    exportButton.isEnabled = false
                    setStatus(if (found.packageName == "com.netease.x19") "Minecraft China reconhecido ✅" else "APK importado, mas package não é com.netease.x19 ⚠️")
                    details.text = found.pretty()
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
                val stats = translator.generateSigned(source, unsigned, signed)
                generatedApk = signed
                runOnUiThread {
                    exportButton.isEnabled = true
                    buildButton.isEnabled = true
                    details.append("\n\n=== GERAÇÃO PT-BR ===\nArquivos alterados: ${stats.filesChanged}\nSubstituições: ${stats.replacements}\nAPK assinado: ${signed.length() / (1024 * 1024)} MB\n\nIMPORTANTE: esta é uma assinatura de teste, não a assinatura oficial da NetEase. O APK pode ser recusado por verificações de integridade. Para instalar, normalmente será necessário remover a instalação oficial com o mesmo package primeiro.")
                    setStatus("APK PT-BR de teste gerado ✅")
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
