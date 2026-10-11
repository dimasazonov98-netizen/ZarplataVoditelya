package com.duobudget.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.duobudget.app.receipt.*
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun ReceiptInput(enabled: Boolean, onDraft: (ReceiptDraft) -> Unit, onScanning: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var cameraPath by rememberSaveable { mutableStateOf("") }
    var pending by rememberSaveable { mutableStateOf("") }
    val latestDraft by rememberUpdatedState(onDraft)
    val latestScanning by rememberUpdatedState(onScanning)
    fun recognize(uri: Uri, cleanup: File? = null) {
        scanning = true; latestScanning(true); error = ""
        scope.launch {
            try { latestDraft(ReceiptRecognizer.recognize(context, uri)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Не удалось распознать чек. Попробуйте QR-код или более чёткое фото." }
            finally { cleanup?.delete(); scanning = false; latestScanning(false) }
        }
    }
    val qr = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { raw ->
            runCatching { ReceiptParser.qr(raw) }.onSuccess { latestDraft(it); error = "" }
                .onFailure { error = it.message ?: "Не удалось прочитать QR-код чека" }
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { recognize(it) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (cameraPath.isNotBlank()) {
            val file = File(cameraPath); cameraPath = ""
            if (ok) recognize(FileProvider.getUriForFile(context, context.packageName + ".receipt-files", file), file) else file.delete()
        }
    }
    fun launch(mode: String) {
        error = ""
        try {
            if (mode == "qr") qr.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Наведите камеру на QR-код кассового чека").setBeepEnabled(false)
                .setOrientationLocked(false).setCaptureActivity(ReceiptCaptureActivity::class.java))
            else {
                val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
                // Remove abandoned temporary photos after one day.
                dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000 }?.forEach { it.delete() }
                val file = File(dir, UUID.randomUUID().toString() + ".jpg")
                file.createNewFile(); cameraPath = file.absolutePath
                camera.launch(FileProvider.getUriForFile(context, context.packageName + ".receipt-files", file))
            }
        } catch (e: Exception) { cameraPath.takeIf { it.isNotBlank() }?.let { File(it).delete() }; cameraPath = ""; error = "Не удалось открыть камеру. Можно выбрать фото чека." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val mode = pending; pending = ""
        if (granted && mode.isNotBlank()) launch(mode) else error = "Доступ к камере не разрешён. Выберите фото чека из галереи."
    }
    fun capture(mode: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launch(mode)
        else { pending = mode; permission.launch(Manifest.permission.CAMERA) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Добавить по чеку", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { capture("qr") }, enabled = enabled && !scanning, modifier = Modifier.fillMaxWidth()) { Text("Сканировать QR-код чека") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { capture("photo") }, enabled = enabled && !scanning, modifier = Modifier.weight(1f)) { Text("Сфотографировать") }
            OutlinedButton(onClick = { photo.launch("image/*") }, enabled = enabled && !scanning, modifier = Modifier.weight(1f)) { Text("Из галереи") }
        }
        if (scanning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Распознаю чек на телефоне…") }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Text("После распознавания проверьте данные и сохраните. Фото не отправляется на сервер.", style = MaterialTheme.typography.bodySmall)
    }
}
