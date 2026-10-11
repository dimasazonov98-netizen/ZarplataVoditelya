package com.duobudget.app.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

object ReceiptRecognizer {
    suspend fun recognize(context: Context, uri: Uri): ReceiptDraft = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                ensureActive()
                val n = input.read(buffer); if (n < 0) break
                require(out.size() + n <= 20 * 1024 * 1024) { "Фото больше 20 МБ. Выберите меньший файл." }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } ?: error("Не удалось открыть фото")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Файл не является изображением" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2800) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("Не удалось прочитать фото")
        try {
            val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
            val matrix = Matrix().apply {
                if (exif?.isFlipped == true) postScale(-1f, 1f)
                postRotate((exif?.rotationDegrees ?: 0).toFloat())
            }
            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated !== bitmap) { bitmap.recycle(); bitmap = rotated }
            }
            ensureActive()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val qr = runCatching { MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)).text }.getOrNull()
            // Ignore unrelated QR codes in photos and continue with OCR.
            val fiscal = qr?.let { runCatching { ReceiptParser.qr(it) }.getOrNull() }
            val root = File(context.filesDir, "receipt-ocr")
            val data = File(root, "tessdata").apply { mkdirs() }
            for (lang in listOf("rus", "eng")) {
                val dest = File(data, "$lang.traineddata")
                if (!dest.exists()) {
                    val temp = File(data, "$lang.tmp")
                    context.assets.open("tessdata/$lang.traineddata").use { input -> temp.outputStream().use { input.copyTo(it) } }
                    check(temp.renameTo(dest)) { "Не удалось подготовить распознавание" }
                }
            }
            ensureActive()
            val tess = TessBaseAPI()
            try {
                check(tess.init(root.absolutePath, "rus+eng")) { "Не удалось запустить распознавание" }
                tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
                tess.setImage(bitmap)
                val draft = ReceiptParser.text(tess.getUTF8Text().orEmpty())
                ensureActive()
                if (fiscal == null) draft else fiscal.copy(merchant = draft.merchant, category = draft.category)
            } finally { tess.recycle() }
        } finally { bitmap.recycle(); bytes.fill(0) }
    }
}
