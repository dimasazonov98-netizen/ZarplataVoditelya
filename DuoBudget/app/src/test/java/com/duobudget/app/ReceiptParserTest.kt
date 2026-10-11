package com.duobudget.app

import com.duobudget.app.receipt.ReceiptParser
import com.duobudget.app.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

class ReceiptParserTest {
    private val qr = "t=20261010T123045&s=1250.75&fn=9999078900001234&i=12345&fp=1234567890&n=1"
    private fun fails(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
    @Test fun qrReadsExactKopecksAndTime() {
        val r = ReceiptParser.qr(qr)
        assertEquals(125075L, r.amount); assertEquals(LocalDateTime.of(2026,10,10,12,30,45), r.date)
        assertEquals(TransactionType.EXPENSE, r.type); assertEquals(72, r.receiptId.length)
    }
    @Test fun fiscalIdentitySurvivesReorderingUrlsAndAmountFormatting() {
        val a = ReceiptParser.qr(qr)
        val b = ReceiptParser.qr("https://example.test/check?fn=9999078900001234&fp=1234567890&i=12345&n=1&s=1250%2C75&t=20261010T123045")
        assertEquals(a.receiptId, b.receiptId)
        assertNotEquals(a.receiptId, ReceiptParser.qr(qr.replace("i=12345", "i=12346")).receiptId)
    }
    @Test fun refundsAreNotExpenses() { assertEquals(TransactionType.REFUND, ReceiptParser.qr(qr.replace("n=1", "n=2")).type) }
    @Test fun invalidQrNeverCreatesAnExpense() {
        listOf("https://example.test", qr.replace("1250.75","-12.00"), qr.replace("1250.75","0"), qr.replace("1250.75","1.001"), qr.replace("20261010", "20260230"), qr.replace("20261010", "20991010"), qr.replace("n=1","n=3"), qr + "&s=1", qr.replace("fn=9999078900001234", "fn=1")).forEach { fails { ReceiptParser.qr(it) } }
    }
    @Test fun russianOcrReadsTotalRatherThanCashAndChange() {
        val r = ReceiptParser.text("МАГНИТ\nКАССОВЫЙ ЧЕК\n10.10.2026 12:30\nМолоко 89,90\nИТОГ 1 250,75\nНАЛИЧНЫМИ 2 000,00\nСДАЧА 749,25")
        assertEquals(125075L, r.amount); assertEquals("МАГНИТ", r.merchant); assertEquals("Продукты", r.category)
        assertEquals(LocalDateTime.of(2026,10,10,12,30), r.date)
    }
    @Test fun missingAndConflictingTotalsRequireManualReview() {
        assertNull(ReceiptParser.text("Молоко 100,00\nНАЛИЧНЫМИ 500,00\nСДАЧА 400,00").amount)
        assertNull(ReceiptParser.text("ИТОГ 100,00\nИТОГО 200,00").amount)
        assertEquals(10000L, ReceiptParser.text("ИТОГ\n100,00").amount)
        assertNull(ReceiptParser.text("ИТОГ\nМолоко 100,00").amount)
    }
    @Test fun invalidOcrDateIsNotReplacedWithToday() { assertNull(ReceiptParser.text("30.02.2026\nИТОГ 100,00").date) }
    @Test fun ocrAndQrHaveSameFiscalIdentity() {
        val r = ReceiptParser.text("ФН: 9999078900001234\nФД: 12345\nФПД: 1234567890\nИТОГ 1250,75")
        assertEquals(ReceiptParser.qr(qr).receiptId, r.receiptId)
    }
    @Test fun recognizedExpenseUpdatesBalanceAndMonthlySpendingAndRoundTrips() {
        val r = ReceiptParser.qr(qr)
        val t = MoneyTransaction(id=r.receiptId,type=r.type,amount=r.amount!!,category="Продукты",note="Чек",accountId="cash",payer=Payer.ME,createdByUid="a",createdAt=r.date!!)
        val s = BudgetEngine.apply(BudgetData(),BudgetCommand(kind="TX_PUT",actorId="a",tx=t))
        assertEquals(125075L,BudgetEngine.netExpenses(s,YearMonth.of(2026,10)))
        assertEquals(-125075L,BudgetEngine.balance(s,"cash"))
        assertEquals(r.receiptId, com.duobudget.app.data.StateCodec.data(com.duobudget.app.data.StateCodec.data(s)).transactions.single().id)
    }
}
