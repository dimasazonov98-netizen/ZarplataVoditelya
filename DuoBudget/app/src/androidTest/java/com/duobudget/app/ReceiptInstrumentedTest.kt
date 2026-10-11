package com.duobudget.app

import android.graphics.*
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duobudget.app.model.*
import com.duobudget.app.receipt.*
import com.duobudget.app.viewmodel.AppViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class ReceiptInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun receiptButtonsAreAvailableInExpenseForm() {
        rule.onNodeWithContentDescription("Добавить операцию").performClick()
        rule.onNodeWithText("+ Расход").performClick()
        rule.onNodeWithText("Сканировать QR-код чека").assertIsDisplayed()
        rule.onNodeWithText("Сфотографировать").assertIsDisplayed()
        rule.onNodeWithText("Из галереи").assertIsDisplayed()
    }
    @Test fun realRussianOcrAndQrPhotosFillExpenseAndDuplicateIsBlocked() = runBlocking {
        val context = rule.activity.applicationContext
        val file = File(context.cacheDir,"synthetic-receipt.png")
        try {
            val bmp = Bitmap.createBitmap(1200,1800,Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp); canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.BLACK; textSize=60f; typeface=Typeface.create("sans-serif",Typeface.NORMAL) }
            listOf("МАГНИТ", "КАССОВЫЙ ЧЕК", "10.10.2026 12:30", "Хлеб 75,50", "ИТОГ 1250,75", "НАЛИЧНЫМИ 2000,00", "СДАЧА 749,25").forEachIndexed { i,line -> canvas.drawText(line,70f,120f+i*140,paint) }
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }; bmp.recycle()
            val r = ReceiptRecognizer.recognize(context,Uri.fromFile(file))
            assertEquals(125075L,r.amount); assertEquals("Продукты",r.category)
            assertEquals(java.time.LocalDate.of(2026,10,10),r.date?.toLocalDate())
            // Decode a genuine QR bitmap through the same gallery/photo pipeline.
            val raw="t=20261010T1230&s=1250.75&fn=9999078900001234&i=12345&fp=1234567890&n=1"
            val matrix=com.google.zxing.MultiFormatWriter().encode(raw,com.google.zxing.BarcodeFormat.QR_CODE,900,900)
            val qrBmp=Bitmap.createBitmap(900,900,Bitmap.Config.ARGB_8888)
            for(y in 0 until 900)for(x in 0 until 900)qrBmp.setPixel(x,y,if(matrix[x,y])Color.BLACK else Color.WHITE)
            file.outputStream().use { qrBmp.compress(Bitmap.CompressFormat.PNG,100,it) };qrBmp.recycle()
            val qr=ReceiptRecognizer.recognize(context,Uri.fromFile(file))
            assertTrue(qr.fromQr);assertEquals(ReceiptParser.qr(raw).receiptId,qr.receiptId)
            lateinit var vm: AppViewModel
            rule.runOnUiThread { vm=ViewModelProvider(rule.activity)[AppViewModel::class.java];vm.reset() }
            rule.waitUntil(20000){!vm.busy.value};rule.runOnUiThread{vm.clearMessage()}
            fun save()=rule.runOnUiThread { vm.saveTransaction(null,qr.type,qr.amount!!,"Продукты","Чек","cash","",Payer.ME,qr.date!!,qr.receiptId) {} }
            save();rule.waitUntil(20000){!vm.busy.value}
            assertEquals(1,vm.data.value.transactions.size)
            assertEquals(125075L,BudgetEngine.netExpenses(vm.data.value,YearMonth.of(2026,10)))
            save();rule.waitUntil(20000){!vm.busy.value}
            assertEquals(1,vm.data.value.transactions.size);assertTrue(vm.message.value.contains("уже учтён"))
            rule.runOnUiThread { vm.clearMessage();vm.deleteTransaction(qr.receiptId) }
            rule.waitUntil(20000){!vm.busy.value}
            save();rule.waitUntil(20000){!vm.busy.value}
            assertTrue(vm.data.value.transactions.single().deleted)
            assertEquals(0L,BudgetEngine.netExpenses(vm.data.value,YearMonth.of(2026,10)))
            rule.runOnUiThread{vm.clearMessage();vm.reset()};rule.waitUntil(20000){!vm.busy.value};rule.runOnUiThread{vm.clearMessage()}
        } finally { file.delete() }
    }
}
